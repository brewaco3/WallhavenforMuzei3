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
package com.brewaco3.muzei.wallhaven.settings.deleteArtwork

import android.content.ContentProviderOperation
import android.content.Context
import android.content.OperationApplicationException
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
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
import java.util.*
import kotlin.math.ceil

class ArtworkDeletionFragment : Fragment() {
    private lateinit var adapter: ArtworkDeletionAdapter

    companion object {
        val SELECTED_ITEMS = mutableListOf<ArtworkItem>()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when tab becomes visible (e.g., after Force Refresh or Clear Cache)
        if (::adapter.isInitialized) {
            SELECTED_ITEMS.clear()
            val freshData = getInitialArtworkItemList(requireContext())
            adapter.updateData(freshData)
        }
        activity?.findViewById<FloatingActionButton>(R.id.fab_delete)?.let { fab ->
            fab.show()
            fab.setOnClickListener { deleteSelectedItems() }
        }
    }

    override fun onPause() {
        super.onPause()
        activity?.findViewById<FloatingActionButton>(R.id.fab_delete)?.let { fab ->
            fab.setOnClickListener(null)
            fab.visibility = View.GONE
        }
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
        val spanCount = ceil(dpWidth.toDouble() / 200).toInt()
        val layoutManager = GridLayoutManager(context, spanCount)
        recyclerView.layoutManager = layoutManager
        adapter = ArtworkDeletionAdapter(getInitialArtworkItemList(context))

        // Set span size lookup for headers
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (adapter.getItemViewType(position)) {
                    0 -> spanCount // TYPE_HEADER (hardcoded 0 to match adapter companion object)
                    else -> 1
                }
            }
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
