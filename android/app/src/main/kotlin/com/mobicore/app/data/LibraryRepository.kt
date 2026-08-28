package com.mobicore.app.data

import com.mobicore.core.jar.SuiteLoader
import com.mobicore.core.library.BatchImport
import com.mobicore.core.library.GameLibrary
import com.mobicore.core.library.LibraryArchive
import com.mobicore.core.library.LibraryEntry
import com.mobicore.core.library.PresetStore
import com.mobicore.core.mod.ModManager
import com.mobicore.core.mod.ResourceCatalog
import com.mobicore.core.tools.ItemChest
import com.mobicore.core.tools.SaveScanner
import com.mobicore.core.model.AppSettings
import com.mobicore.core.model.AutoSetup
import com.mobicore.core.library.CollectionStore
import com.mobicore.core.library.ShareExport
import com.mobicore.core.library.UrlInstaller
import com.mobicore.core.midp.MidpFiles
import com.mobicore.core.net.HttpTransport
import com.mobicore.core.net.NetworkPolicy
import com.mobicore.core.net.NetworkStack
import com.mobicore.core.net.NetworkTransport
import com.mobicore.core.model.DeviceProfile
import com.mobicore.core.model.GamepadProfile
import com.mobicore.core.model.GameProfile
import com.mobicore.core.model.MidletEntry
import com.mobicore.core.rms.RecordStoreManager
import com.mobicore.core.storage.Json
import com.mobicore.core.storage.LocalVfs
import com.mobicore.core.storage.StorageLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One save slot, as the slots screen lists it. */
data class SaveSlot(val slot: Int, val savedAt: Long, val used: Boolean)

/**
 * Android-facing wrapper around the portable [GameLibrary].
 *
 * Nothing here is Android-specific beyond the root directory: keeping the
 * logic in the core is what lets the iOS app behave identically.
 */
class LibraryRepository(filesDir: String) {

    private val layout = StorageLayout(StorageLayout.join(filesDir, "MobiCore"))
    private val vfs = LocalVfs()
    private val library = GameLibrary(vfs, layout)
    private val presetStore = PresetStore(vfs, layout)

    private val _games = MutableStateFlow<List<LibraryEntry>>(emptyList())
    val games: StateFlow<List<LibraryEntry>> = _games.asStateFlow()

    private val _profiles = MutableStateFlow<Map<String, GameProfile>>(emptyMap())
    val profiles: StateFlow<Map<String, GameProfile>> = _profiles.asStateFlow()

    fun open() {
        library.setClock(System.currentTimeMillis())
        library.open()
        refresh()
        refreshPresets()
    }

    private fun refresh() {
        _games.value = library.all()
        _profiles.value = library.allProfiles()
        refreshCollections()
    }

    fun storageLayout(): StorageLayout = layout

    fun rawLibrary(): GameLibrary = library

    /** Imports a JAR, optionally with its descriptor. */
    fun importSuite(jar: ByteArray, jad: ByteArray?): LibraryEntry {
        library.setClock(System.currentTimeMillis())
        val result = library.install(jar, jad)
        applyDefaultPreset()
        refresh()
        return result.entry()
    }

    /**
     * Imports everything the user picked, reporting on each file.
     *
     * One broken download in a folder of eighty must not stop the other
     * seventy-nine; the core pairs descriptors with archives and unpacks a zip
     * of games on the way.
     */
    fun importMany(names: Array<String>, payloads: Array<ByteArray>): BatchImport.Report {
        val report = BatchImport.run(library, names, payloads)
        applyDefaultPreset()
        refresh()
        return report
    }

    fun uninstall(suiteId: String, keepData: Boolean) {
        library.uninstall(suiteId, keepData)
        refresh()
    }

    fun load(suiteId: String): SuiteLoader = library.load(suiteId)

    // ------------------------------------------- tìm vàng, ngọc trong phần lưu

    /** Kết quả lần tìm gần nhất, để lần sau lọc tiếp trên nó. */
    private var saveHits: List<SaveScanner.Hit> = emptyList()

