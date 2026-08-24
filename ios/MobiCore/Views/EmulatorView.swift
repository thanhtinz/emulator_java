import SwiftUI

/// Màn hình chơi: khung hình của trò chơi và bàn phím ảo.
///
/// The status bar and the home indicator are hidden while a game runs. The
/// emulated screen is small to begin with, and giving up a strip of it to
/// system chrome wastes the space the player actually looks at.
struct EmulatorView: View {

    let suiteId: String

    @EnvironmentObject private var client: MobiCoreClient
    @Environment(\.dismiss) private var dismiss
    @StateObject private var engine = EmulatorEngine()

    private var settings: GameSettings? { client.game(suiteId)?.settings }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button("‹  Thoát") {
                    engine.stop()
                    client.refresh()
                    dismiss()
                }
                Spacer()
                if settings?.showFps ?? false {
                    Text("\(engine.measuredFps) hình/giây")
                        .font(.caption)
                        .foregroundStyle(Palette.textDim)
                }
                Spacer()
                Button(engine.isPaused ? "Tiếp tục" : "Tạm dừng") {
                    engine.isPaused ? engine.resume() : engine.pause()
                }
            }
            .tint(Palette.accent)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            GameSurface(engine: engine)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            HStack {
                Text("\(Int(engine.screenSize.width))×\(Int(engine.screenSize.height))"
                     + "  ·  bội số nguyên  ·  không làm mượt")
                    .font(.caption2)
                    .foregroundStyle(Palette.textDim)
                Spacer()
                Text("\(engine.measuredFps) hình/giây")
                    .font(.caption2)
                    .foregroundStyle(Palette.good)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 5)
            .background(Palette.surfaceAlt)

            if let error = engine.error {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(Palette.bad)
                    .padding(.horizontal, 16)
            }

            Keypad(
                onPress: { engine.press($0) },
                onRelease: { engine.release($0) }
            )
            .padding(12)
        }
        .background(Palette.background)
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .onAppear {
            engine.start(suiteId: suiteId, settings: client.settings(suiteId))
        }
        .onDisappear {
            engine.stop()
        }
    }
}

/// Draws the emulated framebuffer, unfiltered and centred.
private struct GameSurface: View {

    @ObservedObject var engine: EmulatorEngine

    var body: some View {
        GeometryReader { geometry in
            let rect = viewport(in: geometry.size)
            ZStack {
                Color.black
                if let frame = engine.frame {
                    Image(decorative: frame, scale: 1, orientation: .up)
                        // Nearest neighbour: smoothing pixel art is the one
                        // thing an emulator must never do.
                        .interpolation(.none)
                        .antialiased(false)
                        .resizable()
                        .frame(width: rect.width, height: rect.height)
                        .position(x: rect.midX, y: rect.midY)
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        engine.pointerMoved(to: value.location, in: rect)
                    }
                    .onEnded { value in
                        engine.pointerUp(at: value.location, in: rect)
                    }
            )
        }
    }

    /// Integer scale by default, so every emulated pixel stays square.
    private func viewport(in size: CGSize) -> CGRect {
        let source = engine.screenSize
        guard source.width > 0, source.height > 0 else {
            return CGRect(origin: .zero, size: size)
        }
        let factor = max(1, floor(min(size.width / source.width, size.height / source.height)))
        let width = source.width * factor
        let height = source.height * factor
        return CGRect(
            x: (size.width - width) / 2,
            y: (size.height - height) / 2,
            width: width,
            height: height
        )
    }
}
