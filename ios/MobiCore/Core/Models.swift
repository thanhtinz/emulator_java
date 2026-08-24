import Foundation

/// One installed suite, as `MobiCoreFacade.libraryJson` reports it.
struct Game: Codable, Identifiable, Hashable {
    let suiteId: String
    let title: String
    let vendor: String
    let version: String
    let configuration: String
    /// The MIDP profile string, e.g. `MIDP-2.0`.
    let profile: String
    let installedAt: Int64
    let jarSize: Int64
    let hasArtwork: Bool
    let stores: Int
    let settings: GameSettings?

    var id: String { suiteId }
}

/// The user-editable configuration for one game.
struct GameSettings: Codable, Hashable {
    var suiteId: String
    var device: DeviceProfile
    var input: InputSettings
    var scaleMode: Int
    var orientation: Int
    var frameLimit: Int
    var volume: Int
    var muted: Bool
    var showFps: Bool
    var keepAspect: Bool
    var networkMode: Int
    var skin: String
    var favourite: Bool
    var lastPlayed: Int64
    var playCount: Int

    /// Catalog of selectable devices; present only in `profileJSON`.
    var devices: [DeviceProfile]?

    static let scaleModeNames = ["Fit", "Integer", "Stretch", "Original"]
    static let networkModeNames = ["Blocked", "Ask", "Allowed"]

    var scaleModeName: String { Self.scaleModeNames[safe: scaleMode] ?? "Integer" }
    var networkModeName: String { Self.networkModeNames[safe: networkMode] ?? "Ask" }
}

struct DeviceProfile: Codable, Hashable, Identifiable {
    let id: String
    let name: String
    let width: Int
    let height: Int
    let keypad: Int
    let colorDepth: Int
    let touch: Bool

    var resolution: String { "\(width)x\(height)" }

    var keypadName: String {
        switch keypad {
        case 1: return "Sony Ericsson"
        case 2: return "Samsung"
        case 3: return "Motorola"
        default: return "Nokia"
        }
    }
}

struct InputSettings: Codable, Hashable {
    var preset: String
    var mappings: [String: Int]
    var turbo: [String: Int]?
}

struct LibraryResponse: Codable {
    let games: [Game]
    let recent: [String]
    let favourites: [String]
}

struct SavesResponse: Codable {
    struct Store: Codable, Identifiable, Hashable {
        let name: String
        let records: Int
        let bytes: Int
        let version: Int

        var id: String { name }
    }

    let stores: [Store]
    let backups: [String]
}

struct InspectResponse: Codable {
    struct Midlet: Codable, Identifiable, Hashable {
        let name: String
        let className: String
        let icon: String?

        var id: String { className }
    }

    struct Resource: Codable, Identifiable, Hashable {
        let name: String
        let bytes: Int

        var id: String { name }
    }

    let attributes: [String: String]
    let midlets: [Midlet]
    let classes: [String]
    let resources: [Resource]
    let uncompressed: Int64
}

/// Result envelope every mutating call returns.
struct ActionResult: Codable {
    let ok: Bool
    let error: String?
    let path: String?
    let restored: String?
    let backup: String?
    let favourite: String?
    let width: Int?
    let height: Int?
    let midlet: String?
}

extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
