/*
 * Copyright (c) 2026 American Printing House for the Blind
 * Use of this source code is governed by an MIT-style license that can be found in the LICENSE file.
 */

package org.aph.monarchtempest.monarch_utils

import com.humanware.keysoftsdk.translator.BrailleGrade
import com.humanware.keysoftsdk.translator.Translator
import com.humanware.keysoftsdk.translator.aidl.TranslationQuery

class BrailleUtil {
    companion object {
        /**
         * Translates a plain text string into a unicode braille string using the
         * current system translator preferences.
         *
         * @param input String object which contains the plain text to be translated.
         * @param translator HumanWare Translator object that performs the translation.
         * @param setBack (Optional) Boolean when set to true it will convert unicode braille back to plain text. Default is false.
         *
         * @return Unicode Braille String of the original string that was passed in.
         */
        fun translate(input: String, translator: Translator, setBack: Boolean = false): String {
            var res = ""
            val query = TranslationQuery().apply {
                // Dynamically fetch and respect the user's configured system braille grade
                grade = getBrailleGrade(translator)
                isBack = setBack
            }

            val lines = input.lines()
            for (line in lines) {
                query.original = line
                translator.translate(query)
                res += query.translated + "\n"
            }
            return res.trimEnd('\n')
        }

        /**
         * Dynamically queries the current system configuration from the Monarch SDK translator instance.
         *
         * @param translator HumanWare Translator object that performs the translation.
         * @return An Integer matching BrailleGrade enum values.
         */
        fun getBrailleGrade(translator: Translator): Int {
            // Run an empty forward translation query without forcing magic/zero flags
            // to allow the SDK engine to supply its globally configured user preference profile.
            val query = TranslationQuery().apply {
                original = " "
                grade = BrailleGrade.GRADE_UNSPECIFIED
            }

            translator.translate(query)

            // Fall back safely to GRADE_UNSPECIFIED if the response profile isn't available
            return if (query.grade != BrailleGrade.GRADE_UNSPECIFIED) {
                query.grade
            } else {
                BrailleGrade.GRADE_UNSPECIFIED
            }
        }
    }
}