package com.mobicore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            MobiCoreTheme {
                MobiCoreApp(library = library, filesDir = filesDir.absolutePath)
            }
        }
    }
}
