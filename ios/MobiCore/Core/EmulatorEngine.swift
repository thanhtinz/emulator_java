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

    private let bridge = MobiCoreBridge.shared
    private let queue = DispatchQueue(label: "com.mobicore.midlet", qos: .userInitiated)
    private var displayLink: CADisplayLink?
    private var running = false
    private var frameLimit = 30
    private var framesThisSecond = 0
    private var secondMark = CACurrentMediaTime()

    func start(suiteId: String, settings: GameSettings?) {
        stop()
        let response = bridge.start(suiteId)
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

        queue.async { [weak self] in
            self?.runLoop()
        }
    }

    private nonisolated func runLoop() {
        let interval: TimeInterval = {
            let limit = DispatchQueue.main.sync { self.frameLimit }
            return limit > 0 ? 1.0 / Double(limit) : 0
        }()
        while DispatchQueue.main.sync(execute: { self.running }) {
            let started = CACurrentMediaTime()
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
            bridge.stopGame()
        }
        isRunning = false
        isPaused = false
    }

    func press(_ button: String) {
        bridge.press(button)
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
