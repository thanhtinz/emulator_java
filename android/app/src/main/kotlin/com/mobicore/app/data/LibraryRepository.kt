package com.mobicore.app.data

import com.mobicore.core.jar.SuiteLoader
import com.mobicore.core.library.GameLibrary
import com.mobicore.core.library.LibraryEntry
import com.mobicore.core.model.AutoSetup
import com.mobicore.core.model.GameProfile
import com.mobicore.core.rms.RecordStoreManager
import com.mobicore.core.storage.LocalVfs
import com.mobicore.core.storage.StorageLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android-facing wrapper around the portable [GameLibrary].
 *
 * Nothing here is Android-specific beyond the root directory: keeping the
 * logic in the core is what lets the iOS app behave identically.
 */
class LibraryRepository(filesDir: String) {

    private val layout = StorageLayout(StorageLayout.join(filesDir, "MobiCore"))
    private val library = GameLibrary(LocalVfs(), layout)

    private val _games = MutableStateFlow<List<LibraryEntry>>(emptyList())
    val games: StateFlow<List<LibraryEntry>> = _games.asStateFlow()

    private val _profiles = MutableStateFlow<Map<String, GameProfile>>(emptyMap())
    val profiles: StateFlow<Map<String, GameProfile>> = _profiles.asStateFlow()

    fun open() {
        library.setClock(System.currentTimeMillis())
        library.open()
        refresh()
    }

    private fun refresh() {
        _games.value = library.all()
        _profiles.value = library.allProfiles()
    }

    fun storageLayout(): StorageLayout = layout

    fun rawLibrary(): GameLibrary = library

    /** Imports a JAR, optionally with its descriptor. */
    fun importSuite(jar: ByteArray, jad: ByteArray?): LibraryEntry {
        library.setClock(System.currentTimeMillis())
        val result = library.install(jar, jad)
        refresh()
        return result.entry()
    }

    fun uninstall(suiteId: String, keepData: Boolean) {
        library.uninstall(suiteId, keepData)
        refresh()
    }

    fun load(suiteId: String): SuiteLoader = library.load(suiteId)

    fun profile(suiteId: String): GameProfile? = library.profile(suiteId)

    fun saveProfile(profile: GameProfile) {
        library.saveProfile(profile)
        refresh()
    }

    fun records(suiteId: String): RecordStoreManager = library.records(suiteId)

    fun artwork(suiteId: String): ByteArray? = library.artwork(suiteId)

    fun backup(suiteId: String): String {
        library.setClock(System.currentTimeMillis())
        val path = library.backup(suiteId)
        refresh()
        return path
    }

    fun restore(archive: ByteArray): LibraryEntry {
        val entry = library.restore(archive)
        refresh()
        return entry
    }

    fun resetGameData(suiteId: String): String {
        library.setClock(System.currentTimeMillis())
        return library.resetGameData(suiteId)
    }

    fun backupsFor(suiteId: String): List<String> = library.backupsFor(suiteId)

    fun search(query: String): List<LibraryEntry> = library.search(query)

    fun sorted(entries: List<LibraryEntry>, mode: Int): List<LibraryEntry> =
        library.sort(entries, mode, _profiles.value)

    fun favourites(): List<LibraryEntry> = library.favourites(_profiles.value)

    /** Games with a recorded play time, most recent first. */
    fun recentlyPlayed(limit: Int = 6): List<LibraryEntry> =
        library.sort(library.all(), GameLibrary.SORT_RECENT, _profiles.value)
            .filter { (_profiles.value[it.suiteId()]?.lastPlayed() ?: 0L) > 0L }
            .take(limit)

    fun markPlayed(suiteId: String) {
        val profile = library.profile(suiteId) ?: return
        profile.markPlayed(System.currentTimeMillis())
        library.saveProfile(profile)
        refresh()
    }

    /**
     * Renames a game as the library lists it. The suite's own manifest title
     * is kept, so the change is reversible and a reinstall still matches.
     */
    /**
     * Configures a game from the game again, throwing away hand-set values.
     *
     * The user's own choices — volume, whether it is a favourite — are not
     * detections and are carried across; everything else is worked out from
     * the suite as it was at import.
     */
    /**
     * Stores where a game was, with the screen the player left behind.
     *
     * The picture is not decoration: coming back to several games, a player
     * recognises where they were from the screen long before a date tells
     * them.
     */
    fun writeSaveState(suiteId: String, state: ByteArray, screenshot: ByteArray?) {
        library.writeSaveState(suiteId, state, screenshot)
        refresh()
    }

    fun readSaveState(suiteId: String): ByteArray? = library.readSaveState(suiteId)

    fun saveStateThumbnail(suiteId: String): ByteArray? = library.saveStateThumbnail(suiteId)

    fun hasSaveState(suiteId: String): Boolean = library.hasSaveState(suiteId)

    fun deleteSaveState(suiteId: String) {
        library.deleteSaveState(suiteId)
        refresh()
    }

    fun autoSetup(suiteId: String): GameProfile? {
        val current = library.profile(suiteId) ?: return null
        val fresh = AutoSetup.configure(library.load(suiteId)).profile()
        fresh.setVolume(current.volume())
        fresh.setMuted(current.isMuted)
        fresh.isFavourite = current.isFavourite
        library.saveProfile(fresh)
        refresh()
        return fresh
    }

    fun rename(suiteId: String, title: String) {
        library.rename(suiteId, title)
        refresh()
    }

    fun resetTitle(suiteId: String) {
        library.resetTitle(suiteId)
        refresh()
    }

    /** Replaces the cover. PNG only — the core refuses anything else. */
    fun setArtwork(suiteId: String, png: ByteArray) {
        library.setArtwork(suiteId, png)
        refresh()
    }

    fun resetArtwork(suiteId: String) {
        library.resetArtwork(suiteId)
        refresh()
    }

    fun toggleFavourite(suiteId: String) {
        val profile = library.profile(suiteId) ?: return
        profile.isFavourite = !profile.isFavourite
        library.saveProfile(profile)
        refresh()
    }
}
