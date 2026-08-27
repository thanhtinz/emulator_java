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

    /// Which way the phone is meant to be held for this game. Auto-setup
    /// turns a game written for a wide screen; the button in the bar is for
    /// the ones that drew sideways on a portrait handset and left it to the
    /// player.
    private var landscape: Bool { (settings?.orientation ?? 0) == 1 }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button("‹  Thư viện") {
                    engine.stop()
                    client.refresh()
                    dismiss()
                }
                Spacer()
                // Deliberately not the game's title: the MIDlet has its own
                // title bar inside the screen, and repeating it invites
                // confusion with the game's own commands.
                Text("\(Int(engine.screenSize.width))×\(Int(engine.screenSize.height))"
                     + "  ·  \(engine.measuredFps) hình/giây")
                    .font(.caption)
                    .foregroundStyle(Palette.textDim)
                Spacer()
                Button(engine.isPaused ? "Tiếp tục" : "Tạm ngưng") {
                    engine.isPaused ? engine.resume() : engine.pause()
                }
                Spacer(minLength: 12)
                gameMenu
            }
            .tint(Palette.accent)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            if landscape && !engine.wantsText {
                // Held sideways, the game keeps the middle and each hand gets
                // a column. A keypad stacked under a wide screen would leave
                // the game a strip along the top.
                HStack(spacing: 0) {
                    ControlColumn(directional: true, softKeyLabel: engine.leftSoftKeyLabel,
                                  showSoftKey: !engine.showsSoftKeyBar,
                                  onPress: { engine.press($0) },
                                  onRelease: { engine.release($0) },
                                  shape: settings?.keyShape ?? 0,
                                  opacity: engine.keypadOpacity)
                        .padding(.vertical, 8)
                    GameSurface(engine: engine, smooth: settings?.smoothing ?? true)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    ControlColumn(directional: false, softKeyLabel: engine.rightSoftKeyLabel,
                                  showSoftKey: !engine.showsSoftKeyBar,
                                  onPress: { engine.press($0) },
                                  onRelease: { engine.release($0) },
                                  shape: settings?.keyShape ?? 0,
                                  opacity: engine.keypadOpacity)
                        .padding(.vertical, 8)
                }
                if let error = engine.error {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(Palette.bad)
                        .padding(.horizontal, 16)
                }
            } else {
                GameSurface(engine: engine, smooth: settings?.smoothing ?? true)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                if let error = engine.error {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(Palette.bad)
                        .padding(.horizontal, 16)
                }

                // While the game wants text, the system keyboard takes this half
                // of the screen. Multi-tap on a numeric pad was the only way a
                // handset could enter a name; asking for that with a real
                // keyboard in the user's hand would be a museum exhibit.
                if engine.wantsText {
                    GameTextField(engine: engine)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                } else {
                    Keypad(
                        onPress: { engine.press($0) },
                        onRelease: { engine.release($0) },
                        leftSoftKey: engine.leftSoftKeyLabel,
                        rightSoftKey: engine.rightSoftKeyLabel,
                        layout: settings?.keypadLayout ?? 0,
                        showSoftKeys: !engine.showsSoftKeyBar,
                        shape: settings?.keyShape ?? 0,
                        opacity: engine.keypadOpacity
                    )
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                }
            }
        }
        .background(Palette.background)
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .onAppear {
            engine.start(suiteId: suiteId, settings: client.settings(suiteId))
            turnDevice(to: landscape)
        }
        .onChange(of: landscape) { turnDevice(to: $0) }
        .onDisappear {
            engine.stop()
            // The rest of the app is a list of games, and a list reads
            // upright.
            turnDevice(to: false)
        }
    }
}

private extension EmulatorView {

