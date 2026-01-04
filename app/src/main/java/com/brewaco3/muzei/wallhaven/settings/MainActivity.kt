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
package com.brewaco3.muzei.wallhaven.settings

import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.brewaco3.muzei.wallhaven.BuildConfig
import com.brewaco3.muzei.wallhaven.R
import com.brewaco3.muzei.wallhaven.common.WallhavenMuzeiActivity
import com.brewaco3.muzei.wallhaven.settings.fragments.AdvOptionsPreferenceFragment
import com.brewaco3.muzei.wallhaven.util.IntentUtils
import com.google.android.apps.muzei.api.MuzeiContract.Sources.createChooseProviderIntent
import com.google.android.apps.muzei.api.MuzeiContract.Sources.isProviderSelected
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : WallhavenMuzeiActivity(), AdvOptionsPreferenceFragment.NightModePreferenceListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val tabTitles = intArrayOf(
            R.string.tab_heading_main,
            R.string.tab_heading_adv_options,
            R.string.tab_heading_artwork_delete
        )

        setContentView(R.layout.activity_main)

        // Setup FAB with window insets for safe area positioning
        val fab = findViewById<FloatingActionButton>(R.id.fab_delete)
        val fabParams = fab.layoutParams as ViewGroup.MarginLayoutParams
        val initialBottomMargin = fabParams.bottomMargin
        val initialRightMargin = fabParams.rightMargin
        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = initialBottomMargin + insets.bottom
            params.rightMargin = initialRightMargin + insets.right
            view.layoutParams = params
            windowInsets
        }

        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        val sectionsPagerAdapter = SectionsPagerAdapter(this)
        viewPager.adapter = sectionsPagerAdapter
        TabLayoutMediator(findViewById(R.id.tabs), viewPager) { tab, position ->
            tab.text = applicationContext.resources.getString(tabTitles[position])
        }.attach()
    }

    override fun onResume() {
        super.onResume()

        // If Muzei is not installed, this will redirect the user to Muzei's Play Store listing
        if (!isMuzeiInstalled) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialogTitle_muzeiNotInstalled))
                .setMessage(getString(R.string.dialog_installMuzei))
                .setPositiveButton(R.string.dialog_yes) { dialog: DialogInterface?, which: Int ->
                    if (!IntentUtils.launchActivity(
                            this,
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=net.nurik.roman.muzei"))
                        )
                    ) {
                        val fallback = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=net.nurik.roman.muzei")
                        )
                        IntentUtils.launchActivity(this, fallback)
                    }
                }
                .setNegativeButton(R.string.dialog_no) { dialog: DialogInterface, which: Int ->
                    // Do nothing
                    dialog.dismiss()
                }
                .show()
        } else if (!isProviderSelected(this, BuildConfig.APPLICATION_ID + ".provider")) {
        // If Wallhaven for Muzei 3 is not the selected provider
            AlertDialog.Builder(this)
                .setTitle(applicationContext.getString(R.string.dialogTitle_muzeiNotActiveSource))
                .setMessage(applicationContext.getString(R.string.dialog_selectSource))
                .setNeutralButton(android.R.string.ok) { dialog: DialogInterface?, which: Int ->
                    val intent = createChooseProviderIntent(BuildConfig.APPLICATION_ID + ".provider")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    finishAffinity()
                    IntentUtils.launchActivity(this, intent)
                }
                .show()
        }
    }

    // Checks if Muzei is installed
    private val isMuzeiInstalled: Boolean
        get() {
            var found = true
            try {
                applicationContext.packageManager.getPackageInfo("net.nurik.roman.muzei", 0)
            } catch (ex: PackageManager.NameNotFoundException) {
                found = false
            }
            return found
        }

    override fun nightModeOptionSelected(option: Int) {
        AppCompatDelegate.setDefaultNightMode(option)
    }
}
