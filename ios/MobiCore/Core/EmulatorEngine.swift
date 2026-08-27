import Combine
import CoreGraphics
import Foundation
import QuartzCore
import SwiftUI

/// Điều khiển một trò chơi đang chạy.
///
/// A J2ME game loop blocks and sleeps, so the MIDlet runs on a background
/// queue; only the finished frame is handed to the main actor. The display
/// link paces presentation so the UI redraws at most once per screen refresh
/// no matter how fast the game runs.
@MainActor
final class EmulatorEngine: ObservableObject {

    @Published private(set) var frame: CGImage?
    @Published private(set) var isRunning = false
    @Published private(set) var isPaused = false
    @Published private(set) var measuredFps = 0
    @Published private(set) var error: String?
    @Published private(set) var screenSize = CGSize(width: 240, height: 320)
    /// Labels the running screen has mapped to the two softkeys.
    @Published private(set) var leftSoftKeyLabel: String?
    @Published private(set) var rightSoftKeyLabel: String?
    /// True while the emulated screen draws the command bar itself, which on
    /// a touchscreen is the softkeys: tapping a label runs its command.
    @Published private(set) var showsSoftKeyBar = false

    /// How solid the keypad should be drawn now, in percent.
    ///
    /// Read off the emulator on every presented frame rather than worked out
    /// here: the session holds both the setting and the clock that fades it.
    @Published private(set) var keypadOpacity = 100
    /// How fast the game is playing, as a percentage of a handset's pace.
    @Published private(set) var speed = 100

    private let bridge = MobiCoreBridge.shared
    private let queue = DispatchQueue(label: "com.mobicore.midlet", qos: .userInitiated)
    private var displayLink: CADisplayLink?
    private var running = false
    private var frameLimit = 30
    private var framesThisSecond = 0
    private var secondMark = CACurrentMediaTime()

