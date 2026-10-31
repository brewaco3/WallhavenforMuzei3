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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.app.RemoteActionCompat
import androidx.preference.PreferenceManager
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class WallhavenArtProvider : MuzeiArtProvider() {

    companion object {
        const val TAG = "WallhavenArtProvider"
    }

    private val commandManager by lazy { MuzeiCommandManager() }

    private var running = false

    override fun onCreate(): Boolean {
        super.onCreate()
        running = true
        return true
    }

    override fun onLoadRequested(initial: Boolean) {
        val providerContext = context ?: return
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(providerContext)
        if (sharedPrefs.getBoolean("pref_dataMode", false) && isOnMobileData()) {
            Log.i(TAG, "Data saver mode, skipping new artwork download")
            return
        }
        WallhavenArtWorker.enqueueLoad(false, providerContext)
    }

    override fun getCommandActions(artwork: Artwork): List<RemoteActionCompat> {
        if (!running) {
            return emptyList()
        }
        return commandManager.provideActions(checkContext(), artwork)
    }

    @Throws(IOException::class)
    override fun openFile(artwork: Artwork): InputStream {
        val currentContext = try {
            checkContext()
        } catch (ex: IllegalStateException) {
            throw IOException("Provider not prepared.", ex)
        }

        val artworkPersistentUri = artwork.persistentUri
            ?: throw IOException("Require non-null persistent uri in Artwork $artwork")

        val inputStream = try {
            currentContext.contentResolver.openInputStream(artworkPersistentUri)
        } catch (ex: FileNotFoundException) {
            Log.d(TAG, "Fail to open stream: $artworkPersistentUri", ex)
            throw IOException("Fail to open stream: $artworkPersistentUri", ex)
        }
        return requireNotNull(inputStream)
    }

    /**
     * Whether the active network is the mobile one. Returns false when there is no active network at
     * all, which is also what happens in airplane mode.
     */
    private fun isOnMobileData(): Boolean {
        val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    }

    /**
     * Return the [Context] this provider is running in.
     *
     * @throws IllegalStateException if not currently running after [onCreate].
     */
    @Throws(IllegalStateException::class)
    private fun checkContext(): Context {
        val currentContext = context
        check(running && currentContext != null) { "Provider $this not in running." }
        return currentContext
    }

    override fun getArtworkInfo(artwork: Artwork): PendingIntent? {
        val webUri = artwork.webUri ?: return null
        val providerContext = context ?: return null
        return PendingIntent.getActivity(
            providerContext,
            0,
            Intent(Intent.ACTION_VIEW, webUri),
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}
