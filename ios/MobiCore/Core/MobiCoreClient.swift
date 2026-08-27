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

    func setDevice(_ deviceId: String, for suiteId: String) {
        report(decode(bridge.setDeviceProfile(deviceId, forSuite: suiteId)))
        refresh()
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

    func deleteScreenshot(_ suiteId: String, named name: String) {
        report(decode(bridge.deleteScreenshot(forSuite: suiteId, named: name)))
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
