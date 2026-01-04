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
package com.brewaco3.muzei.wallhaven.settings.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.brewaco3.muzei.wallhaven.BuildConfig
import com.brewaco3.muzei.wallhaven.R
import com.brewaco3.muzei.wallhaven.provider.network.github.GithubClient
import com.brewaco3.muzei.wallhaven.provider.network.github.GithubRelease
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AboutPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.about_preference_layout, rootKey)

        // Display current version
        findPreference<Preference>("pref_version")?.summary = BuildConfig.VERSION_NAME

        // Source code link
        findPreference<Preference>("pref_source_code")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_source_code)))
            startActivity(intent)
            true
        }

        // Check for update
        findPreference<Preference>("pref_check_update")?.setOnPreferenceClickListener {
            checkForUpdate()
            true
        }
    }

    private fun checkForUpdate() {
        val view = view ?: return
        Snackbar.make(view, R.string.toast_checking_update, Snackbar.LENGTH_SHORT).show()

        GithubClient.service.getLatestRelease("brewaco3", "WallhavenforMuzei3")
            .enqueue(object : Callback<GithubRelease> {
                override fun onResponse(call: Call<GithubRelease>, response: Response<GithubRelease>) {
                    if (!isAdded) return

                    val currentView = view ?: return
                    if (response.isSuccessful && response.body() != null) {
                        val release = response.body()!!
                        val remoteVersion = release.tagName.removePrefix("v")
                        val localVersion = BuildConfig.VERSION_NAME

                        if (isNewerVersion(remoteVersion, localVersion)) {
                            showUpdateDialog(release)
                        } else {
                            Snackbar.make(currentView, R.string.toast_no_update, Snackbar.LENGTH_SHORT).show()
                        }
                    } else {
                        Snackbar.make(currentView, R.string.toast_update_check_failed, Snackbar.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<GithubRelease>, t: Throwable) {
                    if (!isAdded) return
                    val currentView = view ?: return
                    Snackbar.make(currentView, R.string.toast_update_check_failed, Snackbar.LENGTH_SHORT).show()
                }
            })
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }
        val length = maxOf(remoteParts.size, localParts.size)

        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun showUpdateDialog(release: GithubRelease) {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle(getString(R.string.dialog_new_version_title, release.tagName))
            .setMessage(release.body ?: "")
            .setPositiveButton(R.string.dialog_download) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                startActivity(intent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
