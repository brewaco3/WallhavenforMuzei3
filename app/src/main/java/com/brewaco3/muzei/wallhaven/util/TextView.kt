package com.brewaco3.muzei.wallhaven.util

import android.widget.TextView

fun TextView.text(trim: Boolean = false): CharSequence =
    this.text?.toString()
        ?.let {
            if (trim) it.trim() else it
        }
        ?: ""
