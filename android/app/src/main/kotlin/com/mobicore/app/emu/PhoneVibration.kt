package com.mobicore.app.emu

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.mobicore.core.haptics.VibrationSink

/**
 * The phone's motor, on the other side of the core's [VibrationSink].
 *
 * A J2ME game's only physical feedback was the handset shaking: the buzz on a
 * crash or a hit is part of what the game was. The core decides when and for
 * how long; this only carries it to the device.
 */
class PhoneVibration(context: Context) : VibrationSink {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()

    override fun vibrate(durationMs: Int): Boolean {
        // The honest answer, which is what MIDP's own vibrate reports: a game
        // told no may draw something instead.
        val motor = vibrator?.takeIf { it.hasVibrator() } ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                motor.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs.toLong(),
                        VibrationEffect.DEFAULT_AMPLITUDE,
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                motor.vibrate(durationMs.toLong())
            }
            true
        }.getOrDefault(false)
    }

    override fun cancel() {
        runCatching { vibrator?.cancel() }
    }
}
