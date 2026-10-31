package com.brewaco3.muzei.wallhaven

import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Single place the rest of the app goes to for the Wallhaven API key.
 *
 * The key is only ever stored in shared preferences: it is not a secret we can protect beyond that,
 * but every request that uses it goes to wallhaven.cc over TLS.
 */
object WallhavenPrefs {

    private val prefs: SharedPreferences
        get() {
            val context = checkNotNull(WallhavenMuzei.context) {
                "WallhavenMuzei.onCreate() has not run yet"
            }
            return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        }

    fun getApiKey(): String = prefs.getString(WallhavenProviderConst.PREFERENCE_API_KEY, "").orEmpty()

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    fun setApiKey(apiKey: String?) {
        val sanitized = apiKey?.trim().orEmpty()
        prefs.edit().apply {
            if (sanitized.isEmpty()) {
                remove(WallhavenProviderConst.PREFERENCE_API_KEY)
            } else {
                putString(WallhavenProviderConst.PREFERENCE_API_KEY, sanitized)
            }
        }.apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(WallhavenProviderConst.PREFERENCE_API_KEY).apply()
    }
}
