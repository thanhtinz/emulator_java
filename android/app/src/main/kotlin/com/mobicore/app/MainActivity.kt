package com.mobicore.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mobicore.app.emu.Gamepad
import com.mobicore.app.emu.GamepadRouter
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

    /**
     * Controller events arrive at the Activity, not at a composable.
     *
     * A pad has no idea what has focus, and Android delivers its buttons to
     * whoever is on top. The running game — if there is one — takes them; when
     * there is not, they go back to Android so the d-pad still walks the
     * library list.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val engine = GamepadRouter.engine
        val pad = Gamepad.padFor(event.keyCode)
        if (engine != null && pad != null && Gamepad.isController(event)) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    // A held button repeats; the game already knows it is held.
                    if (event.repeatCount == 0 && engine.pressPad(pad)) {
                        return true
                    }
                    if (event.repeatCount > 0) {
                        return true
                    }
                }
                KeyEvent.ACTION_UP -> {
                    engine.releasePad(pad)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** A stick moved: read as a d-pad, because these games have four ways. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val engine = GamepadRouter.engine
        if (engine != null && event.action == MotionEvent.ACTION_MOVE) {
            engine.stickMoved(Gamepad.directionsFrom(event))
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

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
