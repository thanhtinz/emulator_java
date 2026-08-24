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

    func uninstall(_ suiteId: String, keepData: Bool = false) {
        report(decode(bridge.uninstallSuite(suiteId, keepData: keepData)))
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
