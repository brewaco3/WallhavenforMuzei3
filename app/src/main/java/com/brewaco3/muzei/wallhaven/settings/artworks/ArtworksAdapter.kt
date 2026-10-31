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

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.brewaco3.muzei.wallhaven.R
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArtworksAdapter(private val artworkItems: MutableList<ArtworkItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /**
     * Each entry carries a snapshot of the state it was built from, because
     * [ArtworkItem.selected] is mutated in place and DiffUtil compares whole lists.
     */
    sealed class DeletionItem {
        data class Header(
            val date: String,
            val items: List<ArtworkItem>,
            val allSelected: Boolean
        ) : DeletionItem()

        data class Item(val artworkItem: ArtworkItem, val selected: Boolean) : DeletionItem()
    }

    private val displayItems: MutableList<DeletionItem> = buildDisplayItems().toMutableList()

    private fun buildDisplayItems(): List<DeletionItem> {
        val formatter = SimpleDateFormat(DATE_PATTERN, Locale.getDefault())
        val grouped = artworkItems.groupBy { formatter.format(Date(it.dateAdded)) }

        // Keys are yyyy-MM-dd, so reverse string order is newest first
        return grouped.toSortedMap(compareByDescending { it }).flatMap { (date, items) ->
            buildList {
                add(DeletionItem.Header(date, items, items.all { it.selected }))
                items.forEach { add(DeletionItem.Item(it, it.selected)) }
            }
        }
    }

    /** Rebuilds the list and rebinds only the rows that actually changed. */
    private fun refresh() {
        val previous = displayItems.toList()
        val updated = buildDisplayItems()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = previous.size

            override fun getNewListSize(): Int = updated.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
                when {
                    previous[oldPosition] is DeletionItem.Header &&
                        updated[newPosition] is DeletionItem.Header ->
                        (previous[oldPosition] as DeletionItem.Header).date ==
                            (updated[newPosition] as DeletionItem.Header).date

                    previous[oldPosition] is DeletionItem.Item &&
                        updated[newPosition] is DeletionItem.Item ->
                        (previous[oldPosition] as DeletionItem.Item).artworkItem.token ==
                            (updated[newPosition] as DeletionItem.Item).artworkItem.token

                    else -> false
                }

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
                previous[oldPosition] == updated[newPosition]
        })

        displayItems.clear()
        displayItems.addAll(updated)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemViewType(position: Int): Int = when (displayItems[position]) {
        is DeletionItem.Header -> TYPE_HEADER
        is DeletionItem.Item -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_artwork_header, parent, false)
            )

            TYPE_ITEM -> ItemViewHolder(inflater.inflate(R.layout.fragment_artwork, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is DeletionItem.Header -> (holder as HeaderViewHolder).bind(item)
            is DeletionItem.Item -> (holder as ItemViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = displayItems.size

    fun removeItems(artworkItemsToDelete: List<ArtworkItem>) {
        artworkItems.removeAll(artworkItemsToDelete.toSet())
        refresh()
    }

    fun updateData(newItems: MutableList<ArtworkItem>) {
        artworkItems.clear()
        artworkItems.addAll(newItems)
        refresh()
    }

    /** Drops the current selection without touching the artwork themselves. */
    fun clearSelection() {
        artworkItems.forEach { it.selected = false }
        ArtworksFragment.SELECTED_ITEMS.clear()
        refresh()
    }

    /** Selects or clears every artwork of one day at once. */
    private fun setGroupSelected(items: List<ArtworkItem>, selected: Boolean) {
        items.forEach { item ->
            if (item.selected != selected) {
                item.selected = selected
                if (selected) {
                    ArtworksFragment.SELECTED_ITEMS.add(item)
                } else {
                    ArtworksFragment.SELECTED_ITEMS.remove(item)
                }
            }
        }
        refresh()
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.header_title)
        private val checkBox: CheckBox = view.findViewById(R.id.header_checkbox)

        fun bind(header: DeletionItem.Header) {
            title.text = header.date
            checkBox.contentDescription = header.date
            checkBox.isChecked = header.allSelected

            checkBox.setOnClickListener { setGroupSelected(header.items, checkBox.isChecked) }
            itemView.setOnClickListener { setGroupSelected(header.items, !header.allSelected) }
        }
    }

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        private val imageView: ImageView = view.findViewById(R.id.image)

        fun bind(item: DeletionItem.Item) {
            Glide.with(itemView)
                .load(item.artworkItem.persistent_uri)
                .centerCrop()
                .into(imageView)

            if (item.selected) {
                // Subtle white overlay for selection
                imageView.setColorFilter(Color.argb(80, 255, 255, 255))
            } else {
                imageView.clearColorFilter()
            }
        }

        override fun onClick(view: View) {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            val artworkItem = (displayItems[position] as? DeletionItem.Item)?.artworkItem ?: return

            artworkItem.selected = !artworkItem.selected
            if (artworkItem.selected) {
                ArtworksFragment.SELECTED_ITEMS.add(artworkItem)
            } else {
                ArtworksFragment.SELECTED_ITEMS.remove(artworkItem)
            }
            refresh()
        }

        init {
            itemView.setOnClickListener(this)
        }
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
        private const val DATE_PATTERN = "yyyy-MM-dd"
    }
}