    /// The menu behind the toolbar, and the reason it exists.
    ///
    /// J2ME Loader keeps exactly this set behind its overflow — a screenshot,
    /// which keys the keypad shows, which way the screen is held, the way out
    /// — because these are the things a player wants *while* a game is
    /// running and cannot reach from a settings page they would have to quit
    /// to get to.
    var gameMenu: some View {
        Menu {
            Button {
                client.takeScreenshot()
            } label: {
                Label("Chụp màn hình", systemImage: "camera")
            }
            Button {
                client.cycleKeypadLayout(suiteId)
            } label: {
                Label("Bàn phím: \(settings?.keypadLayoutName ?? "Đầy đủ")",
                      systemImage: "slider.horizontal.3")
            }
            Button {
                engine.rewind()
            } label: {
                Label("Tua lại 1 giây (\(engine.rewindDepth)s)", systemImage: "arrow.uturn.backward")
            }
            Button {
                engine.cycleSpeed()
            } label: {
                Label("Tốc độ: \(speedLabel(engine.speed))", systemImage: "speedometer")
            }
            Button {
                client.toggleOrientation(suiteId)
            } label: {
                Label(landscape ? "Màn hình: Ngang" : "Màn hình: Dọc",
                      systemImage: "rotate.right")
            }
            // Four slots of the player's own, plus the automatic one the
            // emulator writes when the game is left. Saving before something
            // hard and coming back to it is what one slot per game cannot do.
            Menu {
                ForEach(1..<5) { slot in
                    Button("Lưu vào ô \(slot)") { client.saveState(slot: slot) }
                }
            } label: {
                Label("Lưu trạng thái", systemImage: "square.and.arrow.down")
            }
            Menu {
                ForEach(client.saveSlots(suiteId).filter { $0.used && !$0.auto }) { slot in
                    Button("Nạp ô \(slot.slot)") { client.loadState(slot: slot.slot) }
                }
            } label: {
                Label("Nạp trạng thái", systemImage: "square.and.arrow.up")
            }
            Button(role: .destructive) {
                engine.stop()
                client.refresh()
                dismiss()
            } label: {
                Label("Thoát", systemImage: "rectangle.portrait.and.arrow.right")
            }
        } label: {
            Text("Menu")
        }
        .tint(Palette.accent)
    }
}

/// "2×", "0,5×": what the speed control shows.
private func speedLabel(_ speed: Int) -> String {
    speed % 100 == 0 ? "\(speed / 100)×" : "\(speed / 100),\((speed % 100) / 10)×"
}

/// Asks the system to turn the phone with the game.
///
/// A request, not a command: the player can have rotation locked, and a game
/// that refused to run because of that would be worse than one shown the way
/// round the phone already is.
private func turnDevice(to landscape: Bool) {
    guard let scene = UIApplication.shared.connectedScenes
        .compactMap({ $0 as? UIWindowScene }).first else { return }
    scene.requestGeometryUpdate(
        .iOS(interfaceOrientations: landscape ? .landscape : .portrait))
}

/// Draws the emulated framebuffer, unfiltered and centred.
private struct GameSurface: View {

    @ObservedObject var engine: EmulatorEngine
    var smooth: Bool = true

    var body: some View {
        GeometryReader { geometry in
            let rect = viewport(in: geometry.size)
            ZStack {
                Color.black
                if let frame = engine.frame {
                    Image(decorative: frame, scale: 1, orientation: .up)
                        // Smoothing is on by default: a handset packed this
                        // many pixels into about two inches, so drawing them
                        // as hard blocks looks worse than the real hardware.
                        .interpolation(smooth ? .medium : .none)
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

/// The field the game is asking for, backed by the system keyboard.
private struct GameTextField: View {

    @ObservedObject var engine: EmulatorEngine
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 10) {
            TextField("Nhập cho trò chơi", text: Binding(
                get: { engine.text },
                set: { value in
                    engine.text = value
                    engine.commitText(value)
                }
            ))
            .textFieldStyle(.roundedBorder)
            .submitLabel(.done)
            .focused($focused)
            .onSubmit { engine.press("softLeft") }

            HStack(spacing: 10) {
                Button(engine.leftSoftKeyLabel ?? "Xong") { engine.press("softLeft") }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                Button(engine.rightSoftKeyLabel ?? "Quay lại") { engine.press("softRight") }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
            }
        }
        .onAppear { focused = true }
    }
}
