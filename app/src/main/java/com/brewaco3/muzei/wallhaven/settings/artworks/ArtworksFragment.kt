/*
 *     This file is part of WallhavenforMuzei3.
 *
 *     WallhavenforMuzei3 is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program  is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.brewaco3.muzei.wallhaven.settings.artworks

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.OperationApplicationException
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.RemoteException
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brewaco3.muzei.wallhaven.AppDatabase
import com.brewaco3.muzei.wallhaven.BuildConfig
import com.brewaco3.muzei.wallhaven.R
import com.brewaco3.muzei.wallhaven.provider.WallhavenArtProvider
import com.brewaco3.muzei.wallhaven.settings.applyNavigationBarPadding
import com.google.android.apps.muzei.api.provider.ProviderContract
import com.google.android.apps.muzei.api.provider.ProviderContract.Artwork.TOKEN
import com.google.android.apps.muzei.api.provider.ProviderContract.getProviderClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

class ArtworksFragment : Fragment() {
    private lateinit var adapter: ArtworksAdapter

    companion object {
        val SELECTED_ITEMS = mutableListOf<ArtworkItem>()
        private const val PREF_ARTWORK_COLUMNS = "pref_artwork_columns"
        private const val SAVED_DIRECTORY = "WallhavenForMuzei3/Saved"
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when the tab becomes visible (e.g., after Force Refresh or Clear Cache)
        if (::adapter.isInitialized) {
            SELECTED_ITEMS.clear()
            adapter.updateData(getInitialArtworkItemList(requireContext()))
            updateEmptyState()
        }
        activity?.findViewById<LinearLayout>(R.id.fab_container)?.visibility = View.VISIBLE
        activity?.findViewById<FloatingActionButton>(R.id.fab_delete)
            ?.setOnClickListener { deleteSelectedItems() }
        activity?.findViewById<FloatingActionButton>(R.id.fab_save)
            ?.setOnClickListener { saveSelectedItems() }
    }

    override fun onPause() {
        super.onPause()
        activity?.findViewById<LinearLayout>(R.id.fab_container)?.visibility = View.GONE
        activity?.findViewById<FloatingActionButton>(R.id.fab_delete)?.setOnClickListener(null)
        activity?.findViewById<FloatingActionButton>(R.id.fab_save)?.setOnClickListener(null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val emptyView = view?.findViewById<TextView>(R.id.empty_view) ?: return
        emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_artwork_list, container, false)
        val recyclerView: RecyclerView = root.findViewById(R.id.list)
        recyclerView.applyNavigationBarPadding()
        val context = recyclerView.context

        // Dynamically sets the number of grid columns.
        // The ceiling gives a minimum of 2 columns, and scales well up to a Nexus 10 tablet (1280dp width)
        val displayMetrics = context.resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density

        val minSpanCount = 2
        val maxSpanCount = (dpWidth / 100).toInt().coerceAtLeast(minSpanCount)
        val initialSpanCount = ceil(dpWidth.toDouble() / 200).toInt().coerceIn(minSpanCount, maxSpanCount)

        // Load the column count the user last pinched to, falling back to the calculated default
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val startingSpanCount =
            sharedPrefs.getInt(PREF_ARTWORK_COLUMNS, initialSpanCount).coerceIn(minSpanCount, maxSpanCount)

        val layoutManager = GridLayoutManager(context, startingSpanCount)
        recyclerView.layoutManager = layoutManager
        adapter = ArtworksAdapter(getInitialArtworkItemList(context))

        // Headers take the full width, thumbnails take a single cell
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (adapter.getItemViewType(position) == ArtworksAdapter.TYPE_HEADER) {
                    layoutManager.spanCount
                } else {
                    1
                }
        }

        val scaleGestureDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                var scaleFactor = 1f

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scaleFactor *= detector.scaleFactor
                    val currentSpan = layoutManager.spanCount

                    // Pinch out -> fewer columns and larger thumbnails, pinch in -> the opposite
                    val pinchOut = scaleFactor > 1.2f
                    val pinchIn = scaleFactor < 0.8f
                    val newSpan = when {
                        pinchOut && currentSpan > minSpanCount -> currentSpan - 1
                        pinchIn && currentSpan < maxSpanCount -> currentSpan + 1
                        else -> currentSpan
                    }

                    if (newSpan != currentSpan) {
                        layoutManager.spanCount = newSpan
                        adapter.notifyItemRangeChanged(0, adapter.itemCount)
                        sharedPrefs.edit().putInt(PREF_ARTWORK_COLUMNS, newSpan).apply()
                    }
                    // Start measuring the next step from here, so holding a pinch at either limit
                    // does not leave a huge accumulated factor behind
                    if (pinchOut || pinchIn) scaleFactor = 1f
                    return true
                }
            }
        )

        recyclerView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            false // Return false to allow normal touch events (scrolling, clicking)
        }

        recyclerView.adapter = adapter

        return root
    }

    private fun deleteSelectedItems() {
        val context = requireContext()
        if (SELECTED_ITEMS.isEmpty()) {
            Snackbar.make(
                requireView(), R.string.snackbar_selectArtworkFirst,
                Snackbar.LENGTH_LONG
            ).show()
            return
        }
        val numberDeleted = SELECTED_ITEMS.size
        val deletedItems = SELECTED_ITEMS.toList()

        // Deletes the artwork items from the ArrayList used as backing for the RecyclerView
        adapter.removeItems(deletedItems)
        updateEmptyState()

        // Delete the artworks themselves from the ContentProvider, and remember the ids so they are
        // never downloaded again
        val operations = ArrayList<ContentProviderOperation>()
        val deletedIds = mutableListOf<DeletedArtworkIdEntity>()
        for (artworkItem in deletedItems) {
            operations.add(
                ContentProviderOperation
                    .newDelete(getProviderClient(context, WallhavenArtProvider::class.java).contentUri)
                    .withSelection("$TOKEN = ?", arrayOf(artworkItem.token))
                    .build()
            )
            deletedIds.add(DeletedArtworkIdEntity(artworkItem.token))
        }
        SELECTED_ITEMS.clear()

        try {
            context.contentResolver.applyBatch(BuildConfig.APPLICATION_ID + ".provider", operations)
        } catch (e: RemoteException) {
            e.printStackTrace()
        } catch (e: OperationApplicationException) {
            e.printStackTrace()
        }

        val appDatabase = AppDatabase.getInstance(context)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                appDatabase.deletedArtworkIdDao().insertDeletedArtworkId(deletedIds)
            }
        }

        Snackbar.make(
            requireView(),
            resources.getQuantityString(
                R.plurals.snackbar_deletedArtworks, numberDeleted, numberDeleted
            ),
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun saveSelectedItems() {
        if (SELECTED_ITEMS.isEmpty()) {
            Snackbar.make(
                requireView(), R.string.snackbar_selectArtworkFirst,
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        val context = requireContext()
        val itemsToSave = SELECTED_ITEMS.toList()

        lifecycleScope.launch {
            var savedCount = 0
            withContext(Dispatchers.IO) {
                itemsToSave.forEach { item ->
                    if (copyToExternalStorage(context, item)) savedCount++
                }
            }

            // Clear the selection after saving
            adapter.clearSelection()

            val skippedCount = itemsToSave.size - savedCount
            val message = if (skippedCount > 0) {
                resources.getQuantityString(
                    R.plurals.snackbar_savedArtworksWithSkipped, savedCount, savedCount, skippedCount
                )
            } else {
                resources.getQuantityString(R.plurals.snackbar_savedArtworks, savedCount, savedCount)
            }
            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun copyToExternalStorage(context: Context, item: ArtworkItem): Boolean {
        return try {
            val fileName = "${item.token}.${extensionOf(context, item.persistent_uri)}"
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(fileName.substringAfterLast('.')) ?: "image/jpeg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Check if the file already exists in MediaStore
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
                        "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                    arrayOf(fileName, "$SAVED_DIRECTORY/"),
                    null
                )?.use { if (it.count > 0) return false }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, SAVED_DIRECTORY)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return false

                context.contentResolver.openInputStream(item.persistent_uri)?.use { input ->
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        input.copyTo(output)
                    } ?: return false
                } ?: return false
                true
            } else {
                // Legacy file access for Android 9 and below
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    SAVED_DIRECTORY
                )
                if (!directory.exists() && !directory.mkdirs()) return false

                val outputFile = File(directory, fileName)
                if (outputFile.exists()) return false

                context.contentResolver.openInputStream(item.persistent_uri)?.use { input ->
                    outputFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return false
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Wallhaven serves both jpeg and png wallpapers, so the copy keeps whichever one it was. */
    private fun extensionOf(context: Context, uri: Uri): String {
        val sourceName = uri.lastPathSegment.orEmpty()
        if (sourceName.contains('.')) {
            return sourceName.substringAfterLast('.').lowercase()
        }
        val mimeType = context.contentResolver.getType(uri) ?: return "jpg"
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
    }

    private fun getInitialArtworkItemList(context: Context): MutableList<ArtworkItem> {
        val listOfArtworkItem = mutableListOf<ArtworkItem>()
        val projection = arrayOf(TOKEN, ProviderContract.Artwork.PERSISTENT_URI, ProviderContract.Artwork.DATE_ADDED)
        val conResUri = getProviderClient(context, WallhavenArtProvider::class.java).contentUri
        context.contentResolver.query(conResUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val token = cursor.getString(cursor.getColumnIndexOrThrow(TOKEN))
                val persistentUri =
                    Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(ProviderContract.Artwork.PERSISTENT_URI)))
                val dateAdded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(ProviderContract.Artwork.DATE_ADDED)
                )
                listOfArtworkItem.add(ArtworkItem(token, persistentUri, dateAdded))
            }
        }
        // Sort by date descending
        listOfArtworkItem.sortByDescending { it.dateAdded }
        return listOfArtworkItem
    }
}
