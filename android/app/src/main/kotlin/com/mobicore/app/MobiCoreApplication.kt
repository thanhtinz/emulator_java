package com.mobicore.app

import android.app.Application
import com.mobicore.app.data.LibraryRepository

/**
 * Holds the single library instance.
 *
 * The library owns file handles and an index that must not diverge between
 * screens, so it is created once here rather than per Activity.
 */
class MobiCoreApplication : Application() {

    lateinit var library: LibraryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        library = LibraryRepository(filesDir.absolutePath)
        library.open()
    }
}
