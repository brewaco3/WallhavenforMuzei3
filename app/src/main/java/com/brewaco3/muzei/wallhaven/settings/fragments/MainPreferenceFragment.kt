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
package com.brewaco3.muzei.wallhaven.settings.fragments

import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.widget.Toast
import androidx.preference.*
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.brewaco3.muzei.wallhaven.PixivMuzeiSupervisor
import com.brewaco3.muzei.wallhaven.PixivProviderConst
import com.brewaco3.muzei.wallhaven.PixivProviderConst.AUTH_MODES
import com.brewaco3.muzei.wallhaven.R
import com.brewaco3.muzei.wallhaven.provider.PixivArtWorker.Companion.enqueueLoad
import com.google.android.material.snackbar.Snackbar
class MainPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var oldUpdateMode: String
    private lateinit var newUpdateMode: String
    private lateinit var oldTag: String
    private lateinit var newTag: String
    private lateinit var oldArtist: String
    private lateinit var newArtist: String
    private lateinit var oldRankingFilters: Set<String>
    private lateinit var oldRankingCategories: Set<String>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preference_layout, rootKey)

        PixivMuzeiSupervisor.start(requireContext().applicationContext)

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Stores user toggleable variables into a temporary store for later comparison in onStop()
        // If the value of the preference on Activity creation is different to Activity stop, then take certain action
        oldUpdateMode = sharedPrefs.getString("pref_updateMode", "toplist") ?: "toplist"
        oldTag = sharedPrefs.getString("pref_tagSearch", "") ?: ""
        oldArtist = sharedPrefs.getString("pref_artistId", "") ?: ""
        oldRankingFilters = sharedPrefs.getStringSet("pref_rankingFilterSelect", setOf("sfw"))
            ?.toSet() ?: setOf("sfw")
        oldRankingCategories = sharedPrefs.getStringSet(
            "pref_rankingCategorySelect",
            setOf("general", "anime", "people")
        )?.toSet() ?: setOf("general", "anime", "people")

        // Ensures that the user has provided an API key before selecting any update mode requiring authentication
        // Reveals UI elements as needed depending on Update Mode selection
        val updateModePref = findPreference<DropDownPreference>("pref_updateMode")
        updateModePref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
                // User has selected an authenticated feed mode, but has not yet provided
                // the API key required for those requests
                if (AUTH_MODES.contains(newValue) && !PixivMuzeiSupervisor.hasApiKey()) {
                    Snackbar.make(
                        requireView(), R.string.toast_loginFirst,
                        Snackbar.LENGTH_SHORT
                    ).show()
                    return@setOnPreferenceChangeListener false
                }
                // If any of the auth feed modes, reveal login Preference Category, reveal the auth NSFW filtering,
                // and hide the ranking NSFW filtering
                val authFeedModeSelected = AUTH_MODES.contains(newValue)
                findPreference<Preference>("pref_authFilterSelect")?.isVisible =
                    authFeedModeSelected
                findPreference<Preference>("pref_rankingFilterSelect")?.isVisible =
                    !authFeedModeSelected
                findPreference<Preference>("pref_rankingCategorySelect")?.isVisible =
                    !authFeedModeSelected
                findPreference<Preference>("pref_tagSearch")?.isVisible = newValue == "tag_search"
                findPreference<Preference>("pref_tagLanguage")?.isVisible = newValue == "tag_search"
                findPreference<Preference>("pref_artistId")?.isVisible = newValue == "artist"
                true
            }

        // All this is needed for the arbitrary selection NSFW filtering
        // Will default to only SFW if no filtering modes are selected
        // Prints a summary string based on selection
        // Updates authFilterSelectPref summary as user updates it
        // SimpleSummaryProvider does not support MultiSelectListPreference
        findPreference<MultiSelectListPreference>("pref_authFilterSelect")?.let {
            it.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
                    // Reset to SFW on empty selection
                    @Suppress("UNCHECKED_CAST")
                    if ((newValue as Set<String>).isEmpty()) {
                        it.values = setOf("2")
                        sharedPrefs.edit().apply {
                            putStringSet("pref_authFilterSelect", setOf("2"))
                            apply()
                        }
                        it.summary = "SFW"
                        return@setOnPreferenceChangeListener false
                    }
                    it.summary =
                        authSummaryStringGenerator(newValue)

                    true
                }
            it.summary = authSummaryStringGenerator(
                sharedPrefs.getStringSet(
                    "pref_authFilterSelect",
                    setOf("2")
                ) as Set<String>
            )
        }

        // Updates ranking SFW filtering preference
        // Same manner as above, the auth modes
        findPreference<MultiSelectListPreference>("pref_rankingFilterSelect")?.let {
            it.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
                    @Suppress("UNCHECKED_CAST")
                    val selection = newValue as Set<String>
                    if (selection.isEmpty()) {
                        it.values = setOf("sfw")
                        sharedPrefs.edit().apply {
                            putStringSet("pref_rankingFilterSelect", setOf("sfw"))
                            apply()
                        }
                        it.summary = "SFW"
                        return@setOnPreferenceChangeListener false
                    }

                    it.summary = rankingSummaryStringGenerator(selection)

                    true
                }
            // Generates the ranking NSFW filter summary during activity startup
            it.summary =
                rankingSummaryStringGenerator(
                    sharedPrefs.getStringSet("pref_rankingFilterSelect", setOf("sfw")) as Set<String>
                )
        }

        findPreference<MultiSelectListPreference>("pref_rankingCategorySelect")?.let { pref ->
            pref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener setOnPreferenceChangeListener@{ _: Preference?, newValue: Any ->
                    @Suppress("UNCHECKED_CAST")
                    val selection = newValue as Set<String>
                    if (selection.isEmpty()) {
                        val defaultSelection = setOf("general", "anime", "people")
                        pref.values = defaultSelection
                        sharedPrefs.edit().apply {
                            putStringSet("pref_rankingCategorySelect", defaultSelection)
                            apply()
                        }
                        pref.summary = categorySummaryStringGenerator(defaultSelection)
                        return@setOnPreferenceChangeListener false
                    }
                    pref.summary = categorySummaryStringGenerator(selection)
                    true
                }
            pref.summary = categorySummaryStringGenerator(
                sharedPrefs.getStringSet(
                    "pref_rankingCategorySelect",
                    setOf("general", "anime", "people")
                ) ?: setOf("general", "anime", "people")
            )
        }

        // Reveal the tag_search or artist_id EditTextPreference and write the summary if update mode matches
        val updateMode = sharedPrefs.getString("pref_updateMode", "toplist")
        if (AUTH_MODES.contains(updateMode)) {
            findPreference<Preference>("pref_authFilterSelect")?.isVisible = true
            findPreference<Preference>("prefCat_loginSettings")?.isVisible = true
            findPreference<Preference>("pref_rankingFilterSelect")?.isVisible = false
            findPreference<Preference>("pref_rankingCategorySelect")?.isVisible = false
            if (updateMode == "tag_search") {
                findPreference<Preference>("pref_tagSearch")?.let {
                    it.isVisible = true
                }
                findPreference<Preference>("pref_tagLanguage")?.let {
                    it.isVisible = true
                }
            } else if (updateMode == "artist") {
                findPreference<Preference>("pref_artistId")?.let {
                    it.isVisible = true
                }
            }
        } else {
            findPreference<Preference>("pref_rankingFilterSelect")?.isVisible = true
            findPreference<Preference>("pref_rankingCategorySelect")?.isVisible = true
        }

        // Preference that immediately clears Muzei's image cache when pressed
        findPreference<Preference>(getString(R.string.button_clearCache))?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                WorkManager.getInstance(requireContext()).cancelUniqueWork("ANTONY")
                requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?.deleteRecursively()
                enqueueLoad(true, requireContext())
                Snackbar.make(
                    requireView(), R.string.toast_clearingCache,
                    Snackbar.LENGTH_SHORT
                )
                    .show()
                newUpdateMode = oldUpdateMode
                true
            }

        findPreference<Preference>("pref_forceRefresh")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
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
                        WorkInfo.State.SUCCEEDED -> {
                            Snackbar.make(
                                requireView(),
                                R.string.toast_forceRefreshComplete,
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }

                        WorkInfo.State.FAILED -> {
                            val errorMessage = info.outputData
                                .getString(PixivProviderConst.WORK_ERROR_MESSAGE_KEY)
                                .orEmpty()
                            val message = if (errorMessage.isBlank()) {
                                getString(R.string.toast_forceRefreshFailed_generic)
                            } else {
                                getString(R.string.toast_forceRefreshFailed, errorMessage)
                            }
                            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
                                .show()
                        }

                        WorkInfo.State.CANCELLED -> {
                            Snackbar.make(
                                requireView(),
                                R.string.toast_forceRefreshCancelled,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }

                        else -> {
                            // Ignore other states
                        }
                    }

                    if (info.state.isFinished) {
                        liveData.removeObservers(viewLifecycleOwner)
                    }
                }
                true
            }

        findPreference<EditTextPreference>("pref_api_key")?.let { pref ->
            pref.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
                if (preference.text.isNullOrBlank()) {
                    getString(R.string.prefSummary_apiKey_notSet)
                } else {
                    getString(R.string.prefSummary_apiKey_set)
                }
            }
            pref.setOnBindEditTextListener { editText ->
                editText.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            val storedApiKey = PixivMuzeiSupervisor.getApiKey()
            if (pref.text.isNullOrBlank() && storedApiKey.isNotBlank()) {
                pref.text = storedApiKey
            }
            pref.setOnPreferenceChangeListener { preference, newValue ->
                val sanitized = (newValue as? String).orEmpty().trim()
                if (sanitized.isEmpty()) {
                    PixivMuzeiSupervisor.clearApiKey()
                } else {
                    PixivMuzeiSupervisor.setApiKey(sanitized)
                }
                if (sanitized != newValue) {
                    (preference as EditTextPreference).text = sanitized
                    return@setOnPreferenceChangeListener false
                }
                true
            }
        }
    }

    // 0 or 1 correspond to SFW or NSFW respectively
    private fun rankingSummaryStringGenerator(selection: Set<String>): String {
        val entries = resources.getStringArray(R.array.pref_rankingFilterLevel_entries)
        val values = resources.getStringArray(R.array.pref_rankingFilterLevel_entryvalues)
        val valueToEntry = values.indices.associate { values[it] to entries[it] }
        val orderedSelection = values.filter { selection.contains(it) }
        val summaryValues = if (orderedSelection.isEmpty()) {
            listOf(entries.first())
        } else {
            orderedSelection.mapNotNull { valueToEntry[it] }
        }
        return summaryValues.joinToString(", ")
    }

    private fun categorySummaryStringGenerator(selection: Set<String>): String {
        val entries = resources.getStringArray(R.array.pref_rankingCategory_entries)
        val values = resources.getStringArray(R.array.pref_rankingCategory_entryvalues)
        val valueToEntry = values.indices.associate { values[it] to entries[it] }
        val orderedSelection = values.filter { selection.contains(it) }
        val summaryValues = if (orderedSelection.isEmpty()) {
            entries.toList()
        } else {
            orderedSelection.mapNotNull { valueToEntry[it] }
        }
        return summaryValues.joinToString(", ")
    }

    // Returns a comma delimited string of user selections. There is no trailing comma
    // newValue is a HashSet that can contain 2, 4, 6, or 8, and corresponds to
    // SFW, Slightly Ecchi, Fairly Ecchi, and R18 respectively
    // 2, 4, 6, 8 was selected to match with the values used in Pixiv JSON
    private fun authSummaryStringGenerator(selection: Set<String>): String {
        val authFilterEntriesPossible =
            resources.getStringArray(R.array.pref_authFilterLevel_entries)
        return StringBuilder().let {
            selection.forEachIndexed { _, element ->
                // Translation from {2, 4, 6, 8} to {0, 1, 2, 3}
                it.append(authFilterEntriesPossible[(element.toInt() - 2) / 2])
                it.append(", ")
            }
            it.setLength(it.length - 2)
            it.toString()
        }
    }

    // Functions in here action only on app exit
    override fun onStop() {
        super.onStop()
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        newUpdateMode = sharedPrefs.getString("pref_updateMode", "") ?: ""
        newTag = sharedPrefs.getString("pref_tagSearch", "") ?: ""
        newArtist = sharedPrefs.getString("pref_artistId", "") ?: ""
        val newRankingFilters = sharedPrefs.getStringSet("pref_rankingFilterSelect", setOf("sfw"))
            ?.toSet() ?: setOf("sfw")
        val newRankingCategories = sharedPrefs.getStringSet(
            "pref_rankingCategorySelect",
            setOf("general", "anime", "people")
        )?.toSet() ?: setOf("general", "anime", "people")

        // If user has changed update, filter mode, or search tag:
        // Immediately stop any pending work, clear the Provider of any Artwork, and then toast
        if (oldUpdateMode != newUpdateMode || oldTag != newTag
            || oldArtist != newArtist || oldRankingFilters != newRankingFilters
            || oldRankingCategories != newRankingCategories
        ) {
            WorkManager.getInstance(requireContext()).cancelUniqueWork("ANTONY")
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?.deleteRecursively()
            enqueueLoad(true, requireContext())
            when {
                oldUpdateMode != newUpdateMode -> Toast.makeText(
                    context,
                    getString(R.string.toast_newUpdateMode),
                    Toast.LENGTH_SHORT
                ).show()
                oldArtist != newArtist -> Toast.makeText(
                    context,
                    getString(R.string.toast_newArtist),
                    Toast.LENGTH_SHORT
                ).show()
                oldTag != newTag -> Toast.makeText(
                    context,
                    getString(R.string.toast_newTag),
                    Toast.LENGTH_SHORT
                ).show()
                oldRankingFilters != newRankingFilters || oldRankingCategories != newRankingCategories ->
                    Toast.makeText(
                        context,
                        getString(R.string.toast_newFilterSelect),
                        Toast.LENGTH_SHORT
                    ).show()
                else -> {
                    Toast.makeText(
                        context,
                        getString(R.string.toast_newFilterSelect),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
