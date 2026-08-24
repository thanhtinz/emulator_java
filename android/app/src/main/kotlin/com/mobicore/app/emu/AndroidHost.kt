package com.mobicore.app.emu

import android.util.Log
import com.mobicore.core.vm.VmHost

/**
 * Platform services for the emulated program.
 *
 * `System.exit` is turned into a thrown error rather than being honoured: a
 * MIDlet must be able to end itself without taking the whole app down with it.
 */
class AndroidHost : VmHost {

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun print(error: Boolean, text: String) {
        if (error) {
            Log.w(TAG, text.trimEnd())
        } else {
            Log.i(TAG, text.trimEnd())
        }
    }

    override fun exit(code: Int) {
        throw com.mobicore.core.vm.VmError("The MIDlet called System.exit($code)")
    }

    override fun property(name: String): String? = when (name) {
        "microedition.platform" -> "MobiCore/Android"
        "microedition.encoding" -> "UTF-8"
        "microedition.locale" -> java.util.Locale.getDefault().toString().replace('_', '-')
        else -> null
    }

    @Throws(InterruptedException::class)
    override fun sleep(millis: Long) {
        Thread.sleep(millis)
    }

    private companion object {
        const val TAG = "MobiCore"
    }
}
