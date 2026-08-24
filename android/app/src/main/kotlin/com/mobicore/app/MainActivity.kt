package com.mobicore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mobicore.app.ui.MobiCoreApp
import com.mobicore.app.ui.MobiCoreTheme

/**
 * Single Activity host.
 *
 * A running MIDlet holds a virtual machine and a live game thread, so the app
 * deliberately avoids Activity recreation on rotation — the manifest handles
 * the configuration changes itself.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val library = (application as MobiCoreApplication).library
        setContent {
            // Whatever the user chose last time, from the moment the app opens.
            val theme by library.theme.collectAsState()
            MobiCoreTheme(themeChoice = theme) {
                MobiCoreApp(library = library, filesDir = filesDir.absolutePath)
            }
        }
    }
}
