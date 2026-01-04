/*
 *     This file is part of PixivforMuzei3.
 *
 *     PixivforMuzei3 is free software: you can redistribute it and/or modify
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
import android.view.ScaleGestureDetector
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brewaco3.muzei.wallhaven.AppDatabase
import com.brewaco3.muzei.wallhaven.BuildConfig
import com.brewaco3.muzei.wallhaven.R
import com.brewaco3.muzei.wallhaven.provider.WallhavenArtProvider
import com.google.android.apps.muzei.api.provider.ProviderContract
import com.google.android.apps.muzei.api.provider.ProviderContract.Artwork.TOKEN
import com.google.android.apps.muzei.api.provider.ProviderContract.getProviderClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.math.ceil

class ArtworksFragment : Fragment() {
    private lateinit var adapter: ArtworksAdapter

    companion object {
        val SELECTED_ITEMS = mutableListOf<ArtworkItem>()
        private const val PREF_ARTWORK_COLUMNS = "pref_artwork_columns"
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when tab becomes visible (e.g., after Force Refresh or Clear Cache)
        if (::adapter.isInitialized) {
            SELECTED_ITEMS.clear()
            val freshData = getInitialArtworkItemList(requireContext())
            adapter.updateData(freshData)
        }
        activity?.findViewById<LinearLayout>(R.id.fab_container)?.visibility = View.VISIBLE
        activity?.findViewById<FloatingActionButton>(R.id.fab_delete)?.setOnClickListener { deleteSelectedItems() }
        activity?.findViewById<FloatingActionButton>(R.id.fab_save)?.setOnClickListener { saveSelectedItems() }
    }

    override fun onPause() {
        super.onPause()
        activity?.findViewById<LinearLayout>(R.id.fab_container)?.visibility = View.GONE
        activity?.findViewById<FloatingActionButton>(R.id.fab_delete)?.setOnClickListener(null)
        activity?.findViewById<FloatingActionButton>(R.id.fab_save)?.setOnClickListener(null)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val linearLayoutView = inflater.inflate(R.layout.fragment_artwork_list, container, false)
        val recyclerView: RecyclerView = linearLayoutView.findViewById(R.id.list)
        val context = recyclerView.context

        // Dynamically sets number of grid columns
        // The ceiling gives a minimum of 2 columns, and scales well up to a Nexus 10 tablet (1280dp width)
        val displayMetrics = context.resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density

        val minSpanCount = 2
        val maxSpanCount = (dpWidth / 100).toInt().coerceAtLeast(minSpanCount)
        val initialSpanCount = ceil(dpWidth.toDouble() / 200).toInt().coerceIn(minSpanCount, maxSpanCount)

        // Load saved column count, falling back to calculated default
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val savedSpanCount = sharedPrefs.getInt(PREF_ARTWORK_COLUMNS, initialSpanCount)
        val startingSpanCount = savedSpanCount.coerceIn(minSpanCount, maxSpanCount)

        val layoutManager = GridLayoutManager(context, startingSpanCount)
        recyclerView.layoutManager = layoutManager
        adapter = ArtworksAdapter(getInitialArtworkItemList(context))

        // Set span size lookup for headers
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (adapter.getItemViewType(position)) {
                    0 -> layoutManager.spanCount // TYPE_HEADER (dynamic lookup)
                    else -> 1
                }
            }
        }

        // Pinch-to-zoom to change column count
        val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            var scaleFactor = 1f

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                val currentSpan = layoutManager.spanCount

                if (scaleFactor > 1.2f) { // Pinch out -> Decrease columns (images get larger)
                    if (currentSpan > minSpanCount) {
                        layoutManager.spanCount = currentSpan - 1
                        adapter.notifyItemRangeChanged(0, adapter.itemCount)
                        sharedPrefs.edit().putInt(PREF_ARTWORK_COLUMNS, layoutManager.spanCount).apply()
                        scaleFactor = 1f
                    }
                } else if (scaleFactor < 0.8f) { // Pinch in -> Increase columns (images get smaller)
                    if (currentSpan < maxSpanCount) {
                        layoutManager.spanCount = currentSpan + 1
                        adapter.notifyItemRangeChanged(0, adapter.itemCount)
                        sharedPrefs.edit().putInt(PREF_ARTWORK_COLUMNS, layoutManager.spanCount).apply()
                        scaleFactor = 1f
                    }
                }
                return true
            }
        })

        recyclerView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            false // Return false to allow normal touch events (scrolling, clicking)
        }

        recyclerView.adapter = adapter

        return linearLayoutView
    }

    private fun deleteSelectedItems() {
        val context = requireContext()
        // Early exit when no artworks are selected for deletion
        if (SELECTED_ITEMS.isEmpty()) {
            Snackbar.make(
                requireView(), R.string.snackbar_selectArtworkFirst,
                Snackbar.LENGTH_LONG
            ).show()
            return
        }
        val numberDeleted = SELECTED_ITEMS.size

        // Deletes the artwork items from the ArrayList used as backing for the RecyclerView
        adapter.removeItems(SELECTED_ITEMS)

        // We insert the deleted artwork ID's
        val listOfDeletedIds: MutableList<DeletedArtworkIdEntity> = mutableListOf()

        // Now to delete the Artwork's themselves from the ContentProvider
        val operations = ArrayList<ContentProviderOperation>()
        val selection = "$TOKEN = ?"
        // Builds a new delete operation for every selected artwork
        for (artworkItem in SELECTED_ITEMS) {
            val operation = ContentProviderOperation
                .newDelete(getProviderClient(context, WallhavenArtProvider::class.java).contentUri)
                .withSelection(selection, arrayOf(artworkItem.token))
                .build()
            operations.add(operation)

            // Used to remember which artworks have been deleted, so we don't download them again
            listOfDeletedIds.add(DeletedArtworkIdEntity(artworkItem.token))
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
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch(Dispatchers.IO) {
            appDatabase.deletedArtworkIdDao().insertDeletedArtworkId(listOfDeletedIds.toList())
        }

        Snackbar.make(
            requireView(), numberDeleted.toString() + " " + getString(R.string.snackbar_deletedArtworks),
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

        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            var savedCount = 0
            withContext(Dispatchers.IO) {
                for (item in itemsToSave) {
                    if (copyToExternalStorage(context, item)) {
                        savedCount++
                    }
                }
            }

            // Clear selection after saving
            SELECTED_ITEMS.forEach { it.selected = false }
            SELECTED_ITEMS.clear()
            adapter.notifyDataSetChanged()

            val skippedCount = itemsToSave.size - savedCount
            val message = if (skippedCount > 0) {
                "$savedCount ${getString(R.string.snackbar_savedArtworksWithSkipped, skippedCount)}"
            } else {
                "$savedCount ${getString(R.string.snackbar_savedArtworks)}"
            }
            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun copyToExternalStorage(context: Context, item: ArtworkItem): Boolean {
        return try {
            val fileName = "${item.token}.jpg"
            val relativePath = Environment.DIRECTORY_PICTURES + "/WallhavenForMuzei3/Saved"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Check if file already exists in MediaStore
                val existsQuery = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                    arrayOf(fileName, "$relativePath/"),
                    null
                )
                val alreadyExists = existsQuery?.use { it.count > 0 } ?: false
                if (alreadyExists) return false

                val inputStream = context.contentResolver.openInputStream(item.persistent_uri)
                    ?: return false

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return false

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
                true
            } else {
                // Legacy file access for Android 9 and below
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WallhavenForMuzei3/Saved")
                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val outputFile = File(directory, fileName)
                if (outputFile.exists()) return false

                val inputStream = context.contentResolver.openInputStream(item.persistent_uri)
                    ?: return false
                outputFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getInitialArtworkItemList(context: Context): MutableList<ArtworkItem> {
        val listOfArtworkItem = mutableListOf<ArtworkItem>()
        val projection = arrayOf("token", "title", "persistent_uri", "date_added")
        val conResUri = getProviderClient(context, WallhavenArtProvider::class.java).contentUri
        val cursor = context.contentResolver.query(conResUri, projection, null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val token = cursor.getString(cursor.getColumnIndexOrThrow(TOKEN))
                //val title = cursor.getString(cursor.getColumnIndexOrThrow(ProviderContract.Artwork.TITLE))
                val persistentUri =
                    Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(ProviderContract.Artwork.PERSISTENT_URI)))
                val dateAdded = try {
                    cursor.getLong(cursor.getColumnIndexOrThrow(ProviderContract.Artwork.DATE_ADDED))
                } catch (e: IllegalArgumentException) {
                    0L
                }
                listOfArtworkItem.add(ArtworkItem(token, persistentUri, dateAdded))
            }
            cursor.close()
        }
        // Sort by date descending
        listOfArtworkItem.sortByDescending { it.dateAdded }
        return listOfArtworkItem
    }
}