    /**
     * Lần tìm đầu: mọi chỗ trong phần lưu đang mang con số người chơi thấy.
     */
    fun scanSave(suiteId: String, value: Long): List<SaveScanner.Hit> {
        saveHits = SaveScanner.find(library.records(suiteId), value)
        return saveHits
    }

    /**
     * Lần tìm sau: giữ lại những chỗ nay mang con số mới.
     *
     * Đây mới là chỗ tìm ra cái đúng: một con số trùng ở lần đầu có thể là
     * điểm, là toạ độ; chỉ ô thật sự giữ số vàng mới đổi theo.
     */
    fun narrowSave(suiteId: String, value: Long): List<SaveScanner.Hit> {
        saveHits = if (saveHits.isEmpty()) {
            SaveScanner.find(library.records(suiteId), value)
        } else {
            SaveScanner.narrow(library.records(suiteId), saveHits, value)
        }
        return saveHits
    }

    fun clearSaveScan() {
        saveHits = emptyList()
    }

    /** Danh sách rỗng cùng kiểu, để màn hình dựng trạng thái ban đầu. */
    fun clearedHits(): List<SaveScanner.Hit> = emptyList()

    /**
     * Đặt cùng một con số vào mọi chỗ còn lại, sau khi sao lưu.
     *
     * @return sửa được mấy chỗ
     */
    fun setSaveValue(suiteId: String, value: Long): Int {
        if (saveHits.isEmpty()) return 0
        library.backup(suiteId)
        val records = library.records(suiteId)
        var written = 0
        saveHits.forEach { hit ->
            if (SaveScanner.fits(value, hit.encoding()) &&
                SaveScanner.write(records, hit, value, System.currentTimeMillis())
            ) {
                written++
            }
        }
        return written
    }

    /** Những vật phẩm đã tìm ra và đặt tên, lọc theo ô tìm kiếm. */
    fun items(suiteId: String, query: String = ""): List<ItemChest.Item> =
        ItemChest(vfs, layout, suiteId).search(library.records(suiteId), query)

    /** Cất chỗ vừa tìm được dưới một cái tên; từ đó chỉ còn gõ số lượng. */
    fun keepItem(suiteId: String, name: String): Boolean {
        if (saveHits.isEmpty()) return false
        ItemChest(vfs, layout, suiteId).keep(name, saveHits, saveHits[0].value())
        return true
    }

    /**
     * Gửi một số lượng vào game.
     *
     * @return số chỗ đã ghi được; 0 khi con số không vừa chỗ game để dành
     */
    fun sendItem(suiteId: String, itemId: String, amount: Long): Int {
        val chest = ItemChest(vfs, layout, suiteId)
        val item = chest.find(itemId) ?: return 0
        if (amount < 0 || amount > item.ceiling()) return 0
        library.backup(suiteId)
        return chest.send(library.records(suiteId), item, amount, System.currentTimeMillis())
    }

    fun forgetItem(suiteId: String, itemId: String) {
        ItemChest(vfs, layout, suiteId).forget(itemId)
    }

    /** Kho tài nguyên của một game, kèm những gì người chơi đã tự thay. */
    fun resources(suiteId: String): List<ResourceCatalog.Entry> =
        ResourceCatalog.scan(library.load(suiteId), mods(suiteId).installed())

    fun mods(suiteId: String): ModManager = ModManager(library, suiteId)

    /**
     * Thay một tệp trong game bằng tệp người chơi chọn.
     *
     * Bản gốc không bị đụng tới: thứ thay vào nằm trong một bản mod riêng phủ
     * lên trên, nên bỏ ra lúc nào cũng được.
     */
    fun replaceResource(suiteId: String, path: String, bytes: ByteArray) {
        mods(suiteId).replaceResource(path, bytes)
    }

    fun restoreResource(suiteId: String, path: String) {
        mods(suiteId).restoreResource(path)
    }

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
    // -------------------------------------------------------- app settings

