package com.brewaco3.muzei.wallhaven.settings

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView

/**
 * The app draws edge to edge, so scrolling content has to make room for the navigation bar itself.
 * Padding rather than a margin keeps the last row scrollable clear of the bar instead of permanently
 * out of reach.
 */
fun RecyclerView.applyNavigationBarPadding() {
    clipToPadding = false
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(bottom = insets.bottom)
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
