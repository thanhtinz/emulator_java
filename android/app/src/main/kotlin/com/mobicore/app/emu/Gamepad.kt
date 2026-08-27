package com.mobicore.app.emu

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Android's controller events, in the emulator's own names.
 *
 * Android has a key code for every button any pad ever shipped; the emulator
 * has fourteen names for the controls a pad really has. Turning one into the
 * other happens here and nowhere else, so what a button does is decided once,
 * in the profile, for Android and iOS alike.
 *
 * A keyboard is included on purpose. On a tablet with a case, or a phone with
 * one paired, the arrow keys and space are the same input as a pad's — and a
 * player who has a keyboard in front of them will try them.
 */
object Gamepad {

    /** The emulator's name for an Android key code, or null if it is not one. */
    fun padFor(keyCode: Int): String? = when (keyCode) {
        // The d-pad, which a stick also reports once it has been pushed far
        // enough — Android does that conversion itself.
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> "padUp"
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> "padDown"
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> "padLeft"
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> "padRight"

        // The face buttons. Space and Enter are here because a keyboard has
        // no A button and every player tries those two first.
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER -> "padA"
        KeyEvent.KEYCODE_BUTTON_B -> "padB"
        KeyEvent.KEYCODE_BUTTON_X -> "padX"
        KeyEvent.KEYCODE_BUTTON_Y -> "padY"

        KeyEvent.KEYCODE_BUTTON_L1 -> "padL1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "padR1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "padL2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "padR2"

        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> "padStart"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "padSelect"
        else -> null
    }

    /**
     * True when this event came from something a player holds.
     *
     * Checked because the same key codes arrive from the phone itself: the
     * volume rocker reports as a key, and a soft keyboard reports letters. A
     * game should not start firing because someone typed a name into a search
     * box on another screen.
     */
    fun isController(event: KeyEvent): Boolean {
        val source = event.source
        return source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
    }

    /** How far a stick must be pushed before it counts as a direction. */
    private const val DEAD_ZONE = 0.5f

    /**
     * The directions an analogue stick is currently pushing.
     *
     * A J2ME game has four directions and nothing else, so a stick is read as
     * a d-pad rather than as an axis. The dead zone is wide on purpose: a worn
     * stick that rests at 0.2 would otherwise walk the player into a wall for
     * as long as the game is open.
     */
    fun directionsFrom(event: MotionEvent): Set<String> {
        val x = axis(event, MotionEvent.AXIS_X, MotionEvent.AXIS_HAT_X)
        val y = axis(event, MotionEvent.AXIS_Y, MotionEvent.AXIS_HAT_Y)
        val held = mutableSetOf<String>()
        if (x <= -DEAD_ZONE) held += "padLeft"
        if (x >= DEAD_ZONE) held += "padRight"
        if (y <= -DEAD_ZONE) held += "padUp"
        if (y >= DEAD_ZONE) held += "padDown"
        return held
    }

    /** Whichever of the stick and the hat is pushed further. */
    private fun axis(event: MotionEvent, stick: Int, hat: Int): Float {
        val fromStick = event.getAxisValue(stick)
        val fromHat = event.getAxisValue(hat)
        return if (kotlin.math.abs(fromHat) > kotlin.math.abs(fromStick)) fromHat else fromStick
    }
}

/**
 * Which engine controller events go to, if any.
 *
 * The Activity receives them and the engine consumes them, and nothing in
 * between knows about either. One nullable reference, set while a game is on
 * screen and cleared when it leaves, is the whole connection: while it is
 * null the events go back to Android, so the d-pad still walks the library.
 */
object GamepadRouter {
    @Volatile
    var engine: EmulatorEngine? = null
}
