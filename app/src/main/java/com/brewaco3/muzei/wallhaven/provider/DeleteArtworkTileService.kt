package com.brewaco3.muzei.wallhaven.provider

import android.service.quicksettings.TileService
import com.google.android.apps.muzei.api.MuzeiContract

/**
 * Quick settings tile that deletes the wallpaper currently on screen, exactly like the
 * "Delete this artwork" command does.
 */
class DeleteArtworkTileService : TileService() {
    override fun onClick() {
        super.onClick()
        // Muzei only exposes the displayed artwork's title, which this provider sets to the Wallhaven id
        val token = MuzeiContract.Artwork.getCurrentArtwork(applicationContext)?.title ?: return
        DeleteArtworkReceiver.dispatch(applicationContext, token)
    }
}
