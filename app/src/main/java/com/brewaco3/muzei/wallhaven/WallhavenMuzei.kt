package com.brewaco3.muzei.wallhaven

import android.app.Application
import android.content.Context

class WallhavenMuzei : Application() {
    override fun onCreate() {
        super.onCreate()
        context = this
    }

    companion object {
        var context: Context? = null
    }
}
