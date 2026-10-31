package com.brewaco3.muzei.wallhaven.provider

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.brewaco3.muzei.wallhaven.AppDatabase
import com.brewaco3.muzei.wallhaven.R
import com.brewaco3.muzei.wallhaven.WallhavenPrefs
import com.brewaco3.muzei.wallhaven.WallhavenProviderConst
import com.brewaco3.muzei.wallhaven.provider.exceptions.CorruptFileException
import com.brewaco3.muzei.wallhaven.provider.exceptions.FilterMatchNotFoundException
import com.brewaco3.muzei.wallhaven.provider.network.OkHttpSingleton
import com.brewaco3.muzei.wallhaven.provider.network.moshi.RankingArtwork
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.ProviderContract
import com.google.android.apps.muzei.api.provider.ProviderContract.getProviderClient
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.buffer
import okio.sink
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class WallhavenArtWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    companion object {
        private const val LOG_TAG = "WallhavenArtWorker"

        /** Name of the unique work that loads artworks; only one may run at a time. */
        const val WORKER_TAG = "wallhaven_art_load"

        /** Unique work name from before the Wallhaven port. Cancelled on enqueue so an upgrade cannot run two loads. */
        private const val LEGACY_WORKER_TAG = "ANTONY"

        /** Set on the work request to wipe the current rotation queue before loading. */
        const val INPUT_CLEAR_ARTWORK = "clearArtwork"

        // Cache of already-fetched search results, so repeated runs do not re-query the API
        private const val CACHE_KEY_DATA = "cache_ranking_data"
        private const val CACHE_KEY_TIMESTAMP = "cache_ranking_timestamp"
        private const val CACHE_KEY_HASH = "cache_ranking_hash"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L

        /** How many spare candidates to keep in hand relative to the requested batch size. */
        private const val CANDIDATE_BUFFER_FACTOR = 3

        // ponytail: fixed page cap. The API has no "give me everything" mode and a worker must terminate
        private const val MAX_PAGES = 5

        private const val PREF_STORE_EXTERNAL = "pref_storeInExtStorage"
        private const val DEFAULT_NUM_TO_DOWNLOAD = 3

        private val moshi = com.squareup.moshi.Moshi.Builder().build()
        private val cacheAdapter = moshi.adapter<List<RankingArtwork>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, RankingArtwork::class.java)
        )

        internal fun enqueueLoad(
            clearArtworkRequested: Boolean,
            context: Context?,
            existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
        ): UUID? {
            val appContext = context?.applicationContext ?: return null
            val request = OneTimeWorkRequest.Builder(WallhavenArtWorker::class.java)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setInputData(workDataOf(INPUT_CLEAR_ARTWORK to clearArtworkRequested))
                .addTag(WORKER_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

            // The work must be unique: concurrent loads would race on the cache and spam the API
            val workManager = WorkManager.getInstance(appContext)
            workManager.cancelUniqueWork(LEGACY_WORKER_TAG)
            workManager.enqueueUniqueWork(WORKER_TAG, existingWorkPolicy, request)
            return request.id
        }
    }

    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
    }

    // Entry method, called by Muzei / WorkManager
    override fun doWork(): Result {
        Log.i(LOG_TAG, "Starting work")
        return try {
            val providerClient = getProviderClient(applicationContext, WallhavenArtProvider::class.java)
            if (inputData.getBoolean(INPUT_CLEAR_ARTWORK, false)) {
                Log.i(LOG_TAG, "Clearing existing artwork and candidate cache")
                providerClient.setArtwork(emptyList())
                clearRankingCache(prefs)
            }
            providerClient.addArtwork(getArtworks())
            Log.i(LOG_TAG, "Work completed")
            Result.success()
        } catch (throwable: Throwable) {
            Log.e(LOG_TAG, "Failed to update artwork", throwable)
            Result.failure(
                workDataOf(
                    WallhavenProviderConst.WORK_ERROR_MESSAGE_KEY to
                        (throwable.message ?: throwable.javaClass.simpleName)
                )
            )
        }
    }

    private fun getArtworks(): List<Artwork> {
        val updateMode = prefs.getString("pref_updateMode", "toplist") ?: "toplist"
        Log.i(LOG_TAG, "Feed mode: $updateMode")

        // Loaded once so every candidate check can use cheap set lookups
        val existingTokens = loadExistingArtworkTokens()
        val deletedIds = loadDeletedArtworkIds()

        val artworkList = getArtworksRanking(updateMode, existingTokens, deletedIds)
        Log.i(LOG_TAG, "Submitting ${artworkList.size} artworks")
        return artworkList
    }

    private fun getArtworksRanking(
        updateMode: String,
        existingTokens: Set<String>,
        deletedIds: Set<String>,
    ): List<Artwork> {
        val numToDownload = prefs.getInt("prefSlider_numToDownload", DEFAULT_NUM_TO_DOWNLOAD)
        val puritySelection = prefs.getStringSet("pref_rankingFilterSelect", setOf("sfw")) ?: setOf("sfw")
        val categorySelection = prefs.getStringSet(
            "pref_rankingCategorySelect",
            setOf("general", "anime", "people")
        ) ?: setOf("general", "anime", "people")
        val minWidth = prefs.getInt("prefSlider_minimumWidth", 0)
        val minHeight = prefs.getInt("prefSlider_minimumHeight", 0)
        val hasApiKey = WallhavenPrefs.hasApiKey()
        val allowedPurities = ArtworkFilter.allowedPurities(puritySelection, hasApiKey)

        fun matches(candidate: RankingArtwork) = ArtworkFilter.matches(
            candidate, allowedPurities, minWidth, minHeight, existingTokens, deletedIds
        )

        var candidates = loadRankingCache(prefs) ?: emptyList()
        var matching = candidates.filter(::matches)

        // Top up from the API until there is enough spare choice to keep the next runs interesting
        val target = numToDownload * CANDIDATE_BUFFER_FACTOR
        if (matching.size < target) {
            val helper = ContentsHelper(
                updateMode,
                ArtworkFilter.purityQuery(puritySelection, hasApiKey),
                ArtworkFilter.categoryQuery(categorySelection),
                prefs.getString("pref_tagFilter", "").orEmpty(),
                prefs.getString("pref_atleast", "").orEmpty(),
                ArtworkFilter.ratiosQuery(
                    prefs.getStringSet("pref_aspectRatioSelect_v2", emptySet()) ?: emptySet()
                ),
                prefs.getString("pref_topRange", "1M") ?: "1M",
                prefs.getString("pref_order", "desc") ?: "desc",
                prefs.getString("pref_seed", "").orEmpty(),
                prefs.getString("pref_colors", "").orEmpty()
            )

            var page = 1
            while (matching.size < target && page <= MAX_PAGES) {
                Log.i(LOG_TAG, "Fetching page $page, ${matching.size} matching candidates so far")
                val contents = helper.getPage(page)
                candidates = (candidates + contents.data).distinctBy { it.id }
                matching = candidates.filter(::matches)
                if (page >= contents.meta.lastPage) break
                page++
            }
        }

        if (matching.isEmpty()) {
            clearRankingCache(prefs)
            throw FilterMatchNotFoundException("No Wallhaven artwork matched the current filters")
        }

        val toDownload = matching.shuffled().take(numToDownload)
        val artworkList = toDownload.mapNotNull { candidate ->
            try {
                downloadArtwork(candidate, updateMode)
            } catch (e: CorruptFileException) {
                Log.w(LOG_TAG, "Skipping corrupt artwork ${candidate.id}", e)
                null
            }
        }

        // Keep the candidates we did not use for the next run
        val downloadedIds = toDownload.map { it.id }.toSet()
        val remaining = candidates.filterNot { it.id in downloadedIds }
        if (remaining.isEmpty()) {
            clearRankingCache(prefs)
        } else {
            saveRankingCache(prefs, remaining)
        }
        return artworkList
    }

    private fun downloadArtwork(candidate: RankingArtwork, updateMode: String): Artwork {
        val url = imageUrl(candidate.path)
        Log.i(LOG_TAG, "Downloading ${candidate.id} from $url")
        OkHttpSingleton.imageClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw CorruptFileException("HTTP ${response.code} downloading ${candidate.id}")
            }
            val body = response.body ?: throw CorruptFileException("Empty response for ${candidate.id}")
            val localUri = downloadImage(body, candidate.id, prefs.getBoolean(PREF_STORE_EXTERNAL, false))
            Log.i(LOG_TAG, "Download completed: ${candidate.id}")
            return Artwork.Builder()
                .title(candidate.id)
                .byline(bylineOf(candidate))
                .attribution(attributionOf(candidate, updateMode))
                .persistentUri(localUri)
                .token(candidate.id)
                .webUri(Uri.parse(candidate.url))
                .build()
        }
    }

    /** Routes downloads through a user-supplied mirror of Wallhaven's image host when enabled. */
    private fun imageUrl(path: String): String {
        if (!prefs.getBoolean("pref_useWallhavenProxy", false)) return path
        val host = prefs.getString("pref_wallhavenProxyHost", "").orEmpty().trim()
        if (host.isEmpty()) return path
        return path.replace(WallhavenProviderConst.WALLHAVEN_IMAGE_HOST, host)
    }

    private fun bylineOf(artwork: RankingArtwork): String =
        "${artwork.width}x${artwork.height} • 👁 ${formatNumber(artwork.views)} • ❤️ ${formatNumber(artwork.favorites)}"

    private fun attributionOf(artwork: RankingArtwork, updateMode: String): String = buildString {
        artwork.createdAt?.takeIf { it.isNotBlank() }?.let {
            append(it.take(10))
            append(" • ")
        }
        append(sortingLabel(updateMode))
        append(" • ")
        append(artwork.purity.uppercase(Locale.US))
        artwork.category?.takeIf { it.isNotBlank() }?.let {
            append(" • ")
            append(it.replaceFirstChar { first -> first.uppercase() })
        }
        artwork.uploader?.username?.takeIf { it.isNotBlank() }?.let {
            append(" • by ")
            append(it)
        }
    }

    private fun sortingLabel(updateMode: String): String = when (updateMode) {
        "toplist" -> applicationContext.getString(R.string.attr_toplist)
        "hot" -> applicationContext.getString(R.string.attr_hot)
        else -> updateMode.split('_').joinToString(" ") { it.replaceFirstChar { first -> first.uppercase() } }
    }

    private fun formatNumber(num: Int): String = when {
        num >= 1_000_000 -> String.format(Locale.US, "%.1fM", num / 1_000_000.0)
        num >= 1_000 -> String.format(Locale.US, "%.1fK", num / 1_000.0)
        else -> num.toString()
    }

    // Batch load all existing artwork tokens from Muzei provider
    private fun loadExistingArtworkTokens(): Set<String> {
        val tokens = mutableSetOf<String>()
        applicationContext.contentResolver.query(
            getProviderClient(applicationContext, WallhavenArtProvider::class.java).contentUri,
            arrayOf(ProviderContract.Artwork.TOKEN),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { tokens.add(it) }
            }
        }
        return tokens
    }

    // Batch load all deleted artwork IDs from Room database
    private fun loadDeletedArtworkIds(): Set<String> =
        AppDatabase.getInstance(applicationContext).deletedArtworkIdDao().getAllIds().toSet()

    // Compute a hash of all query-affecting settings for cache invalidation
    private fun computeQueryHash(sharedPrefs: SharedPreferences): String {
        val params = listOf(
            sharedPrefs.getString("pref_updateMode", "toplist"),
            sharedPrefs.getStringSet("pref_rankingFilterSelect", setOf("sfw"))?.sorted()?.joinToString(),
            sharedPrefs.getStringSet("pref_rankingCategorySelect", setOf("general"))?.sorted()?.joinToString(),
            sharedPrefs.getString("pref_tagFilter", ""),
            sharedPrefs.getString("pref_atleast", ""),
            sharedPrefs.getStringSet("pref_aspectRatioSelect_v2", setOf(""))?.sorted()?.joinToString(),
            sharedPrefs.getString("pref_topRange", "1M"),
            sharedPrefs.getString("pref_order", "desc"),
            sharedPrefs.getString("pref_seed", ""),
            sharedPrefs.getString("pref_colors", "")
        )
        return params.joinToString("|").hashCode().toString()
    }

    // Load cached ranking candidates if cache is valid
    private fun loadRankingCache(sharedPrefs: SharedPreferences): List<RankingArtwork>? {
        val timestamp = sharedPrefs.getLong(CACHE_KEY_TIMESTAMP, 0)
        if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) {
            Log.d(LOG_TAG, "Cache expired")
            return null
        }
        if (sharedPrefs.getString(CACHE_KEY_HASH, "") != computeQueryHash(sharedPrefs)) {
            Log.d(LOG_TAG, "Cache invalidated: query settings changed")
            return null
        }

        val json = sharedPrefs.getString(CACHE_KEY_DATA, null) ?: return null
        return try {
            cacheAdapter.fromJson(json)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to deserialize cache", e)
            null
        }
    }

    // Save ranking candidates to cache
    private fun saveRankingCache(sharedPrefs: SharedPreferences, candidates: List<RankingArtwork>) {
        try {
            sharedPrefs.edit()
                .putString(CACHE_KEY_DATA, cacheAdapter.toJson(candidates))
                .putLong(CACHE_KEY_TIMESTAMP, System.currentTimeMillis())
                .putString(CACHE_KEY_HASH, computeQueryHash(sharedPrefs))
                .apply()
            Log.d(LOG_TAG, "Saved ${candidates.size} candidates to cache")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to serialize cache", e)
        }
    }

    // Clear the ranking cache
    private fun clearRankingCache(sharedPrefs: SharedPreferences) {
        sharedPrefs.edit()
            .remove(CACHE_KEY_DATA)
            .remove(CACHE_KEY_TIMESTAMP)
            .remove(CACHE_KEY_HASH)
            .apply()
    }

    private fun downloadImage(
        responseBody: ResponseBody,
        filename: String,
        storeInExtStorage: Boolean
    ): Uri {
        val fileType = responseBody.contentType()
        return when {
            !storeInExtStorage -> downloadImageInternal(responseBody, filename, fileType)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                downloadImageExternalApi29(responseBody, filename, fileType)
            else -> downloadImageExternalApi28(responseBody, filename, fileType)
        }
    }

    /** Extension to store the artwork under, taken from the response content type. */
    private fun extensionOf(fileType: MediaType?): String {
        val subtype = fileType?.subtype ?: return "jpg"
        return if (subtype == "jpeg") "jpg" else subtype
    }

    /**
     * Streams [responseBody] into [sink], failing when fewer bytes arrive than the response promised.
     * A truncated wallpaper is worse than no wallpaper, and OkHttp happily hands back a short body
     * after a flaky connection.
     */
    private fun copyBodyTo(responseBody: ResponseBody, sink: BufferedSink) {
        val expected = responseBody.contentLength()
        val written = sink.writeAll(responseBody.source())
        if (expected >= 0 && written != expected) {
            throw CorruptFileException("Truncated download: got $written of $expected bytes")
        }
    }

    // Function used to download images to internal storage
    // Internal storage in this case is /storage/emulated/0/Android/data/com.brewaco3.muzei.wallhaven/files
    private fun downloadImageInternal(
        responseBody: ResponseBody,
        filename: String,
        fileType: MediaType?,
    ): Uri {
        Log.i(LOG_TAG, "Downloading artwork, internal")
        val directory = applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: applicationContext.filesDir
        val image = File(directory, "$filename.${extensionOf(fileType)}")
        if (image.exists()) {
            Log.i(LOG_TAG, "Artwork exists, early exit")
            return Uri.fromFile(image)
        }

        try {
            image.sink().buffer().use { copyBodyTo(responseBody, it) }
        } catch (e: Exception) {
            // A partial file would be mistaken for a finished download next time
            image.delete()
            throw e
        }
        Log.i(LOG_TAG, "Downloaded")
        return Uri.fromFile(image)
    }

    // Function to download images to external storage
    // External storage in this case refers to /storage/emulated/0/Pictures/WallhavenForMuzei3
    // Option is also there to store onto an SD card if present
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun downloadImageExternalApi29(
        responseBody: ResponseBody,
        filename: String,
        fileType: MediaType?,
    ): Uri {
        Log.i(LOG_TAG, "Downloading artwork, scoped storage")
        val contentResolver = applicationContext.contentResolver

        // If image already exists on the filesystem, then we can skip downloading it
        getExistingImageExternalApi29(contentResolver, filename)?.let {
            Log.i(LOG_TAG, "Image already exists, early exiting")
            return it
        }

        // Inserting the filename, relative path within the /Pictures folder, and MIME type into the content provider
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/WallhavenForMuzei3"
            )
            put(MediaStore.MediaColumns.MIME_TYPE, fileType?.toString() ?: "image/jpeg")
        }

        // Default option is VOLUME_EXTERNAL_PRIMARY
        // If the user has selected the option to store onto an SD card, first we check if there is more than
        // one storage mounted, then iterate the mounted volumes looking for one that is not the primary one.
        // The manual iterating is required as I received a user report where VOLUME_EXTERNAL_PRIMARY was not
        // the first entry.
        // Older builds wrote the preference with stray quotes around the value, so strip them
        var volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY
        if (prefs.getString("pref_selectWhichExtStorage", "phone").orEmpty().trim('"') != "phone") {
            MediaStore.getExternalVolumeNames(applicationContext).takeIf { it.size > 1 }
                ?.let { volumeNames ->
                    for (volume in volumeNames) {
                        if (volume != MediaStore.VOLUME_EXTERNAL_PRIMARY) {
                            volumeName = volume
                        }
                    }
                }
        }

        val imageUri = contentResolver.insert(
            MediaStore.Images.Media.getContentUri(volumeName),
            contentValues
        ) ?: throw CorruptFileException("Could not create a MediaStore entry for $filename")

        val output = contentResolver.openOutputStream(imageUri)
        if (output == null) {
            contentResolver.delete(imageUri, null, null)
            throw CorruptFileException("Could not open $imageUri for writing")
        }

        try {
            output.sink().buffer().use { copyBodyTo(responseBody, it) }
        } catch (e: Exception) {
            contentResolver.delete(imageUri, null, null)
            throw e
        }

        Log.i(LOG_TAG, "Downloaded")
        return imageUri
    }

    /* Checking if existing copy of images exists*/
    // Returns the Uri of an image with matching filename, otherwise returns null
    private fun getExistingImageExternalApi29(
        contentResolver: ContentResolver,
        filename: String
    ): Uri? {
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("$filename%"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                Log.v(LOG_TAG, "Duplicate found")
                return ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                )
            }
        }
        return null
    }

    // Function used when downloading on external storage on API 28 or lower
    @Suppress("DEPRECATION")
    private fun downloadImageExternalApi28(
        responseBody: ResponseBody,
        filename: String,
        fileType: MediaType?,
    ): Uri {
        Log.i(LOG_TAG, "Downloading artwork, legacy external storage")
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "WallhavenForMuzei3"
        )
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val image = File(directory, "$filename.${extensionOf(fileType)}")
        if (image.exists()) {
            Log.i(LOG_TAG, "Artwork exists, early exit")
            return Uri.fromFile(image)
        }

        try {
            image.sink().buffer().use { copyBodyTo(responseBody, it) }
        } catch (e: Exception) {
            image.delete()
            throw e
        }

        // Broadcast the addition of the new media file, otherwise it does not show up in the gallery
        MediaScannerConnection.scanFile(applicationContext, arrayOf(image.toString()), null, null)
        Log.i(LOG_TAG, "Downloaded")
        return Uri.fromFile(image)
    }
}
