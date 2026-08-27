package com.mobicore.app.emu

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * The phone's own idea of which way it is being held.
 *
 * Android reports gravity in metres per second squared along three axes; what
 * the emulator wants is "how far is this leaning", from -1 to 1. Dividing by
 * gravity is the whole conversion: a phone on its side reads 9.8 on one axis
 * and that is a full lean.
 *
 * Listening stops with the game. A sensor left running is a sensor draining a
 * battery for a screen nobody is looking at.
 */
class TiltSensor(context: Context, private val onTilt: (Float, Float) -> Unit) :
    SensorEventListener {

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gravity = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** True on a phone that has no such sensor, so the setting can say so. */
    val available: Boolean get() = gravity != null

    fun start() {
        val sensor = gravity ?: return
        // The game reads held keys at its own pace, so the fastest rate would
        // be readings nobody looks at. "Game" is the rate Android means for
        // exactly this.
        sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 3) {
            return
        }
        // Held upright in portrait, gravity is along -y. Leaning right tips
        // it into x, and leaning away from the player tips it out of y — so
        // those two are the steering.
        val x = -event.values[0] / SensorManager.GRAVITY_EARTH
        val y = (event.values[1] / SensorManager.GRAVITY_EARTH) - 1f
        onTilt(clamp(x), clamp(y))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Nothing to do: a less accurate reading of which way a phone leans is
        // still which way it leans.
    }

    private fun clamp(value: Float): Float =
        if (abs(value) > 1f) (if (value < 0) -1f else 1f) else value
}
