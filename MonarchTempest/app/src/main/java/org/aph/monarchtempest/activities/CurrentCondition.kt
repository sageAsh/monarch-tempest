/*
 * Copyright (c) 2026 American Printing House for the Blind
 * Use of this source code is governed by an MIT-style license that can be found in the LICENSE file.
 */

package org.aph.monarchtempest.activities

import android.content.Intent
import android.os.Bundle
import com.humanware.keysoftsdk.ui.menu.AbstractMenuActivity
import com.humanware.keysoftsdk.ui.menu.AccessibleListView
import com.humanware.keysoftsdk.ui.menu.accessibleitem.AccessibleItem
import com.humanware.keysoftsdk.ui.menu.accessibleitem.attributes.AccessibleItemAttributes
import com.humanware.keysoftsdk.R as sdkR
import org.aph.monarchtempest.R as demoR

/**
 * Entry point for the "Current Conditions" section of the weather app.
 *
 * Allows the user to choose between displaying the live weather from the
 * Open-Meteo API as scrollable text or as a tactile graphic icon with a label.
 */
class CurrentCondition : AbstractMenuActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(demoR.string.current_condition_menu_title)
    }

    /**
     * Gets the resource id for the menu's title.
     */
    override fun getTitleId() = demoR.string.current_condition_menu_title

    /**
     * Initializes the menu and specifies the item sorting strategy.
     */
    override fun initialize(): AccessibleListView.Sort {
        listview = inflateSettingItemLayout()
        return AccessibleListView.Sort.BY_POSITION
    }

    /**
     * Inflates the accessible list items for the sub-menu.
     */
    private fun inflateSettingItemLayout(): AccessibleListView {
        return findViewById<AccessibleListView>(sdkR.id.settings_listview).apply {
            // Add "Text" option (Position 0)
            setNext(makeAccessibleItem(demoR.string.show_currentCond_text))
            // Add "Graphic" option (Position 1)
            setNext(makeAccessibleItem(demoR.string.show_currentCond_graphic))

            setOnItemClickListener { _, _, position, _ ->
                val intent = when (position) {
                    0 -> Intent(this@CurrentCondition, CurrentWeatherActivity::class.java)
                    1 -> Intent(this@CurrentCondition, CurrentWeatherGraphicActivity::class.java)
                    else -> null
                }

                if (intent != null) {
                    startActivity(intent)
                }
            }
        }
    }

    /**
     * Creates an [AccessibleItem] to be displayed in the menu.
     */
    private fun makeAccessibleItem(
        labelNameId: Int,
        layoutId: Int = sdkR.layout.one_textview,
        labelId: Int = sdkR.id.textview
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