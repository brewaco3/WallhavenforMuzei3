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

package com.brewaco3.muzei.wallhaven.provider

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.RemoteActionCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.IconCompat
import com.brewaco3.muzei.wallhaven.BuildConfig
import com.brewaco3.muzei.wallhaven.R
import com.brewaco3.muzei.wallhaven.WallhavenProviderConst.WALLHAVEN_ARTWORK_URL
import com.brewaco3.muzei.wallhaven.util.IntentUtils
import com.google.android.apps.muzei.api.provider.Artwork
import java.io.File

/**
 * Builds the actions Muzei shows for the current artwork.
 */
class MuzeiCommandManager {

    fun provideActions(context: Context, artwork: Artwork): List<RemoteActionCompat> =
        listOfNotNull(
            obtainActionShareImage(context, artwork),
            obtainActionViewArtworkDetails(context, artwork),
            obtainActionDeleteArtwork(context, artwork)
        )

    /**
     * Shares the cached copy of the artwork. Artworks saved to user storage by the worker are
     * MediaStore uris, the rest are files in the app's own cache directory, so they have to be
     * handed out through the app's FileProvider.
     */
    @SuppressLint("InlinedApi")
    private fun obtainActionShareImage(context: Context, artwork: Artwork): RemoteActionCompat? {
        val persistentUri = artwork.persistentUri ?: return null
        val shareUri = if (persistentUri.scheme == ContentResolver.SCHEME_FILE) {
            val path = persistentUri.path ?: return null
            try {
                FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    File(path)
                )
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Cannot share $persistentUri", e)
                return null
            }
        } else {
            persistentUri
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            // The receiving app has no permission of its own, and a chooser will not forward the
            // stream extra to the picked activity unless it is also offered as ClipData
            clipData = ClipData.newUri(context.contentResolver, "artwork", shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            artwork.id.toInt(),
            IntentUtils.chooseIntent(
                shareIntent,
                context.getString(R.string.command_shareImage),
                context
            ).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = context.getString(R.string.command_shareImage)
        return RemoteActionCompat(
            IconCompat.createWithResource(context, R.drawable.ic_baseline_share_24),
            title,
            title,
            pendingIntent
        )
    }

    @SuppressLint("InlinedApi")
    private fun obtainActionViewArtworkDetails(
        context: Context,
        artwork: Artwork
    ): RemoteActionCompat? =
        artwork.token
            ?.takeIf { it.isNotEmpty() }
            ?.let { token -> Intent(Intent.ACTION_VIEW, Uri.parse(WALLHAVEN_ARTWORK_URL + token)) }
            ?.let { intent ->
                PendingIntent.getActivity(
                    context,
                    artwork.id.toInt(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            ?.let { pendingIntent ->
                val title = context.getString(R.string.command_viewArtworkDetails)
                RemoteActionCompat(
                    IconCompat.createWithResource(
                        context,
                        com.google.android.apps.muzei.api.R.drawable.muzei_launch_command
                    ),
                    title,
                    title,
                    pendingIntent
                ).apply { setShouldShowIcon(false) }
            }

    @SuppressLint("InlinedApi")
    private fun obtainActionDeleteArtwork(context: Context, artwork: Artwork): RemoteActionCompat =
        DeleteArtworkReceiver.intentFor(context, artwork.token.orEmpty()).let { intent ->
            PendingIntent.getBroadcast(
                context,
                artwork.id.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }.let { pendingIntent ->
            val title = context.getString(R.string.command_delete_artwork)
            RemoteActionCompat(
                IconCompat.createWithResource(context, R.drawable.ic_delete_white_24dp),
                title,
                title,
                pendingIntent
            ).apply { setShouldShowIcon(false) }
        }

    companion object {
        private const val TAG = "MuzeiCommandManager"
    }
}
