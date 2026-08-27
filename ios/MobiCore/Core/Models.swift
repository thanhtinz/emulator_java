import Foundation

/// One installed suite, as `MobiCoreFacade.libraryJson` reports it.
struct Game: Codable, Identifiable, Hashable {
    let suiteId: String
    let title: String
    /// The title the suite's own manifest declares. Kept when a game is
    /// renamed, so the original name can always be offered back.
    let originalTitle: String
    let renamed: Bool
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
    var keypadLayout: Int
    /// Which MIDlet inside the suite the play button opens; empty for the first.
    var midletClass: String
    /// Total time in this game, and the words for it.
    var playedMs: Int64?
    var playedName: String?
    var frameLimit: Int
    var volume: Int
    var muted: Bool
    var showFps: Bool
    /// Whether the phone may buzz for this game.
    var vibration: Bool
    var keepAspect: Bool
    var smoothing: Bool
    var networkMode: Int
    var skin: String
    /// True while every setting is still the one the import worked out.
    var auto: Bool
    /// 0 runs, 1 runs with something emulated incompletely, 2 will not start.
    var compatibility: Int
    /// Why the emulator set the game up this way, in the user's language.
    var setupNotes: [String]
    var favourite: Bool
    var lastPlayed: Int64
    var playCount: Int


    static let scaleModeNames = ["Vừa khung", "Bội số nguyên", "Kéo đầy", "Nguyên cỡ"]
    static let networkModeNames = ["Chặn", "Hỏi trước", "Cho phép"]

    /// What the in-game menu shows beside "Bàn phím".
    var keypadLayoutName: String {
        switch keypadLayout {
        case 1: return "Chỉ phím hướng"
        case 2: return "Chỉ phím số"
        case 3: return "Ẩn bàn phím"
        default: return "Đầy đủ"
        }
    }

    var showsArrows: Bool { keypadLayout == 0 || keypadLayout == 1 }
    var showsNumbers: Bool { keypadLayout == 0 || keypadLayout == 2 }

    var scaleModeName: String { Self.scaleModeNames[safe: scaleMode] ?? "Bội số nguyên" }
    var networkModeName: String { Self.networkModeNames[safe: networkMode] ?? "Hỏi trước" }

    /// Virtual buttons in keypad order, with the names the interface shows.
    static let buttonLabels: [(button: String, label: String)] = [
        ("up", "Lên"), ("down", "Xuống"), ("left", "Trái"), ("right", "Phải"),
        ("fire", "Chọn"), ("softLeft", "Phím mềm L"), ("softRight", "Phím mềm R"),
    ]
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

/// What `MobiCoreFacade.searchJson` reports: just the matches, in order.
struct SearchResponse: Codable {
    let games: [Game]
    let query: String?
    let sort: Int?
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


/// What `MobiCoreFacade.appSettingsJson` reports.
struct AppSettingsPayload: Codable {
    let theme: Int
    let themeName: String?
    let librarySort: Int?
    let confirmBeforeDeleting: Bool?
}


/// What `MobiCoreFacade.importMany` reports: a line for the user, and a row
/// per file so nothing fails silently.
struct BatchImportResponse: Codable {
    struct FileOutcome: Codable {
        let name: String
        /// 0 installed, 1 replaced, 2 failed, 3 skipped.
        let status: Int
        let detail: String?
    }

    let installed: Int
    let failed: Int
    let skipped: Int
    let summary: String
    let files: [FileOutcome]
}

/// One picture taken of a game, as the gallery lists it.
struct Screenshot: Codable, Identifiable, Hashable {
    let name: String
    let takenAt: Int64
    let bytes: Int

    var id: String { name }
}

struct ScreenshotsResponse: Codable {
    let screenshots: [Screenshot]
}

/// Named settings, saved once and applied to any game.
struct PresetsResponse: Codable {
    let presets: [String]
    let defaultPreset: String
}

/// One save slot: the emulator's own at zero, then the player's.
struct SaveSlot: Codable, Identifiable, Hashable {
    let slot: Int
    let auto: Bool
    let used: Bool
    let savedAt: Int64
    let thumbnail: Bool

    var id: Int { slot }
}

struct SaveSlotsResponse: Codable {
    let slots: [SaveSlot]
}

/// One key a virtual button can be pointed at.
struct KeyChoice: Codable, Identifiable, Hashable {
    let keyCode: Int
    let keyName: String

    var id: Int { keyCode }
}

struct KeyChoicesResponse: Codable {
    let keys: [KeyChoice]
}

/// What restoring a whole-library backup reports.
struct LibraryRestoreResponse: Codable {
    let ok: Bool
    let error: String?
    let files: Int?
    let games: Int?
    let summary: String?
}

/// One MIDlet inside a suite: a JAR often holds the game plus a help screen.
struct MidletChoice: Codable, Identifiable, Hashable {
    let name: String
    let className: String
    let icon: String
    let chosen: Bool

    var id: String { className }
}

struct MidletsResponse: Codable {
    let midlets: [MidletChoice]
}