    private val _theme = MutableStateFlow(loadTheme())

    /** Light, dark or follow the phone; see `AppSettings`. */
    val theme: StateFlow<Int> = _theme.asStateFlow()

    fun setTheme(choice: Int) {
        val settings = appSettings()
        settings.setTheme(choice)
        writeAppSettings(settings)
        _theme.value = settings.theme()
    }

    /** Light to dark to system and back, for the switch on the home screen. */
    fun cycleTheme() {
        val settings = appSettings()
        settings.setTheme(settings.nextTheme())
        writeAppSettings(settings)
        _theme.value = settings.theme()
    }

    private val _librarySort = MutableStateFlow(appSettings().librarySort())

    /** The order the library opens in, remembered between sessions. */
    val librarySort: StateFlow<Int> = _librarySort.asStateFlow()

    fun setLibrarySort(sort: Int) {
        val settings = appSettings()
        settings.setLibrarySort(sort)
        writeAppSettings(settings)
        _librarySort.value = settings.librarySort()
    }

    private fun loadTheme(): Int = appSettings().theme()

    private fun appSettings(): AppSettings {
        val path = layout.settingsPath()
        if (!vfs.exists(path)) return AppSettings()
        return runCatching {
            AppSettings.fromJson(Json.readObject(String(vfs.read(path), Charsets.UTF_8)))
        }.getOrElse { AppSettings() }
    }

    private fun writeAppSettings(settings: AppSettings) {
        runCatching {
            vfs.mkdirs(layout.root())
            vfs.write(layout.settingsPath(),
                Json.write(settings.toJson()).toByteArray(Charsets.UTF_8))
        }
    }

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

    /**
     * The slots one game has: the emulator's own at zero, then the player's.
     *
     * Kept apart on purpose — quitting a game writes slot zero, and must not
     * overwrite the place someone deliberately saved before a boss.
     */
    fun saveSlots(suiteId: String): List<SaveSlot> =
        (0..StorageLayout.SLOTS).map { slot ->
            SaveSlot(
                slot = slot,
                savedAt = library.saveStateTime(suiteId, slot),
                used = library.hasSaveState(suiteId, slot),
            )
        }

    fun writeSaveState(suiteId: String, slot: Int, state: ByteArray, screenshot: ByteArray?) {
        library.writeSaveState(suiteId, slot, state, screenshot)
        refresh()
    }

    fun readSaveState(suiteId: String, slot: Int): ByteArray? =
        library.readSaveState(suiteId, slot)

    fun saveStateThumbnail(suiteId: String, slot: Int): ByteArray? =
        library.saveStateThumbnail(suiteId, slot)

    fun deleteSaveState(suiteId: String, slot: Int) {
        library.deleteSaveState(suiteId, slot)
        refresh()
    }

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

    /**
     * Turns a game's screen and remembers it.
     *
     * Auto-setup already turns a game written for a wide screen; this is for
     * the ones that drew sideways on a portrait handset and expected the
     * player to turn the phone themselves.
     */
    fun toggleOrientation(suiteId: String): Int {
        val profile = library.profile(suiteId) ?: return DeviceProfile.ORIENTATION_PORTRAIT
        val turned = if (profile.orientation() == DeviceProfile.ORIENTATION_LANDSCAPE) {
            DeviceProfile.ORIENTATION_PORTRAIT
        } else {
            DeviceProfile.ORIENTATION_LANDSCAPE
        }
        profile.setOrientation(turned)
        library.saveProfile(profile)
        refresh()
        return turned
    }

    /**
     * Which keys the keypad shows, cycled the way J2ME Loader's "switch
     * layout" does it: full, arrows only, numbers only, hidden. A player
     * works out mid-game that a game only reads the pad, and dropping the
     * numbers hands that space straight back to the game.
     */
    fun cycleKeypadLayout(suiteId: String): String {
        val profile = library.profile(suiteId) ?: return ""
        profile.setKeypadLayout((profile.keypadLayout() + 1) % 4)
        library.saveProfile(profile)
        refresh()
        return profile.keypadLayoutName()
    }

