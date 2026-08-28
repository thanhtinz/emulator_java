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
    /// How solid the keypad is drawn, 20-100 percent.
    var keypadOpacity: Int?
    /// Rounded, square or round; see `GameProfile.KEY_SHAPE_*` in the core.
    var keypadShape: Int?
    /// Seconds of not being touched before the keypad fades; 0 never fades.
    var keypadFadeDelay: Int?
    /// Which MIDlet inside the suite the play button opens; empty for the first.
    var midletClass: String
    /// Total time in this game, and the words for it.
    var playedMs: Int64?
    var playedName: String?
    var frameLimit: Int
    var volume: Int
    var muted: Bool
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

    /// The three keypad-look settings, with the defaults an older profile
    /// that predates them is read back with.
    var keyOpacity: Int { keypadOpacity ?? 100 }
    var keyShape: Int { keypadShape ?? 0 }
    var keyFadeDelay: Int { keypadFadeDelay ?? 0 }

    var keypadShapeName: String {
        switch keyShape {
        case 1: return "Vuông"
        case 2: return "Tròn"
        default: return "Bo góc"
        }
    }

    var keypadFadeDelayName: String {
        keyFadeDelay == 0 ? "Luôn rõ" : "Sau \(keyFadeDelay) giây"
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
    /// Sửa được mấy chỗ, khi đặt số mới vào phần lưu.
    let written: Int?
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
    /// True for a recorded clip, which shares the gallery with the pictures.
    let clip: Bool?

    var id: String { name }
    var isClip: Bool { clip ?? false }
}

/// The one game offered on the way in, if there is one.
struct ContinueCard: Codable {
    let has: Bool
    let game: Game?
    let suiteId: String?
    /// True when pressing it carries on; false when it starts again.
    let resumes: Bool?
    let action: String?
    let lastPlayed: Int64?
    let playedName: String?
}

/// Bảng vật phẩm của một game: những chỗ đã tìm ra và đặt tên.
struct ItemTable: Codable {
    let items: [GameItem]
    let count: Int
}

struct GameItem: Codable, Identifiable {
    let id: String
    let name: String
    let amount: Int64
    /// Số chỗ trong phần lưu đang giữ số lượng của nó.
    let places: Int
    /// Số lớn nhất còn nhét vừa chỗ game để dành cho nó.
    let ceiling: Int64
}

/// Kết quả một lần tìm số vàng trong phần lưu.
struct SaveScan: Codable {
    let summary: String
    let count: Int
    let hits: [SaveHit]
    /// True khi chỉ còn đúng một chỗ — không cần lọc nữa.
    let done: Bool
}

struct SaveHit: Codable, Identifiable {
    let store: String
    let recordId: Int
    let offset: Int
    let encodingName: String
    let value: Int64

    var id: String { "\(store)/\(recordId)/\(offset)/\(encodingName)" }
}

/// Mọi thứ nằm trong tệp game, để xem và để thay.
struct ResourceBox: Codable {
    let resources: [GameResource]
    let count: Int
    let images: Int
    let sounds: Int
    let bytes: Int64
    /// Những tệp chính người chơi đã thay.
    let replaced: [String]
}

struct GameResource: Codable, Identifiable {
    let path: String
    let kind: Int
    let kindName: String
    /// Đúng thứ nó là, đọc từ mấy byte đầu: PNG, JPEG, MIDI, WAV…
    let format: String
    let bytes: Int
    let width: Int
    let height: Int
    let replaced: Bool
    let replacedBy: String

    var id: String { path }
    var isImage: Bool { kind == 0 }
}

/// Những gì game đọc được khi nó hỏi máy nó đang chạy trên đó là máy gì.
///
/// Chỉ để đọc: máy ảo là một cỗ máy duy nhất và bảng này là của chung, không
/// có bản riêng cho từng game.
struct SystemPropertyTable: Codable {
    let platform: String
    let properties: [SystemProperty]
}

