import Combine
import CoreGraphics
import Foundation
import CoreMotion
import GameController
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
    @Published private(set) var error: String?
    /// Vì sao game vừa chết, khi nó chết. Xem `CrashReading`.
    @Published private(set) var crash: CrashReading?
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
        crash = nil
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
        // Hỏi bằng một cờ chứ không dựng JSON mỗi khung hình: phần lớn thời
        // gian câu trả lời là "không có gì", và chuỗi ấy sẽ bị vứt đi ngay.
        if crash == nil, bridge.hasCrashed {
            let reading: CrashReading? = decode(bridge.crashJSON())
            if let reading, reading.has {
                crash = reading
                // Game đã chết thì vòng lặp không còn gì để chạy, nhưng phiên
                // vẫn giữ nguyên để người chơi còn thấy màn hình cuối.
                running = false
                isRunning = false
            }
        }
    }

    private func finish() {
        stop()
    }

    /// Người chơi đã đọc xong lời báo hỏng; game đã chết được dọn đi.
    func dismissCrash() {
        crash = nil
        error = nil
        _ = bridge.dismissCrash()
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
        // Một game kẹt trong vòng lặp của chính nó không bao giờ đọc tới cờ
        // dừng ở trên: lệnh này xuyên thẳng vào máy ảo, nên rời một game treo
        // là chuyện tức thì chứ không phải chờ hết giờ.
        _ = bridge.requestStopGame()
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

    // ------------------------------------------------------------- tilting

    /// The phone's own idea of which way it is being held.
    private let motion = CMMotionManager()

    /**
     Starts reading how the phone is held.

     What the emulator wants is "how far is this leaning", from -1 to 1, and
     Core Motion's gravity vector is already in those units: a phone on its
     side reads 1 on one axis. Held upright in portrait, gravity is along -y,
     so leaning right tips it into x and leaning away tips it out of y.

     Reading stops with the game. A sensor left running is a battery drained
     for a screen nobody is looking at.
     */
    func startTilting() {
        guard motion.isDeviceMotionAvailable, !motion.isDeviceMotionActive else { return }
        // The game reads held keys at its own pace, so a faster rate would be
        // readings nobody looks at.
        motion.deviceMotionUpdateInterval = 1.0 / 30
        motion.startDeviceMotionUpdates(to: .main) { [weak self] data, _ in
            guard let gravity = data?.gravity else { return }
            let x = max(-1, min(1, gravity.x))
            let y = max(-1, min(1, -gravity.y - 1))
            _ = self?.bridge.tiltedX(Int(x * 1000), y: Int(y * 1000))
        }
    }

    func stopTilting() {
        if motion.isDeviceMotionActive {
            motion.stopDeviceMotionUpdates()
        }
    }

    // ---------------------------------------------------------- the controller

    /// True while a controller is connected, so the game can say so.
    @Published private(set) var padConnected = false

    /// Which directions the stick is pushing, so a change can be told apart
    /// from the stream of positions a stick actually sends.
    private var stickHeld: Set<String> = []

    /**
     Starts listening for a controller.

     iOS hands out controllers through notifications rather than events, so
     this watches for one arriving and binds its buttons when it does. Bound
     to the emulator's own names — `padA`, `padUp` — because what those do is
     the profile's decision, made once for iOS and Android alike.
     */
    func watchForControllers() {
        NotificationCenter.default.addObserver(
            forName: .GCControllerDidConnect, object: nil, queue: .main
        ) { [weak self] note in
            guard let controller = note.object as? GCController else { return }
            Task { @MainActor in self?.bind(controller) }
        }
        NotificationCenter.default.addObserver(
            forName: .GCControllerDidDisconnect, object: nil, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.padConnected = !GCController.controllers().isEmpty
            }
        }
        for controller in GCController.controllers() {
            bind(controller)
        }
    }

    private func bind(_ controller: GCController) {
        guard let pad = controller.extendedGamepad else { return }
        padConnected = true

        func hold(_ name: String) -> GCControllerButtonValueChangedHandler {
            { [weak self] _, _, pressed in
                Task { @MainActor in
                    if pressed {
                        self?.pressPad(name)
                    } else {
                        self?.releasePad(name)
                    }
                }
            }
        }

        pad.buttonA.pressedChangedHandler = hold("padA")
        pad.buttonB.pressedChangedHandler = hold("padB")
        pad.buttonX.pressedChangedHandler = hold("padX")
        pad.buttonY.pressedChangedHandler = hold("padY")
        pad.leftShoulder.pressedChangedHandler = hold("padL1")
        pad.rightShoulder.pressedChangedHandler = hold("padR1")
        pad.leftTrigger.pressedChangedHandler = hold("padL2")
        pad.rightTrigger.pressedChangedHandler = hold("padR2")
        pad.buttonMenu.pressedChangedHandler = hold("padStart")
        pad.buttonOptions?.pressedChangedHandler = hold("padSelect")

        pad.dpad.up.pressedChangedHandler = hold("padUp")
        pad.dpad.down.pressedChangedHandler = hold("padDown")
        pad.dpad.left.pressedChangedHandler = hold("padLeft")
        pad.dpad.right.pressedChangedHandler = hold("padRight")

        // A J2ME game has four directions and nothing else, so the stick is
        // read as a d-pad. The dead zone is wide on purpose: a worn stick
        // resting at 0.2 would otherwise walk the player into a wall for as
        // long as the game is open.
        //
        // A direction is taken at one angle and given up at a smaller one,
        // the same way tilting works: with a single threshold, a stick
        // resting right on it sends press, release, press, release many times
        // a second, which the game reads as a player hammering the key.
        pad.leftThumbstick.valueChangedHandler = { [weak self] _, x, y in
            Task { @MainActor in
                guard let self else { return }
                let was = self.stickHeld
                func amount(_ button: String, _ pushed: Float) -> Bool {
                    pushed >= (was.contains(button) ? 0.35 : 0.5)
                }
                var held: Set<String> = []
                if amount("padLeft", -x) { held.insert("padLeft") }
                if amount("padRight", x) { held.insert("padRight") }
                if amount("padUp", y) { held.insert("padUp") }
                if amount("padDown", -y) { held.insert("padDown") }
                self.stickMoved(held)
            }
        }
    }

    /// A control on a real pad was pressed. The profile decides what it does.
    func pressPad(_ pad: String) {
        _ = bridge.pressPad(pad)
        refreshSoftKeys()
    }

    func releasePad(_ pad: String) {
        _ = bridge.releasePad(pad)
    }

    /// What the stick is pushing now, as presses and releases.
    ///
    /// A stick sends positions, not presses, so the change is worked out here.
    /// Without it a game reading held keys would see one press and then
    /// nothing.
    private func stickMoved(_ directions: Set<String>) {
        guard directions != stickHeld else { return }
        for gone in stickHeld.subtracting(directions) {
            releasePad(gone)
        }
        for fresh in directions.subtracting(stickHeld) {
            pressPad(fresh)
        }
        stickHeld = directions
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