    /** Keeps a picture of the game; returns where it went. */
    fun writeScreenshot(suiteId: String, png: ByteArray): String =
        library.writeScreenshot(suiteId, png)

    /**
     * The one game to offer on the way in, or null when there is none.
     *
     * Opening the app to play the game you were just playing is the most
     * common thing anyone does with it, and without this it costs three taps.
     *
     * @return the game, and whether pressing it would carry on or start again
     */
    fun continueCard(): Pair<LibraryEntry, Boolean>? {
        val profiles = library.allProfiles()
        val latest = library.all()
            .mapNotNull { entry -> profiles[entry.suiteId()]?.let { entry to it } }
            .filter { it.second.lastPlayed() > 0 }
            .maxByOrNull { it.second.lastPlayed() }
            ?: return null
        // Carrying on and starting again are not the same thing, and a player
        // offered "continue" who gets a fresh start has lost what they came
        // back for.
        return latest.first to (library.readSaveState(latest.first.suiteId(), 0) != null)
    }

    // ------------------------------------------------------------ collections

    /**
     * Shelves the player puts their games on.
     *
     * Search finds a game whose name is remembered; a library of eighty games
     * is mostly games whose names are not. A shelf is how a person finds
     * those — by having put them somewhere themselves.
     */
    private val shelves by lazy { CollectionStore(library.storage(), library.layout()) }

    private val _collections = MutableStateFlow<List<String>>(emptyList())
    val collections: StateFlow<List<String>> = _collections

    /** Which shelf the library is filtered to, or empty for all of them. */
    private val _shelf = MutableStateFlow("")
    val shelf: StateFlow<String> = _shelf

    fun setShelf(name: String) {
        _shelf.value = name
    }

    fun createCollection(name: String): Boolean {
        val made = shelves.create(name)
        refreshCollections()
        return made
    }

    fun toggleCollection(name: String, suiteId: String): Boolean {
        val added = shelves.toggle(name, suiteId)
        refreshCollections()
        return added
    }

    fun deleteCollection(name: String) {
        shelves.delete(name)
        if (_shelf.value == name) {
            _shelf.value = ""
        }
        refreshCollections()
    }

    fun shelvesOf(suiteId: String): List<String> = shelves.shelvesOf(suiteId)

    /** The games on one shelf, still in library order. */
    fun onShelf(name: String, games: List<LibraryEntry>): List<LibraryEntry> {
        if (name.isEmpty()) {
            return games
        }
        val ids = shelves.gamesOn(name).toSet()
        return games.filter { ids.contains(it.suiteId()) }
    }

    private fun refreshCollections() {
        _collections.value = shelves.names()
    }

    /**
     * Installs a game from a link.
     *
     * These games arrive as a link before they arrive as a file. Fetching one
     * in a browser, finding it in Downloads and picking it out of a file
     * chooser is three steps for something this does in one.
     *
     * @return what happened, in words to show the player
     */
    fun installFromUrl(url: String): String = runCatching {
        val download = UrlInstaller.fetch({ target ->
            val request = NetworkTransport.Request(target)
            // A handset asked for exactly this, and a few servers of the era
            // still refuse anything that does not.
            request.headers["Accept"] =
                "text/vnd.sun.j2me.app-descriptor, application/java-archive, */*"
            downloads.perform(request)
        }, url)
        library.setClock(System.currentTimeMillis())
        val result = library.install(download.jar(), download.jad())
        refresh()
        "Đã cài ${result.entry().title()}"
    }.getOrElse { "Không tải được: ${it.message}" }

    /**
     * Where downloads go through.
     *
     * Separate from a game's network: a game has to ask before it connects,
     * and a download the player asked for by typing an address does not. It
     * still goes through a policy and a monitor, so the same rules about what
     * is recorded apply.
     */
    private val downloads: NetworkStack by lazy {
        val policy = NetworkPolicy()
        policy.setMode(GameProfile.NETWORK_ALLOWED)
        NetworkStack(policy).also { it.setTransport(HttpTransport()) }
    }

