/*
 * Copyright (c) 2026 American Printing House for the Blind
 * Use of this source code is governed by an MIT-style license that can be found in the LICENSE file.
 */

package org.aph.monarchtempest.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.aph.monarchtempest.R

class TextEditActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.text_edit_activity_layout)
    }

}