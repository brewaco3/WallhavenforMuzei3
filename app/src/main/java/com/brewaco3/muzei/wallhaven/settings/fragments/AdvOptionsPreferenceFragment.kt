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

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.DropDownPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.brewaco3.muzei.wallhaven.R
import com.brewaco3.muzei.wallhaven.WallhavenPrefs
import com.brewaco3.muzei.wallhaven.WallhavenProviderConst
import com.brewaco3.muzei.wallhaven.provider.ArtworkFilter
import com.brewaco3.muzei.wallhaven.provider.ClearCacheWorker
import com.brewaco3.muzei.wallhaven.settings.applyNavigationBarPadding
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AdvOptionsPreferenceFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    interface NightModePreferenceListener {
        fun nightModeOptionSelected(option: Int)
    }

    private lateinit var nightModePreferenceListener: NightModePreferenceListener

    override fun onAttach(context: Context) {
        super.onAttach(context)
        nightModePreferenceListener = context as NightModePreferenceListener
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<RecyclerView>(android.R.id.list)?.applyNavigationBarPadding()
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
        updateDebugUrl()
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        updateDebugUrl()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.adv_setting_preference_layout, rootKey)
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Minimum width/height sliders. Both are stored in units of ArtworkFilter.PIXEL_STEP pixels
        // so a single slider can cover 0 - 4090px
        listOf("prefSlider_minimumWidth", "prefSlider_minimumHeight").forEach { key ->
            findPreference<SeekBarPreference>(key)?.let { slider ->
                slider.updatesContinuously = true
                val summaries = { value: Int ->
                    if (value == 0) {
                        getString(R.string.prefSummary_noMinimum)
                    } else {
                        getString(R.string.prefSummary_pixels, value * ArtworkFilter.PIXEL_STEP)
                    }
                }
                slider.summary = summaries(sharedPrefs.getInt(key, 0))
                slider.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, newValue: Any ->
                        slider.summary = summaries(newValue as Int)
                        true
                    }
            }
        }

        // Slider that lets the user adjust how many artworks to download at a time
        findPreference<SeekBarPreference>("prefSlider_numToDownload")?.let { slider ->
            slider.updatesContinuously = true
            slider.summary = sharedPrefs.getInt("prefSlider_numToDownload", 3).toString()
            slider.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue: Any? ->
                    slider.summary = (newValue as Int).toString()
                    true
                }
        }

        // Switch that controls if the cache periodically clears itself
        findPreference<SwitchPreference>("pref_autoClearMode")?.let { pref ->
            pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    enqueueDailyCacheClear()
                } else {
                    WorkManager.getInstance(requireContext()).cancelAllWorkByTag(CACHE_CLEAR_TAG)
                    // Clears scheduled work left over from builds that used the old tag
                    WorkManager.getInstance(requireContext()).cancelAllWorkByTag(LEGACY_CACHE_CLEAR_TAG)
                }
                true
            }
        }

        // If more than one storage is mounted, offer a choice of where to save pictures.
        // Only relevant when the user has asked for artwork to be saved to user storage
        val selectWhichExternalStoragePref =
            findPreference<DropDownPreference>("pref_selectWhichExtStorage")
        selectWhichExternalStoragePref?.isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                isMoreThanOneStorage() &&
                sharedPrefs.getBoolean("pref_storeInExtStorage", false)

        val externalStoragePref = findPreference<SwitchPreference>("pref_storeInExtStorage")
        externalStoragePref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                // Reveals the storage picker as the store-into-external-storage option is enabled
                if (isMoreThanOneStorage()) {
                    selectWhichExternalStoragePref?.isVisible = newValue as Boolean
                }
                true
            }
        // Below Android 10 the app writes through the legacy file API, which needs the permission
        externalStoragePref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_STORAGE
                )
            }
            true
        }

        findPreference<MultiSelectListPreference>("pref_aspectRatioSelect_v2")?.let { pref ->
            pref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference, newValue ->
                    @Suppress("UNCHECKED_CAST")
                    val selected = (newValue as Set<String>).toMutableSet()
                    val previous = (preference as MultiSelectListPreference).values
                    val added = selected - previous

                    when {
                        // "Any" wins on its own, so drop everything else
                        added.contains("") -> {
                            selected.clear()
                            selected.add("")
                        }
                        // Picking anything else means the user stopped wanting "Any"
                        selected.contains("") -> selected.remove("")

                        // Wallhaven cannot combine a keyword like "all wide" with an exact ratio in
                        // one request: it drops the keyword and keeps the ratio. Rather than let the
                        // choice be ignored later, the family the user just picked wins here, where
                        // the change is visible.
                        added.any { it in ArtworkFilter.RATIO_KEYWORDS } -> selected.removeAll {
                            it.isNotEmpty() && it !in ArtworkFilter.RATIO_KEYWORDS
                        }

                        added.any { it.isNotEmpty() && it !in ArtworkFilter.RATIO_KEYWORDS } ->
                            selected.removeAll(ArtworkFilter.RATIO_KEYWORDS)
                    }

                    // An empty selection would mean "Any" to the API as well, so make it explicit
                    if (selected.isEmpty()) selected.add("")

                    pref.values = selected
                    false // The value was handled here
                }
        }

        // This preference immediately sets the night mode option.
        // Without it, the app needs to restart to apply the new setting.
        // Needs to be implemented as an interface as the setting can only be applied within an Activity
        findPreference<ListPreference>("pref_nightMode")?.let { pref ->
            pref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue: Any ->
                    nightModePreferenceListener.nightModeOptionSelected((newValue as String).toInt())
                    true
                }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "pref_debugUrl") {
            val clipboard = requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText(getString(R.string.prefTitle_debugUrl), preference.summary)
            )
            Toast.makeText(
                requireContext(), R.string.toast_debugUrlCopied, Toast.LENGTH_SHORT
            ).show()
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun enqueueDailyCacheClear() {
        val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        // The preference promises 1AM, so delay to the next 01:00 rather than to midnight
        val hoursUntil1Am = ((1 - nowHour + 24) % 24).takeIf { it != 0 } ?: 24

        val request = PeriodicWorkRequest.Builder(ClearCacheWorker::class.java, 24, TimeUnit.HOURS)
            .setInitialDelay(hoursUntil1Am.toLong(), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .addTag(CACHE_CLEAR_TAG)
            .build()

        WorkManager.getInstance(requireContext())
            .enqueueUniquePeriodicWork(
                CACHE_CLEAR_TAG,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request
            )
    }

    /** Shows the exact query the worker would send, so a misconfigured filter is diagnosable. */
    private fun updateDebugUrl() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val hasApiKey = WallhavenPrefs.hasApiKey()
        val sorting = ArtworkFilter.sortingQuery(sharedPrefs.getString("pref_updateMode", null))

        val params = mutableListOf<String>()
        params.add("sorting=$sorting")

        sharedPrefs.getString("pref_tagFilter", null)?.ifBlank { null }
            ?.let { params.add("q=${Uri.encode(it)}") }

        params.add(
            "categories=" + ArtworkFilter.categoryQuery(
                sharedPrefs.getStringSet("pref_rankingCategorySelect", setOf("general", "anime", "people"))
                    ?: setOf("general", "anime", "people")
            )
        )
        params.add(
            "purity=" + ArtworkFilter.purityQuery(
                sharedPrefs.getStringSet("pref_rankingFilterSelect", setOf("sfw")) ?: setOf("sfw"),
                hasApiKey
            )
        )

        // Each of these is built by the same helper the worker uses, so an invalid value is shown
        // as absent here and is absent from the real request too
        ArtworkFilter.atleastQuery(sharedPrefs.getString("pref_atleast", null))
            .takeIf { it.isNotEmpty() }?.let { params.add("atleast=${Uri.encode(it)}") }

        ArtworkFilter.ratiosQuery(
            sharedPrefs.getStringSet("pref_aspectRatioSelect_v2", emptySet()) ?: emptySet()
        ).takeIf { it.isNotEmpty() }?.let { params.add("ratios=${Uri.encode(it)}") }

        if (sorting == ArtworkFilter.SORTING_TOPLIST) {
            params.add("topRange=${sharedPrefs.getString("pref_topRange", "1M")}")
        }

        params.add("order=${sharedPrefs.getString("pref_order", "desc")}")

        ArtworkFilter.seedQuery(sharedPrefs.getString("pref_seed", null), sorting)
            .takeIf { it.isNotEmpty() }?.let { params.add("seed=${Uri.encode(it)}") }
        ArtworkFilter.colorsQuery(sharedPrefs.getString("pref_colors", null))
            .takeIf { it.isNotEmpty() }?.let { params.add("colors=$it") }
        if (hasApiKey) {
            params.add("apikey=********")
        }

        val url = WallhavenProviderConst.WALLHAVEN_API_BASE_URL + "search?" + params.joinToString("&")
        findPreference<Preference>("pref_debugUrl")?.summary = url
    }

    private fun isMoreThanOneStorage(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(requireContext()).size > 1
        } else {
            // Android P or lower
            requireContext().getExternalFilesDirs(null).size > 1
        }

    companion object {
        private const val CACHE_CLEAR_TAG = "wallhaven_cache_auto"
        private const val LEGACY_CACHE_CLEAR_TAG = "PIXIV_CACHE_AUTO"
        private const val REQUEST_WRITE_STORAGE = 1
    }
}