    /**
     * The files a game has written for itself through JSR-75.
     *
     * They are the player's — a saved level, a downloaded track — so they are
     * visible and removable rather than hidden inside the app.
     */
    fun gameFiles(suiteId: String): List<Pair<String, Long>> {
        val base = StorageLayout.join(library.layout().gameDir(suiteId), "files")
        val found = mutableListOf<Pair<String, Long>>()
        fun walk(prefix: String) {
            val dir = if (prefix.isEmpty()) base else StorageLayout.join(base, prefix)
            for (name in library.storage().list(dir)) {
                val relative = if (prefix.isEmpty()) name else "$prefix/$name"
                val path = StorageLayout.join(dir, name)
                if (library.storage().isDirectory(path)) {
                    walk(relative)
                } else {
                    found += relative to library.storage().size(path)
                }
            }
        }
        walk("")
        return found
    }

    /** Throws away one of a game's own files. */
    fun deleteGameFile(suiteId: String, path: String): Boolean {
        val base = StorageLayout.join(library.layout().gameDir(suiteId), "files")
        val full = MidpFiles.resolveOrNull(base, path) ?: return false
        return library.storage().delete(full)
    }

    /**
     * Gets one picture or clip ready to leave the app.
     *
     * Inside the app it is called `1700000000000.png`, which is the right name
     * for a file the app itself reads and says nothing at all in a chat. So a
     * copy is made under a readable name, and it is the copy that goes.
     *
     * @return the copy's path and what to call it, or null when there is
     *     nothing to send
     */
    fun prepareShare(suiteId: String, name: String): Pair<String, String>? {
        val bytes = library.readScreenshot(suiteId, name) ?: return null
        if (bytes.isEmpty()) {
            return null
        }
        val title = library.find(suiteId)?.title() ?: "MobiCore"
        val share = ShareExport(library.storage(), library.layout())
        return runCatching {
            share.prepare(title, name, bytes) to ShareExport.mimeOf(name)
        }.getOrNull()
    }

    /** Keeps a recorded clip beside the pictures; returns where it went. */
    fun writeClip(suiteId: String, gif: ByteArray): String =
        library.writeClip(suiteId, gif)

    /** Every picture taken of one game, newest first. */
    fun screenshots(suiteId: String): List<String> =
        library.screenshotsFor(suiteId).reversed()

    fun readScreenshot(suiteId: String, name: String): ByteArray? =
        library.readScreenshot(suiteId, name)

    fun deleteScreenshot(suiteId: String, name: String): Boolean =
        library.deleteScreenshot(suiteId, name)

    // ---------------------------------------------------------- presets

    private val _presets = MutableStateFlow<List<String>>(emptyList())

    /** Named settings, saved once and applied to any game. */
    val presets: StateFlow<List<String>> = _presets.asStateFlow()

    private val _defaultPreset = MutableStateFlow("")
    val defaultPreset: StateFlow<String> = _defaultPreset.asStateFlow()

    private fun refreshPresets() {
        _presets.value = presetStore.names()
        _defaultPreset.value = appSettings().defaultPreset()
    }

    fun savePreset(name: String, suiteId: String) {
        val profile = library.profile(suiteId) ?: return
        presetStore.save(name, profile)
        refreshPresets()
    }

    /**
     * Puts a saved preset's settings onto a game, keeping everything that is
     * about the game itself: which suite it is, when it was last played,
     * whether it is a favourite.
     */
    fun applyPreset(name: String, suiteId: String) {
        val profile = library.profile(suiteId) ?: return
        val applied = presetStore.apply(name, profile) ?: return
        library.saveProfile(applied)
        refresh()
    }

    fun deletePreset(name: String) {
        presetStore.delete(name)
        if (_defaultPreset.value == name) {
            setDefaultPreset("")
        }
        refreshPresets()
    }

