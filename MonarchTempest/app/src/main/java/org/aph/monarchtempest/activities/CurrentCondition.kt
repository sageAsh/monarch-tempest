/*
 * Copyright (c) 2026 American Printing House for the Blind
 * Use of this source code is governed by an MIT-style license that can be found in the LICENSE file.
 */

package org.aph.monarchtempest.activities

import android.content.Intent
import android.os.Bundle
import com.humanware.keysoftsdk.R
import com.humanware.keysoftsdk.ui.menu.AbstractMenuActivity
import com.humanware.keysoftsdk.ui.menu.AccessibleListView
import com.humanware.keysoftsdk.ui.menu.accessibleitem.AccessibleItem
import com.humanware.keysoftsdk.ui.menu.accessibleitem.attributes.AccessibleItemAttributes

/**
 * Entry point for the "Current Conditions" section of the weather app.
 *
 * Contains an item that takes the user directly to [CurrentWeatherActivity], which fetches
 * and displays live weather data from the Open-Meteo API in braille.
 */
class CurrentCondition : AbstractMenuActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(org.aph.monarchtempest.R.string.current_condition_menu_title)
    }

    override fun getTitleId() = org.aph.monarchtempest.R.string.current_condition_menu_title

    override fun initialize(): AccessibleListView.Sort {
        listview = inflateSettingItemLayout()
        return AccessibleListView.Sort.BY_POSITION
    }

    private fun inflateSettingItemLayout(): AccessibleListView {
        return findViewById<AccessibleListView>(R.id.settings_listview).apply {
            setNext(makeAccessibleItem(org.aph.monarchtempest.R.string.show_currentCond_text))

            setOnItemClickListener { _, _, position, _ ->
                when (position) {
                    0 -> startActivity(Intent(this@CurrentCondition, CurrentWeatherActivity::class.java))
                }
            }
        }
    }

    private fun makeAccessibleItem(
        labelNameId: Int,
        layoutId: Int = R.layout.one_textview,
        labelId: Int  = R.id.textview
    ): AccessibleItem {
        return AccessibleItem(
            AccessibleItemAttributes(
                resources.getString(labelNameId),
                layoutId,
                labelId
            )
        )
    }
}