struct SystemProperty: Codable, Identifiable {
    let name: String
    let value: String

    var id: String { name }
}

/// Vì sao một game vừa chết, đã đọc thành lời.
///
/// Ba câu, đúng thứ tự người chơi hỏi: hỏng cái gì (`title`), vì sao
/// (`reason`), làm gì tiếp (`advice`). `technical` là nguyên văn ngoại lệ,
/// giữ nguyên tiếng Anh vì đó là phần để tra cứu chứ không phải để đọc.
struct CrashReading: Codable {
    let has: Bool
    let kind: Int?
    let title: String?
    let reason: String?
    let advice: String?
    let technical: String?
    let blamesGame: Bool?
    let game: String?
    let stack: [String]?
}

/// Whether tilting the phone steers one game, and how far it must lean.
struct TiltSettings: Codable {
    let enabled: Bool
    let sensitivity: Int
    let axes: Int
    let axesName: String
    let inverted: Bool
}

/// A picture or clip made ready to leave the app.
struct SharedFile: Codable {
    let path: String
    let name: String
    let mime: String
    let clip: Bool
}

/// One shelf the player has put games on.
struct Collection: Codable, Identifiable, Hashable {
    let name: String
    let games: Int
    /// True when the game this listing was asked about is on this shelf.
    let holds: Bool

    var id: String { name }
}

struct CollectionsResponse: Codable {
    let collections: [Collection]
}

/// Which games are on one shelf. Only the ids are read: the entries
/// themselves are already in hand, and reading them twice lets them drift.
struct CollectionGames: Codable {
    struct Row: Codable {
        let suiteId: String
    }

    let name: String
    let games: [Row]
}

/// One file a game wrote for itself through JSR-75.
struct GameFile: Codable, Identifiable, Hashable {
    let path: String
    let bytes: Int
    let modifiedAt: Int64

    var id: String { path }
}

struct GameFilesResponse: Codable {
    let files: [GameFile]
    let bytes: Int
}

/// Everything a control on a pad can be pointed at.
///
/// Wider than the keypad's own list: a pad has more buttons than a game has
/// game keys, and the spare ones are worth pointing at a number the game
/// reads — plenty of these games put "jump" on 5 and "menu" on 0.
enum PadTarget {
    static let all: [(button: String, label: String)] = [
        ("up", "Lên"), ("down", "Xuống"), ("left", "Trái"), ("right", "Phải"),
        ("fire", "Bắn"),
        ("softLeft", "Phím mềm trái"), ("softRight", "Phím mềm phải"),
        ("num0", "Phím 0"), ("num1", "Phím 1"), ("num2", "Phím 2"),
        ("num3", "Phím 3"), ("num4", "Phím 4"), ("num5", "Phím 5"),
        ("num6", "Phím 6"), ("num7", "Phím 7"), ("num8", "Phím 8"),
        ("num9", "Phím 9"),
        ("star", "Phím *"), ("hash", "Phím #"), ("clear", "Xoá"),
    ]
}

/// One control on a real pad, and what it presses.
struct PadMapping: Codable, Hashable, Identifiable {
    let pad: String
    let padName: String
    let button: String
    let buttonName: String

    var id: String { pad }
}

/// What a real controller's buttons do for one game.
struct GamepadSettings: Codable {
    let enabled: Bool
    let custom: Bool
    let pads: [PadMapping]
}

/// One key the player has dragged, in thousandths of a key.
struct KeyOffset: Codable, Hashable, Identifiable {
    let button: String
    let x: Int
    let y: Int

    var id: String { button }
}

/// Where the keys of one game's keypad are, and how big they are drawn.
struct KeypadArrangement: Codable {
    let scale: Int
    let custom: Bool
    let keys: [KeyOffset]
}

/// Whether a clip is being recorded, and how long it has got.
struct Recording: Codable {
    let recording: Bool
    let frames: Int
    let tenths: Int
    let full: Bool
    let maxSeconds: Int
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