    /** Which preset a newly imported game starts from; empty for none. */
    fun setDefaultPreset(name: String) {
        val settings = appSettings()
        settings.setDefaultPreset(name)
        writeAppSettings(settings)
        _defaultPreset.value = settings.defaultPreset()
    }

    /**
     * Puts the default preset on games that nobody has configured by hand.
     *
     * Called after an import: a preset applied then must not undo a setting
     * someone deliberately changed on a game they already had.
     */
    private fun applyDefaultPreset() {
        val name = _defaultPreset.value
        if (name.isEmpty()) return
        library.all().forEach { entry ->
            val profile = library.profile(entry.suiteId())
            if (profile != null && profile.isAuto) {
                presetStore.apply(name, profile)?.let { library.saveProfile(it) }
            }
        }
    }

    /**
     * Auto-repeat for one button, in milliseconds between presses; 0 is off.
     *
     * Half the shooters of the era expect a thumb hammering the keypad, and a
     * held key is not that input: a game reading `keyPressed` sees one press
     * however long it is held.
     */
    /**
     * Points one virtual button at a different key code.
     *
     * The presets are a guess; when the guess is wrong the game simply does
     * not respond, which reads as a broken emulator rather than a wrong key.
     */
    /**
     * The whole library as one file, and back again.
     *
     * Per-game backups are the wrong shape for changing phones: eighty games
     * means eighty transfers, and whoever is doing that at eleven at night
     * gets to game sixty and gives up.
     */
    fun exportLibrary(): ByteArray = LibraryArchive.export(vfs, layout)

    fun importLibrary(archive: ByteArray): LibraryArchive.Report {
        val report = LibraryArchive.restore(vfs, layout, archive)
        // Everything held in memory came from the files just written over.
        library.open()
        refresh()
        refreshPresets()
        return report
    }

    /**
     * Every MIDlet inside one suite.
     *
     * A JAR often holds more than one — the game, a help screen, sometimes a
     * second game — and the play button should open the one the player thinks
     * of as the game.
     */
    fun midlets(suiteId: String): List<MidletEntry> =
        runCatching { load(suiteId).info().midlets() }.getOrDefault(emptyList())

    fun chosenMidlet(suiteId: String): String = library.profile(suiteId)?.midletClass() ?: ""

    fun setMidlet(suiteId: String, className: String) {
        val profile = library.profile(suiteId) ?: return
        profile.setMidletClass(className)
        library.saveProfile(profile)
        refresh()
    }

    fun setKeyMapping(suiteId: String, button: String, keyCode: Int) {
        val profile = library.profile(suiteId) ?: return
        profile.input().setMapping(button, keyCode)
        library.saveProfile(profile)
        refresh()
    }

    /** Points one control on a real pad at an emulator button. */
    fun setPadMapping(suiteId: String, pad: String, button: String) {
        val profile = library.profile(suiteId) ?: return
        profile.gamepad().map(pad, button)
        library.saveProfile(profile)
        refresh()
    }

    /** Whether controller input reaches the game at all. */
    fun setGamepadEnabled(suiteId: String, enabled: Boolean) {
        val profile = library.profile(suiteId) ?: return
        profile.gamepad().setEnabled(enabled)
        library.saveProfile(profile)
        refresh()
    }

    /** Puts the pad back to the arrangement a J2ME game expects. */
    fun resetGamepad(suiteId: String) {
        val profile = library.profile(suiteId) ?: return
        val fresh = GamepadProfile.defaults()
        fresh.setEnabled(profile.gamepad().isEnabled)
        profile.setGamepad(fresh)
        library.saveProfile(profile)
        refresh()
    }

    fun setTurbo(suiteId: String, button: String, intervalMs: Int) {
        val profile = library.profile(suiteId) ?: return
        profile.input().setTurbo(button, intervalMs)
        library.saveProfile(profile)
        refresh()
    }

    fun toggleFavourite(suiteId: String) {
        val profile = library.profile(suiteId) ?: return
        profile.isFavourite = !profile.isFavourite
        library.saveProfile(profile)
        refresh()
    }
}
