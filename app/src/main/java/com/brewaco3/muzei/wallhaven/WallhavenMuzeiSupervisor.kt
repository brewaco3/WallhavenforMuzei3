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

package com.brewaco3.muzei.wallhaven

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.brewaco3.muzei.wallhaven.util.Predicates

/**
 * Created by alvince on 2020/6/16
 *
 * @author alvince.zy@gmail.com
 */
object WallhavenMuzeiSupervisor {

    const val INTENT_CAT_LOCAL = BuildConfig.APPLICATION_ID + ".intent.category.LOCAL_BROADCAST"

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private lateinit var appContext: Context
    private lateinit var appInstrumentation: WallhavenInstrumentation

    private var start: Boolean = false

    private fun ensureInitialized(context: Context? = null): Boolean {
        if (start) {
            return true
        }
        val resolvedContext = (context ?: WallhavenMuzei.context)?.applicationContext
        if (resolvedContext != null) {
            appContext = resolvedContext
            appInstrumentation = WallhavenInstrumentation()
            start = true
        }
        return start
    }

    @MainThread
    fun start(context: Context) {
        Predicates.requireMainThread()

        ensureInitialized(context)
    }

    fun storeSession(context: Context, cookieHeader: String, username: String?) {
        if (!ensureInitialized(context)) {
            return
        }
        appInstrumentation.storeSession(appContext, cookieHeader, username)
    }

    fun clearSession() {
        if (!ensureInitialized()) {
            return
        }
        WallhavenInstrumentation.clearSession(appContext)
    }

    fun setApiKey(apiKey: String) {
        if (!ensureInitialized()) {
            return
        }
        appInstrumentation.storeApiKey(appContext, apiKey)
    }

    fun clearApiKey() {
        if (!ensureInitialized()) {
            return
        }
        appInstrumentation.clearApiKey(appContext)
    }

    fun getApiKey(): String {
        if (!ensureInitialized()) {
            return System.getenv("WALLHAVEN_API_KEY").orEmpty().trim()
        }
        val storedKey = appInstrumentation.getApiKey(appContext)
        if (storedKey.isNotEmpty()) {
            return storedKey
        }
        return System.getenv("WALLHAVEN_API_KEY").orEmpty().trim()
    }

    fun hasApiKey(): Boolean {
        return getApiKey().isNotEmpty()
    }

    fun getSessionCookie(): String {
        if (!ensureInitialized()) {
            return ""
        }
        return appInstrumentation.getSessionCookie(appContext)
    }

    fun hasSession(): Boolean {
        return ensureInitialized() && appInstrumentation.hasSession(appContext)
    }

    fun getAccessToken(): String = getApiKey()

    fun broadcastLocal(intent: Intent) {
        require(intent.action?.isNotEmpty() == true)

        LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent)
    }

    @JvmStatic
    fun post(action: Runnable) {
        mainHandler.post(action)
    }

    fun post(block: () -> Unit) {
        mainHandler.post(block)
    }

}
