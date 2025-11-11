package com.brewaco3.muzei.wallhaven

import android.content.Context
import androidx.preference.PreferenceManager
import com.brewaco3.muzei.wallhaven.BuildConfig
import com.brewaco3.muzei.wallhaven.WallhavenProviderConst.PREFERENCE_API_KEY
import com.brewaco3.muzei.wallhaven.WallhavenProviderConst.PREFERENCE_SESSION_COOKIE
import com.brewaco3.muzei.wallhaven.WallhavenProviderConst.PREFERENCE_SESSION_TIMESTAMP
import com.brewaco3.muzei.wallhaven.WallhavenProviderConst.PREFERENCE_SESSION_USERNAME

class WallhavenInstrumentation {

    companion object {
        const val INTENT_ACTION_ACCESS_TOKEN_MISSING =
            BuildConfig.APPLICATION_ID + ".intent.action.SESSION_MISSING"

        fun clearSession(context: Context) {
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
                .edit()
                .remove(PREFERENCE_SESSION_COOKIE)
                .remove(PREFERENCE_SESSION_TIMESTAMP)
                .remove(PREFERENCE_SESSION_USERNAME)
                .remove(PREFERENCE_API_KEY)
                .apply()
        }
    }

    fun storeSession(context: Context, cookieHeader: String, username: String?) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .apply {
                putString(PREFERENCE_SESSION_COOKIE, cookieHeader)
                putLong(PREFERENCE_SESSION_TIMESTAMP, System.currentTimeMillis())
                if (!username.isNullOrBlank()) {
                    putString(PREFERENCE_SESSION_USERNAME, username)
                }
            }
            .apply()
    }

    fun getSessionCookie(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getString(PREFERENCE_SESSION_COOKIE, "")
            .orEmpty()

    fun getStoredUsername(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getString(PREFERENCE_SESSION_USERNAME, "")
            .orEmpty()

    fun hasSession(context: Context): Boolean =
        getSessionCookie(context).isNotEmpty()

    fun storeApiKey(context: Context, apiKey: String?) {
        val sanitized = apiKey?.trim().orEmpty()
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .apply {
                if (sanitized.isEmpty()) {
                    remove(PREFERENCE_API_KEY)
                } else {
                    putString(PREFERENCE_API_KEY, sanitized)
                }
            }
            .apply()
    }

    fun clearApiKey(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .remove(PREFERENCE_API_KEY)
            .apply()
    }

    fun getApiKey(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getString(PREFERENCE_API_KEY, "")
            .orEmpty()

    fun hasApiKey(context: Context): Boolean =
        getApiKey(context).isNotEmpty()
}
