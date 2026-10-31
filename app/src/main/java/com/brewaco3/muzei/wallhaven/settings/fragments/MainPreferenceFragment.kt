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

import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.brewaco3.muzei.wallhaven.R
import com.brewaco3.muzei.wallhaven.WallhavenPrefs
import com.brewaco3.muzei.wallhaven.WallhavenProviderConst
import com.brewaco3.muzei.wallhaven.provider.WallhavenArtProvider
import com.brewaco3.muzei.wallhaven.provider.WallhavenArtWorker
import com.brewaco3.muzei.wallhaven.provider.WallhavenArtWorker.Companion.enqueueLoad
import com.brewaco3.muzei.wallhaven.settings.applyNavigationBarPadding
import com.google.android.apps.muzei.api.provider.ProviderContract.getProviderClient
import com.google.android.material.snackbar.Snackbar

class MainPreferenceFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<RecyclerView>(android.R.id.list)?.applyNavigationBarPadding()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preference_layout, rootKey)

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // SimpleSummaryProvider does not support MultiSelectListPreference, so both NSFW filters
        // generate their own summary and refuse to leave the user with nothing selected
        findPreference<MultiSelectListPreference>("pref_rankingFilterSelect")?.let { pref ->
            pref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue: Any ->
                    @Suppress("UNCHECKED_CAST")
                    val selection = newValue as Set<String>
                    if (selection.isEmpty()) {
                        val fallback = setOf("sfw")
                        pref.values = fallback
                        sharedPrefs.edit().putStringSet("pref_rankingFilterSelect", fallback).apply()
                        pref.summary = rankingSummaryStringGenerator(fallback)
                        return@OnPreferenceChangeListener false
                    }
                    pref.summary = rankingSummaryStringGenerator(selection)
                    true
                }
            pref.summary = rankingSummaryStringGenerator(
                sharedPrefs.getStringSet("pref_rankingFilterSelect", setOf("sfw")) ?: setOf("sfw")
            )
        }

        findPreference<MultiSelectListPreference>("pref_rankingCategorySelect")?.let { pref ->
            pref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue: Any ->
                    @Suppress("UNCHECKED_CAST")
                    val selection = newValue as Set<String>
                    if (selection.isEmpty()) {
                        val fallback = setOf("general", "anime", "people")
                        pref.values = fallback
                        sharedPrefs.edit().putStringSet("pref_rankingCategorySelect", fallback).apply()
                        pref.summary = categorySummaryStringGenerator(fallback)
                        return@OnPreferenceChangeListener false
                    }
                    pref.summary = categorySummaryStringGenerator(selection)
                    true
                }
            val defaultSelection = setOf("general", "anime", "people")
            pref.summary = categorySummaryStringGenerator(
                sharedPrefs.getStringSet("pref_rankingCategorySelect", defaultSelection) ?: defaultSelection
            )
        }

        // The API key lives in WallhavenPrefs rather than in the preference itself, so the
        // preference is not persistent and only ever acts as a front end for it
        findPreference<EditTextPreference>("pref_api_key")?.let { pref ->
            pref.summary = apiKeySummary()
            pref.text = WallhavenPrefs.getApiKey()
            pref.setOnBindEditTextListener { editText ->
                editText.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            pref.setOnPreferenceChangeListener { preference, newValue ->
                WallhavenPrefs.setApiKey(newValue as? String)
                (preference as EditTextPreference).summary = apiKeySummary()
                true
            }
        }

        // Preference that immediately clears Muzei's image cache when pressed
        findPreference<Preference>("pref_clearCache")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dialog_clearCache_title)
                    .setMessage(R.string.dialog_clearCache_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        // Cancel any ongoing work
                        WorkManager.getInstance(requireContext())
                            .cancelUniqueWork(WallhavenArtWorker.WORKER_TAG)
                        // Delete cached image files
                        requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                            ?.deleteRecursively()
                        // Clear the Muzei provider database
                        getProviderClient(requireContext(), WallhavenArtProvider::class.java)
                            .setArtwork(emptyList())
                        // Download fresh wallpapers
                        enqueueLoad(true, requireContext())
                        Snackbar.make(
                            requireView(), R.string.toast_clearingCache,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }

        findPreference<Preference>("pref_forceRefresh")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                // REPLACE so a queued load does not hold up the one the user just asked for
                val workId = enqueueLoad(true, requireContext(), ExistingWorkPolicy.REPLACE)
                if (workId == null) {
                    Snackbar.make(
                        requireView(),
                        R.string.toast_forceRefreshFailed_generic,
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@OnPreferenceClickListener true
                }

                Snackbar.make(
                    requireView(),
                    R.string.toast_forceRefreshQueued,
                    Snackbar.LENGTH_SHORT
                ).show()

                val workManager = WorkManager.getInstance(requireContext())
                val liveData = workManager.getWorkInfoByIdLiveData(workId)
                liveData.observe(viewLifecycleOwner) { info ->
                    if (info == null) {
                        return@observe
                    }
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> Snackbar.make(
                            requireView(),
                            R.string.toast_forceRefreshComplete,
                            Snackbar.LENGTH_SHORT
                        ).show()

                        WorkInfo.State.FAILED -> {
                            val errorMessage = info.outputData
                                .getString(WallhavenProviderConst.WORK_ERROR_MESSAGE_KEY)
                                .orEmpty()
                            val message = if (errorMessage.isBlank()) {
                                getString(R.string.toast_forceRefreshFailed_generic)
                            } else {
                                getString(R.string.toast_forceRefreshFailed, errorMessage)
                            }
                            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
                        }

                        WorkInfo.State.CANCELLED -> Snackbar.make(
                            requireView(),
                            R.string.toast_forceRefreshCancelled,
                            Snackbar.LENGTH_LONG
                        ).show()

                        else -> Unit
                    }

                    if (info.state.isFinished) {
                        liveData.removeObservers(viewLifecycleOwner)
                    }
                }
                true
            }
    }

    private fun apiKeySummary(): String = getString(
        if (WallhavenPrefs.hasApiKey()) {
            R.string.prefSummary_apiKey_set
        } else {
            R.string.prefSummary_apiKey_notSet
        }
    )

    /** Builds a comma separated summary in the order the entries are declared, never with a trailing comma. */
    private fun rankingSummaryStringGenerator(selection: Set<String>): String =
        summaryStringGenerator(
            selection,
            R.array.pref_rankingFilterLevel_entries,
            R.array.pref_rankingFilterLevel_entryvalues,
            DEFAULT_PURITY
        )

    private fun categorySummaryStringGenerator(selection: Set<String>): String =
        summaryStringGenerator(
            selection,
            R.array.pref_rankingCategory_entries,
            R.array.pref_rankingCategory_entryvalues,
            DEFAULT_CATEGORIES
        )

    /** Describes [selection] in the order the entries are declared, and never leaves the summary blank. */
    private fun summaryStringGenerator(
        selection: Set<String>,
        entriesRes: Int,
        valuesRes: Int,
        fallback: Set<String>
    ): String {
        val entries = resources.getStringArray(entriesRes)
        val values = resources.getStringArray(valuesRes)
        val valueToEntry = values.indices.associate { values[it] to entries[it] }
        val effective = selection.ifEmpty { fallback }
        // Ordered by the declaration order of the values rather than the order of the set
        val ordered = values.filter { effective.contains(it) }.mapNotNull { valueToEntry[it] }
        return ordered.joinToString(", ")
    }

    companion object {
        private val DEFAULT_PURITY = setOf("sfw")
        private val DEFAULT_CATEGORIES = setOf("general", "anime", "people")
    }
}
