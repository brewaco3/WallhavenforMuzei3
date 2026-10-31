package com.brewaco3.muzei.wallhaven.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brewaco3.muzei.wallhaven.AppDatabase
import com.brewaco3.muzei.wallhaven.settings.artworks.DeletedArtworkIdEntity
import com.google.android.apps.muzei.api.provider.ProviderContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Removes one artwork from the rotation and remembers the id, so the worker never downloads it again.
 * Every "delete this artwork" entry point goes through here.
 */
class DeleteArtworkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val artworkId = intent.getStringExtra(EXTRA_ARTWORK_ID) ?: return

        context.contentResolver.delete(
            ProviderContract.getProviderClient(context, WallhavenArtProvider::class.java).contentUri,
            "${ProviderContract.Artwork.TOKEN} = ?",
            arrayOf(artworkId)
        )

        // The process can be torn down as soon as onReceive returns, so keep it alive until the id is stored
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getInstance(context).deletedArtworkIdDao()
                    .insertDeletedArtworkId(listOf(DeletedArtworkIdEntity(artworkId)))
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_DELETE_ARTWORK = "com.brewaco3.muzei.wallhaven.action.DELETE_ARTWORK"
        private const val EXTRA_ARTWORK_ID = "artworkId"

        fun dispatch(context: Context, artworkId: String) {
            context.sendBroadcast(intentFor(context, artworkId))
        }

        fun intentFor(context: Context, artworkId: String): Intent =
            Intent(context, DeleteArtworkReceiver::class.java).apply {
                action = ACTION_DELETE_ARTWORK
                putExtra(EXTRA_ARTWORK_ID, artworkId)
            }
    }
}
