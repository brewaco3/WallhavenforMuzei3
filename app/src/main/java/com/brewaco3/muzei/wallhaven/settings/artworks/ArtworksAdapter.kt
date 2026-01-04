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

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brewaco3.muzei.wallhaven.R
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.*

class ArtworksAdapter(private val artworkItems: MutableList<ArtworkItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class DeletionItem {
        data class Header(val date: String, val items: List<ArtworkItem>) : DeletionItem()
        data class Item(val artworkItem: ArtworkItem) : DeletionItem()
    }

    private var displayItems: MutableList<DeletionItem> = mutableListOf()

    init {
        updateDisplayItems()
    }

    private fun updateDisplayItems() {
        displayItems.clear()
        val grouped = artworkItems.groupBy {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.dateAdded))
        }
        
        // Sort groups by date descending (keys are yyyy-MM-dd strings)
        grouped.toSortedMap(compareByDescending { it }).forEach { (date, items) ->
            displayItems.add(DeletionItem.Header(date, items))
            items.forEach { displayItems.add(DeletionItem.Item(it)) }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is DeletionItem.Header -> TYPE_HEADER
            is DeletionItem.Item -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_artwork_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_ITEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.fragment_artwork, parent, false)
                ItemViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is DeletionItem.Header -> (holder as HeaderViewHolder).bind(item)
            is DeletionItem.Item -> (holder as ItemViewHolder).bind(item.artworkItem)
        }
    }

    override fun getItemCount(): Int {
        return displayItems.size
    }

    fun removeItems(artworkItemsToDelete: List<ArtworkItem>) {
        artworkItems.removeAll(artworkItemsToDelete)
        updateDisplayItems()
        notifyDataSetChanged()
    }

    fun updateData(newItems: MutableList<ArtworkItem>) {
        artworkItems.clear()
        artworkItems.addAll(newItems)
        updateDisplayItems()
        notifyDataSetChanged()
    }

    // Helper for GridLayoutManager span size
    val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return when (getItemViewType(position)) {
                TYPE_HEADER -> 3 // Span full width (assuming 3 columns, will adjust in Fragment)
                else -> 1
            }
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.header_title)
        private val checkBox: CheckBox = view.findViewById(R.id.header_checkbox)

        fun bind(header: DeletionItem.Header) {
            title.text = header.date
            
            // Check state: if all items in this group are selected
            val allSelected = header.items.all { it.selected }
            checkBox.isChecked = allSelected

            checkBox.setOnClickListener {
                val isChecked = checkBox.isChecked
                header.items.forEach { item ->
                    if (item.selected != isChecked) {
                        item.selected = isChecked
                        if (isChecked) {
                            ArtworksFragment.SELECTED_ITEMS.add(item)
                        } else {
                            ArtworksFragment.SELECTED_ITEMS.remove(item)
                        }
                    }
                }
                notifyDataSetChanged()
            }
        }
    }

    inner class ItemViewHolder(private val mView: View) : RecyclerView.ViewHolder(mView), View.OnClickListener {
        private val mImageView: ImageView = mView.findViewById(R.id.image)
        private lateinit var mArtworkItem: ArtworkItem
        private val color = Color.BLUE // Fallback color to avoid potential theme issues

        fun bind(artworkItem: ArtworkItem) {
            mArtworkItem = artworkItem
            Glide.with(mView)
                .load(artworkItem.persistent_uri)
                .centerCrop()
                .into(mImageView)

            updateSelectionState()
        }

        private fun updateSelectionState() {
            if (mArtworkItem.selected) {
                mImageView.setColorFilter(Color.argb(130, Color.red(color), Color.green(color), Color.blue(color)))
            } else {
                mImageView.clearColorFilter()
            }
        }

        override fun onClick(view: View) {
            if (absoluteAdapterPosition != RecyclerView.NO_POSITION) {
                mArtworkItem.selected = !mArtworkItem.selected
                if (mArtworkItem.selected) {
                    ArtworksFragment.SELECTED_ITEMS.add(mArtworkItem)
                } else {
                    ArtworksFragment.SELECTED_ITEMS.remove(mArtworkItem)
                }
                updateSelectionState()
                // Notify header to update checkbox
                // Ideally we should find the header position and notifyItemChanged, 
                // but notifyDataSetChanged is safer/easier for now given the structure
                notifyDataSetChanged() 
            }
        }

        init {
            mView.setOnClickListener(this)
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}