    func start(suiteId: String, settings: GameSettings?) {
        stop()
        // Starts the game and, if the player left one behind, puts it back
        // where it was.
        let response = bridge.startGameResuming(suiteId)
        guard let result: ActionResult = decode(response), result.ok else {
            error = decode(response).flatMap { (r: ActionResult) in r.error } ?? "Không khởi động được trò chơi"
            return
        }
        error = nil
        screenSize = CGSize(width: result.width ?? 240, height: result.height ?? 320)
        frameLimit = max(0, settings?.frameLimit ?? 30)
        isRunning = true
        isPaused = false
        running = true

        let link = CADisplayLink(target: self, selector: #selector(present))
        link.preferredFramesPerSecond = frameLimit == 0 ? 0 : min(60, frameLimit)
        link.add(to: .main, forMode: .common)
        displayLink = link

        refreshSoftKeys()
        queue.async { [weak self] in
            self?.runLoop()
        }
    }

    private nonisolated func runLoop() {
        while DispatchQueue.main.sync(execute: { self.running }) {
            let started = CACurrentMediaTime()
            // The frame budget follows the speed control: at double speed the
            // game's own clock runs twice as fast, and drawing at the old rate
            // would show half of what it does.
            let interval: TimeInterval = DispatchQueue.main.sync {
                self.frameLimit > 0
                    ? 1.0 / (Double(self.frameLimit) * Double(max(10, self.speed)) / 100.0)
                    : 0
            }
            let paused = DispatchQueue.main.sync { self.isPaused }
            if !paused {
                _ = bridge.renderFrame()
            }
            if bridge.isFinished {
                DispatchQueue.main.async { self.finish() }
                return
            }
            let elapsed = CACurrentMediaTime() - started
            let remaining = interval - elapsed
            Thread.sleep(forTimeInterval: remaining > 0 ? remaining : 0.001)
        }
    }

    /// Pulls the newest frame on the display's own cadence.
    @objc private func present() {
        guard running, !isPaused else { return }
        if let image = bridge.copyFrameImage()?.takeRetainedValue() {
            frame = image
        }
        refreshTextInput()
        let solidity = Int(bridge.keypadDrawOpacity())
        if solidity != keypadOpacity {
            keypadOpacity = solidity
        }
        framesThisSecond += 1
        let now = CACurrentMediaTime()
        if now - secondMark >= 1 {
            measuredFps = framesThisSecond
            framesThisSecond = 0
            secondMark = now
        }
    }

    private func finish() {
        stop()
    }

    func pause() {
        guard isRunning, !isPaused else { return }
        isPaused = true
        bridge.pauseGame()
    }

    func resume() {
        guard isRunning, isPaused else { return }
        isPaused = false
        bridge.resumeGame()
    }

    func stop() {
        running = false
        displayLink?.invalidate()
        displayLink = nil
        if isRunning {
            // Leaving a game saves it: on a phone, leaving is not always the
            // player's decision.
            bridge.stopGameSaving()
        }
        isRunning = false
        isPaused = false
    }

    // MARK: - Text entry

    /// True while the game is asking for text; the view raises the keyboard.
    @Published private(set) var wantsText = false
    @Published var text = ""

    /// Called each frame: the game decides when it wants text, not the view.
    private func refreshTextInput() {
        let active = bridge.isTextInputActive()
        if active != wantsText {
            wantsText = active
            text = active ? bridge.textInput() : ""
        }
    }

    /// Whole strings rather than key events: the keyboard does its own
    /// editing, and what the game should see is the result.
    func commitText(_ value: String) {
        bridge.setTextInput(value)
    }

    func press(_ button: String) {
        bridge.press(button)
        if button == "softLeft" || button == "softRight" {
            // A command may have swapped the screen, and with it the labels.
            refreshSoftKeys()
        }
    }

    /// What the two softkeys carry, and whether the screen shows them itself.
    private struct SoftKeys: Decodable {
        let left: String?
        let right: String?
        let bar: Bool?
    }

    /// Re-reads the softkey labels from the running screen.
    func refreshSoftKeys() {
        guard let data = bridge.softKeysJSON().data(using: .utf8),
              let labels = try? JSONDecoder().decode(SoftKeys.self, from: data) else {
            leftSoftKeyLabel = nil
            rightSoftKeyLabel = nil
            showsSoftKeyBar = false
            return
        }
        leftSoftKeyLabel = labels.left
        rightSoftKeyLabel = labels.right
        showsSoftKeyBar = labels.bar ?? false
    }

    /// A J2ME game paces itself off the clock, so this changes what it is
    /// told the time is; the game does the rest with its own logic intact.
    func cycleSpeed() {
        guard let data = bridge.cycleSpeed().data(using: .utf8),
              let payload = try? JSONDecoder().decode(SpeedResponse.self, from: data) else {
            return
        }
        speed = payload.speed
        // Drawing at the old rate would show half of what the game does.
        displayLink?.preferredFramesPerSecond = frameLimit == 0
            ? 0
            : min(60, max(1, frameLimit * speed / 100))
    }

    private struct SpeedResponse: Decodable {
        let speed: Int
    }

    /// How many seconds of play can still be taken back.
    @Published private(set) var rewindDepth = 0

    /// Takes back the last second or so. These games restart a level on one
    /// mistake, because a handset had nowhere to keep anything else.
    @discardableResult
    func rewind() -> Bool {
        guard let data = bridge.rewindStep().data(using: .utf8),
              let payload = try? JSONDecoder().decode(RewindResponse.self, from: data) else {
            return false
        }
        rewindDepth = payload.seconds
        return payload.ok
    }

    func refreshRewind() {
        guard let data = bridge.rewindJSON().data(using: .utf8),
              let payload = try? JSONDecoder().decode(RewindResponse.self, from: data) else {
            return
        }
        rewindDepth = payload.seconds
    }

    private struct RewindResponse: Decodable {
        let ok: Bool
        let seconds: Int
    }

    func release(_ button: String) {
        bridge.release(button)
    }

    /// Maps a point in the displayed rectangle back to emulated coordinates.
    func pointerDown(at point: CGPoint, in rect: CGRect) {
        guard let mapped = map(point, in: rect) else { return }
        bridge.pointerDown(atX: mapped.x, y: mapped.y)
    }

    func pointerMoved(to point: CGPoint, in rect: CGRect) {
        guard let mapped = map(point, in: rect) else { return }
        bridge.pointerMoved(toX: mapped.x, y: mapped.y)
    }

    func pointerUp(at point: CGPoint, in rect: CGRect) {
        guard let mapped = map(point, in: rect) else { return }
        bridge.pointerUp(atX: mapped.x, y: mapped.y)
    }

    private func map(_ point: CGPoint, in rect: CGRect) -> (x: Int, y: Int)? {
        guard rect.width > 0, rect.height > 0 else { return nil }
        let x = Int((point.x - rect.minX) / rect.width * screenSize.width)
        let y = Int((point.y - rect.minY) / rect.height * screenSize.height)
        guard x >= 0, y >= 0, x < Int(screenSize.width), y < Int(screenSize.height) else { return nil }
        return (x, y)
    }

    func screenshot() -> Data? {
        bridge.screenshotPNG()
    }

    func logLines() -> [String] {
        bridge.logText().split(separator: "\n").map(String.init)
    }

    private nonisolated func decode<T: Decodable>(_ json: String) -> T? {
        guard let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
}
