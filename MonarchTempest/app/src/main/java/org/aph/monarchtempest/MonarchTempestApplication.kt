/*
 * Copyright (c) 2026 American Printing House for the Blind
 * Use of this source code is governed by an MIT-style license that can be found in the LICENSE file.
 */

package org.aph.monarchtempest

import android.app.Application
import com.humanware.keysoftsdk.contextmenu.WriteCommandsXmlFileToInternalMemoryStorageExecutor
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MonarchTempestApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        //pass the commands.xml file to KeySoft
        WriteCommandsXmlFileToInternalMemoryStorageExecutor(this).execute()
    }
}