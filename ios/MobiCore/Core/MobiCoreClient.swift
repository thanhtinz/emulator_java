import Foundation
import SwiftUI

/// Swift face of the emulator.
///
/// Decodes the JSON the bridge returns and republishes it as observable state,
/// so views never parse anything themselves.
@MainActor
final class MobiCoreClient: ObservableObject {

    @Published private(set) var games: [Game] = []
    @Published private(set) var recent: [Game] = []
    @Published private(set) var favourites: [Game] = []
    @Published private(set) var lastError: String?
    /// The last thing worth telling the user that was not an error — the
    /// summary of an import, for one.
    @Published private(set) var lastMessage: String?
    /// Bumped whenever a cover changes, so views holding a decoded image
    /// know to ask for it again — the bytes change while the id does not.
    @Published private(set) var artworkRevision: Int = 0

    private let bridge = MobiCoreBridge.shared
    private let decoder = JSONDecoder()

    init() {
        let root = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)
            .first?.path ?? NSTemporaryDirectory()
        _ = bridge.open(atPath: root)
        refresh()
    }

    var storageRoot: String { bridge.storageRoot }

    func refresh() {
        guard let response: LibraryResponse = decode(bridge.libraryJSON()) else { return }
        games = response.games
        let byId = Dictionary(uniqueKeysWithValues: response.games.map { ($0.suiteId, $0) })
        recent = response.recent.compactMap { byId[$0] }
        favourites = response.favourites.compactMap { byId[$0] }
        refreshPresets()
    }

    func game(_ suiteId: String) -> Game? {
        games.first { $0.suiteId == suiteId }
    }

    func artwork(_ suiteId: String) -> Image? {
        guard let data = bridge.artwork(forSuite: suiteId),
              let image = UIImage(data: data) else { return nil }
        return Image(uiImage: image)
    }

    // MARK: - Library

    @discardableResult
    func importSuite(jar: Data, descriptor: Data?) -> Bool {
        let result: ActionResult? = decode(bridge.importJar(jar, descriptor: descriptor))
        report(result)
        refresh()
        return result?.ok ?? false
    }

    /// Imports everything picked at once, and reports what went in.
    @discardableResult
    func importMany(names: [String], payloads: [Data]) -> String {
        let response: BatchImportResponse? =
            decode(bridge.importMany(names, payloads: payloads))
        refresh()
        if let summary = response?.summary {
            lastMessage = summary
            return summary
        }
        return ""
    }

    func uninstall(_ suiteId: String, keepData: Bool = false) {
        report(decode(bridge.uninstallSuite(suiteId, keepData: keepData)))
        refresh()
    }

    /// Renames a game as the library lists it. The suite keeps its own title,
    /// so the change can be undone and a reinstall still matches.
    @discardableResult
    func rename(_ suiteId: String, to title: String) -> Bool {
        let result: ActionResult? = decode(bridge.renameSuite(suiteId, title: title))
        report(result)
        refresh()
        return result?.ok ?? false
    }

    // MARK: - Searching

    /// The library's own sort order, remembered between sessions.
    @Published private(set) var librarySort: Int = 0

    /// Filtering and ordering happen in the core: the same query gives the
    /// same list here as on Android, marks and renamed games included.
    func search(_ query: String, sort: Int) -> [Game] {
        let payload: SearchResponse? = decode(bridge.searchJSON(query, sort: Int32(sort)))
        return payload?.games ?? []
    }

    /// The whole library in the chosen order. An empty query is what the core
    /// treats as "everything", so ordering goes through the same path as a
    /// search and cannot drift from it.
    func sorted(_ games: [Game], by sort: Int) -> [Game] {
        let ordered = search("", sort: sort)
        return ordered.isEmpty ? games : ordered
    }

    func setLibrarySort(_ sort: Int) {
        report(decode(bridge.setLibrarySort(Int32(sort))))
        librarySort = sort
    }

    // MARK: - Appearance

    /// Light, dark or follow the phone; see `AppSettings` in the core.
    @Published private(set) var theme: Int = ThemeChoice.light {
        didSet { applyTheme() }
    }

    func setTheme(_ choice: Int) {
        report(decode(bridge.setTheme(Int32(choice))))
        theme = choice
    }

    /// Light to dark to system and back, for the switch on the home screen.
    func cycleTheme() {
        let response: AppSettingsPayload? = decode(bridge.cycleTheme())
        theme = response?.theme ?? ThemeChoice.light
    }

    func loadTheme() {
        let stored: AppSettingsPayload? = decode(bridge.appSettingsJSON())
        theme = stored?.theme ?? ThemeChoice.light
        librarySort = stored?.librarySort ?? 0
    }

    /// Resolves "follow the phone" and hands the answer to the palette and to
    /// the emulated handset's own chrome, which must not stay dark on a light
    /// screen.
    private func applyTheme() {
        let dark: Bool
        switch theme {
        case ThemeChoice.dark: dark = true
        case ThemeChoice.system:
            dark = UITraitCollection.current.userInterfaceStyle == .dark
        default: dark = false
        }
        Palette.dark = dark
        bridge.setChromeDark(dark)
    }

    // MARK: - Save states

    /// True when the player has a game to come back to.
    func hasSaveState(_ suiteId: String) -> Bool {
        bridge.hasSaveState(forSuite: suiteId)
    }

    /// The screen the player left, for the resume card.
    func saveStateThumbnail(_ suiteId: String) -> Image? {
        guard let data = bridge.saveStateThumbnail(forSuite: suiteId),
              let image = UIImage(data: data) else {
            return nil
        }
        return Image(uiImage: image)
    }

    /// Saves where the player is now, without leaving the game.
    func saveState() {
        report(decode(bridge.saveState()))
    }

    /// Four slots the player writes by hand, plus the automatic one written
    /// when a game is left. Kept apart: quitting must not overwrite the place
    /// someone saved deliberately.
    func saveState(slot: Int) {
        report(decode(bridge.saveState(inSlot: Int32(slot))))
    }

    func loadState(slot: Int) {
        report(decode(bridge.loadState(fromSlot: Int32(slot))))
    }

    func saveSlots(_ suiteId: String) -> [SaveSlot] {
        let payload: SaveSlotsResponse? = decode(bridge.saveStatesJSON(forSuite: suiteId))
        return payload?.slots ?? []
    }

    func saveSlotThumbnail(_ suiteId: String, slot: Int) -> Image? {
        guard let data = bridge.saveStateThumbnail(forSuite: suiteId, slot: Int32(slot)),
              let image = UIImage(data: data) else {
            return nil
        }
        return Image(uiImage: image)
    }

    func deleteSaveState(_ suiteId: String, slot: Int) {
        report(decode(bridge.deleteSaveState(forSuite: suiteId, slot: Int32(slot))))
    }

    func deleteSaveState(_ suiteId: String) {
        report(decode(bridge.deleteSaveState(forSuite: suiteId)))
        refresh()
    }

    /// Configures a game from the game again, discarding hand-set values.
    func autoSetup(_ suiteId: String) {
        report(decode(bridge.autoSetup(forSuite: suiteId)))
        refresh()
    }

    func resetTitle(_ suiteId: String) {
        report(decode(bridge.resetTitle(forSuite: suiteId)))
        refresh()
    }

    /// Replaces the cover. The picture must already be PNG — see
    /// `Artwork.png(from:)`, which is where a picked photo is converted.
    @discardableResult
    func setArtwork(_ png: Data, for suiteId: String) -> Bool {
        let result: ActionResult? = decode(bridge.setArtwork(png, forSuite: suiteId))
        report(result)
        artworkRevision &+= 1
        refresh()
        return result?.ok ?? false
    }

    func resetArtwork(_ suiteId: String) {
        report(decode(bridge.resetArtwork(forSuite: suiteId)))
        artworkRevision &+= 1
        refresh()
    }

    func toggleFavourite(_ suiteId: String) {
        report(decode(bridge.toggleFavourite(forSuite: suiteId)))
        refresh()
    }

    // MARK: - Profiles

    func settings(_ suiteId: String) -> GameSettings? {
        decode(bridge.profileJSON(forSuite: suiteId))
    }


    /// Turns a game's screen and remembers it. Auto-setup already turns a
    /// game written for a wide screen; this is for the ones that drew
    /// sideways on a portrait handset and expected the player to turn it.
    func toggleOrientation(_ suiteId: String) {
        report(decode(bridge.toggleOrientation(forSuite: suiteId)))
        refresh()
    }

    /// Which keys the keypad shows, cycled the way J2ME Loader's "switch
    /// layout" does it: full, arrows only, numbers only, hidden.
    func cycleKeypadLayout(_ suiteId: String) {
        report(decode(bridge.cycleKeypadLayout(forSuite: suiteId)))
        refresh()
    }

    // MARK: - Presets

    /// Named settings, saved once and applied to any game.
    @Published private(set) var presets: [String] = []
    /// Which preset a newly imported game starts from; empty for none.
    @Published private(set) var defaultPreset = ""

    func refreshPresets() {
        let payload: PresetsResponse? = decode(bridge.presetsJSON())
        presets = payload?.presets ?? []
        defaultPreset = payload?.defaultPreset ?? ""
    }

    func savePreset(_ name: String, from suiteId: String) {
        report(decode(bridge.savePreset(name, fromSuite: suiteId)))
        refreshPresets()
    }

    func applyPreset(_ name: String, to suiteId: String) {
        report(decode(bridge.applyPreset(name, toSuite: suiteId)))
        refresh()
    }

    func deletePreset(_ name: String) {
        report(decode(bridge.deletePreset(name)))
        refreshPresets()
    }

    func setDefaultPreset(_ name: String) {
        report(decode(bridge.setDefaultPreset(name)))
        refreshPresets()
    }

    /// Keeps a picture of what the running game is showing.
    func takeScreenshot() {
        report(decode(bridge.takeScreenshot()))
    }

    /// Starts recording the screen as an animation.
    ///
    /// A picture says where the player got to; a clip says how, and a GIF
    /// plays wherever a picture plays.
    func startRecording() {
        report(decode(bridge.startRecording()))
    }

    /// Ends the recording and saves it beside the screenshots.
    func stopRecording() {
        report(decode(bridge.stopRecording()))
    }

    /// Whether a clip is being recorded, and how long it has got.
    func recording() -> Recording? {
        decode(bridge.recordingJSON())
    }

    /// Every picture taken of one game, newest first. A screenshot nothing
    /// can show again is a dead end.
    func screenshots(_ suiteId: String) -> [Screenshot] {
        let payload: ScreenshotsResponse? = decode(bridge.screenshotsJSON(forSuite: suiteId))
        return payload?.screenshots ?? []
    }

    func screenshotImage(_ suiteId: String, named name: String) -> Image? {
        guard let data = bridge.screenshot(forSuite: suiteId, named: name),
              let image = UIImage(data: data) else {
            return nil
        }
        return Image(uiImage: image)
    }

    /// Gets one picture or clip ready to send.
    ///
    /// Inside the app it is called `1700000000000.png` — the right name for a
    /// file the app itself reads, and one that says nothing in a chat. So a
    /// copy is made under a readable name, and it is the copy that goes.
    func prepareShare(_ suiteId: String, named name: String) -> URL? {
        let shared: SharedFile? = decode(bridge.shareScreenshot(name, forSuite: suiteId))
        guard let path = shared?.path else {
            return nil
        }
        return URL(fileURLWithPath: path)
    }

    func deleteScreenshot(_ suiteId: String, named name: String) {
        report(decode(bridge.deleteScreenshot(forSuite: suiteId, named: name)))
    }

    /// Every MIDlet inside one suite. Shown only when there is more than one:
    /// a picker over a list of one is a question with a single answer.
    func midlets(_ suiteId: String) -> [MidletChoice] {
        let payload: MidletsResponse? = decode(bridge.midletsJSON(forSuite: suiteId))
        return payload?.midlets ?? []
    }

    /// Remembers which one the play button should open.
    func chooseMidlet(_ className: String, for suiteId: String) {
        guard var settings = settings(suiteId) else { return }
        settings.midletClass = className
        update(settings)
    }

    // MARK: - Whole-library backup

    /// One file with everything in it, to carry to the next phone. Per-game
    /// backups are the wrong shape for that: eighty games would mean eighty
    /// transfers.
    func exportLibrary() -> Data? {
        bridge.exportLibrary()
    }

    @discardableResult
    func importLibrary(_ archive: Data) -> String {
        let response: LibraryRestoreResponse? = decode(bridge.importLibrary(archive))
        refresh()
        refreshPresets()
        return response?.summary ?? response?.error ?? "Khôi phục thất bại"
    }

    /// Points one virtual button at a different key code. The presets are a
    /// guess; when the guess is wrong the game simply does not respond, which
    /// reads as a broken emulator rather than a wrong key.
    func setKeyMapping(_ keyCode: Int, button: String, for suiteId: String) {
        report(decode(bridge.setKeyMapping(Int32(keyCode), forButton: button, suite: suiteId)))
        refresh()
    }

    /// Only codes a MIDlet of the era might read: a free-text number box
    /// would let someone map a button to a code no handset ever sent.
    func keyChoices() -> [KeyChoice] {
        let payload: KeyChoicesResponse? = decode(bridge.keyChoicesJSON())
        return payload?.keys ?? []
    }

    /// Auto-repeat for one button, in milliseconds between presses; 0 is off.
    /// A held key is not the same input: a game reading `keyPressed` sees one
    /// press however long it is held.
    func setTurbo(_ intervalMs: Int, button: String, for suiteId: String) {
        report(decode(bridge.setTurbo(Int32(intervalMs), forButton: button, suite: suiteId)))
        refresh()
    }

    // ------------------------------------------------------------ collections

    /// Shelves the player puts their games on.
    ///
    /// Search finds a game whose name is remembered; a library of eighty games
    /// is mostly games whose names are not.
    ///
    /// - Parameter suiteId: a game to report membership for, or empty.
    func collections(for suiteId: String = "") -> [Collection] {
        let payload: CollectionsResponse? = decode(bridge.collectionsJSON(forSuite: suiteId))
        return payload?.collections ?? []
    }

    func createCollection(_ name: String) {
        report(decode(bridge.createCollection(name)))
    }

    func toggleCollection(_ name: String, for suiteId: String) {
        report(decode(bridge.toggleCollection(name, forSuite: suiteId)))
    }

    func deleteCollection(_ name: String) {
        report(decode(bridge.deleteCollection(name)))
    }

    /// The games on one shelf, out of the library already in hand.
    ///
    /// Filtered here rather than fetched again: the listing is the same list
    /// of games, and asking for it twice would let the two drift apart.
    func gamesOn(_ name: String) -> [Game] {
        if name.isEmpty {
            return games
        }
        let payload: CollectionGames? = decode(bridge.collectionJSON(name))
        let ids = Set((payload?.games ?? []).map { $0.suiteId })
        return games.filter { ids.contains($0.suiteId) }
    }

    // ------------------------------------------------------------- from a link

    /// Installs a game from a link.
    ///
    /// These games arrive as a link before they arrive as a file. Fetching one
    /// in Safari, finding it in Files and picking it out of a document picker
    /// is three steps for something this does in one.
    ///
    /// - Returns: what happened, in words to show the player.
    func installFromUrl(_ url: String) -> String {
        guard let data = bridge.installFromURL(url).data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return "Không tải được"
        }
        refresh()
        if json["ok"] as? Bool != true {
            return json["error"] as? String ?? "Không tải được"
        }
        let game = json["game"] as? [String: Any]
        return "Đã cài \(game?["title"] as? String ?? "trò chơi")"
    }

    // ------------------------------------------------------ the game's files

    /// The files a game has written for itself through JSR-75.
    ///
    /// They are the player's — a saved level, a downloaded track — so they
    /// are visible and removable rather than hidden inside the app.
    func gameFiles(_ suiteId: String) -> [GameFile] {
        let payload: GameFilesResponse? = decode(bridge.gameFilesJSON(forSuite: suiteId))
        return payload?.files ?? []
    }

    func deleteGameFile(_ path: String, for suiteId: String) {
        report(decode(bridge.deleteGameFile(path, forSuite: suiteId)))
    }

    // ------------------------------------------------------------ carrying on

    /// The one game to offer on the way in, or nil when there is none.
    ///
    /// Opening the app to play the game you were just playing is the most
    /// common thing anyone does with it, and without this it costs three taps.
    func continueCard() -> ContinueCard? {
        let card: ContinueCard? = decode(bridge.continueJSON())
        return card?.has == true ? card : nil
    }

    // -------------------------------------------------------------- tilting

    /// Whether tilting the phone steers this game.
    ///
    /// No J2ME handset could do this, so it is not emulation but a way to
    /// play: it suits a racing game steered left and right and suits nothing
    /// else, which is why it stays off until it is asked for.
    func tilt(_ suiteId: String) -> TiltSettings? {
        decode(bridge.tiltJSON(forSuite: suiteId))
    }

    func setTiltEnabled(_ enabled: Bool, for suiteId: String) {
        report(decode(bridge.setTiltEnabled(enabled, forSuite: suiteId)))
    }

    func setTiltSensitivity(_ percent: Int, for suiteId: String) {
        report(decode(bridge.setTiltSensitivity(percent, forSuite: suiteId)))
    }

    func setTiltAxes(_ axes: Int, for suiteId: String) {
        report(decode(bridge.setTiltAxes(axes, forSuite: suiteId)))
    }

    func setTiltInverted(_ inverted: Bool, for suiteId: String) {
        report(decode(bridge.setTiltInverted(inverted, forSuite: suiteId)))
    }

    // ----------------------------------------------------------- máy ảo khai gì

    /// Những gì game đọc được khi nó hỏi máy nó đang chạy trên đó là máy gì.
    func systemProperties() -> SystemPropertyTable? {
        decode(bridge.systemPropertiesJSON())
    }

    // --------------------------------------------------------- the controller

    /// What a real controller's buttons do for one game.
    func gamepad(_ suiteId: String) -> GamepadSettings? {
        decode(bridge.gamepadJSON(forSuite: suiteId))
    }

    /// Points one control at an emulator button; an empty button unbinds it.
    func setPadMapping(_ pad: String, to button: String, for suiteId: String) {
        report(decode(bridge.setPadMapping(pad, toButton: button, forSuite: suiteId)))
    }

    /// Whether controller input reaches the game at all.
    func setGamepadEnabled(_ enabled: Bool, for suiteId: String) {
        report(decode(bridge.setGamepadEnabled(enabled, forSuite: suiteId)))
    }

    /// Puts the pad back to the arrangement a J2ME game expects.
    func resetGamepad(_ suiteId: String) {
        report(decode(bridge.resetGamepad(forSuite: suiteId)))
    }

    // ------------------------------------------------------ where the keys are

    /// Where the keys have been dragged to, and how big they are drawn.
    func keypadArrangement(_ suiteId: String) -> KeypadArrangement? {
        decode(bridge.keypadArrangementJSON(forSuite: suiteId))
    }

    /// Drags one key, in thousandths of a key from where the layout puts it.
    func moveKey(_ button: String, x: Int, y: Int, for suiteId: String) {
        report(decode(bridge.moveKey(button, toX: x, y: y, forSuite: suiteId)))
    }

    /// How big the keys are drawn, 60-160 percent of the standard size.
    func setKeyScale(_ percent: Int, for suiteId: String) {
        report(decode(bridge.setKeyScale(percent, forSuite: suiteId)))
    }

    /// Puts every key back where the standard layout has it.
    func resetKeypad(_ suiteId: String) {
        report(decode(bridge.resetKeypad(forSuite: suiteId)))
    }

    // ---------------------------------------------------- how the keypad looks

    /// How solid the keypad is drawn, 20-100 percent.
    func setKeypadOpacity(_ percent: Int, for suiteId: String) {
        report(decode(bridge.setKeypadOpacity(percent, forSuite: suiteId)))
        refresh()
    }

    /// Rounded, square or round; see `GameProfile.KEY_SHAPE_*` in the core.
    func setKeypadShape(_ shape: Int, for suiteId: String) {
        report(decode(bridge.setKeypadShape(shape, forSuite: suiteId)))
        refresh()
    }

    /// Seconds of not being touched before the keypad fades; 0 never fades.
    func setKeypadFadeDelay(_ seconds: Int, for suiteId: String) {
        report(decode(bridge.setKeypadFadeDelay(seconds, forSuite: suiteId)))
        refresh()
    }

    func setInputPreset(_ preset: String, for suiteId: String) {
        report(decode(bridge.setInputPreset(preset, forSuite: suiteId)))
        refresh()
    }

    func update(_ settings: GameSettings) {
        guard let data = try? JSONEncoder().encode(settings),
              let json = String(data: data, encoding: .utf8) else { return }
        report(decode(bridge.updateProfileJSON(json)))
        refresh()
    }

    // MARK: - Saves

    func saves(_ suiteId: String) -> SavesResponse? {
        decode(bridge.savesJSON(forSuite: suiteId))
    }

    func backup(_ suiteId: String) {
        report(decode(bridge.backupSuite(suiteId)))
    }

    func restoreLatest(_ suiteId: String) {
        report(decode(bridge.restoreLatest(forSuite: suiteId)))
    }

    func resetData(_ suiteId: String) {
        report(decode(bridge.resetData(forSuite: suiteId)))
    }

    // MARK: - Tools

    func inspect(_ suiteId: String) -> InspectResponse? {
        decode(bridge.inspectJSON(forSuite: suiteId))
    }

    func resource(_ path: String, in suiteId: String) -> Data? {
        bridge.resource(named: path, inSuite: suiteId)
    }

    // MARK: - Helpers

    private func decode<T: Decodable>(_ json: String) -> T? {
        guard let data = json.data(using: .utf8) else { return nil }
        if let result = try? decoder.decode(T.self, from: data) {
            return result
        }
        // Failures come back as {"ok": false, "error": "..."}, which will not
        // decode into the expected shape; surface the message instead.
        if let failure = try? decoder.decode(ActionResult.self, from: data), !failure.ok {
            lastError = failure.error
        }
        return nil
    }

    private func report(_ result: ActionResult?) {
        lastError = (result?.ok ?? false) ? nil : (result?.error ?? "Đã có lỗi xảy ra")
    }
}
