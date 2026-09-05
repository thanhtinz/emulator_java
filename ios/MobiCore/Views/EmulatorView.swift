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
    /// Where the player dragged the keys, read once when the game opens.
    @State private var placement = KeyPlacement()

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
                // Giữa thanh này để trống có chủ ý: tên game thì MIDlet tự vẽ
                // ở trong màn hình của nó, còn cỡ màn hình và số hình mỗi giây
                // là con số của người viết máy ảo — một con số nhảy liên tục
                // ngay trên đầu màn game chỉ kéo mắt đi khỏi thứ đang chơi.
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

            if landscape && !engine.wantsText && !(settings?.keypadPutAway ?? false) {
                // Held sideways, the game keeps the middle and each hand gets
                // a column. A keypad stacked under a wide screen would leave
                // the game a strip along the top.
                HStack(spacing: 0) {
                    ControlColumn(directional: true, softKeyLabel: engine.leftSoftKeyLabel,
                                  onPress: { engine.press($0) },
                                  onRelease: { engine.release($0) },
                                  planFor: { w, h, k in columnPlan(left: true, width: w, height: h, key: k) },
                                  shape: settings?.keyShape ?? 0,
                                  opacity: engine.keypadOpacity,
                                  placement: placement)
                        .padding(.vertical, 8)
                    GameSurface(engine: engine, smooth: settings?.smoothing ?? true)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    ControlColumn(directional: false, softKeyLabel: engine.rightSoftKeyLabel,
                                  onPress: { engine.press($0) },
                                  onRelease: { engine.release($0) },
                                  planFor: { w, h, k in columnPlan(left: false, width: w, height: h, key: k) },
                                  shape: settings?.keyShape ?? 0,
                                  opacity: engine.keypadOpacity,
                                  placement: placement)
                        .padding(.vertical, 8)
                }
            } else {
                GameSurface(engine: engine, smooth: settings?.smoothing ?? true)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                // While the game wants text, the system keyboard takes this half
                // of the screen. Multi-tap on a numeric pad was the only way a
                // handset could enter a name; asking for that with a real
                // keyboard in the user's hand would be a museum exhibit.
                if engine.wantsText {
                    GameTextField(engine: engine)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                } else if !(settings?.keypadPutAway ?? false) {
                    GeometryReader { geometry in
                        let key = KeyMetrics.upright(placement)
                        Keypad(
                            onPress: { engine.press($0) },
                            onRelease: { engine.release($0) },
                            leftSoftKey: engine.leftSoftKeyLabel,
                            rightSoftKey: engine.rightSoftKeyLabel,
                            plan: client.keypadPlan(suiteId,
                                                    width: Int(geometry.size.width),
                                                    height: Int(geometry.size.height),
                                                    key: Int(key),
                                                    landscape: false, left: true),
                            key: key,
                            shape: settings?.keyShape ?? 0,
                            opacity: engine.keypadOpacity,
                            placement: placement
                        )
                    }
                    .frame(height: KeyMetrics.upright * 5)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                }
            }
        }
        .background(Palette.background)
        // Game chết thì màn hình phải nói ra vì sao. Dòng chữ đỏ cỡ chú thích
        // nằm cạnh bàn phím ảo trước đây chỉ ghi tên lớp ngoại lệ — thứ không
        // người chơi nào đọc, và đọc rồi cũng không làm được gì.
        .overlay {
            if let crash = engine.crash {
                CrashCard(crash: crash,
                          stack: crash.stack ?? [],
                          onClose: {
                              engine.dismissCrash()
                              client.refresh()
                              dismiss()
                          },
                          onRetry: {
                              engine.dismissCrash()
                              engine.start(suiteId: suiteId,
                                           settings: client.settings(suiteId))
                          })
            }
        }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .onAppear {
            engine.start(suiteId: suiteId, settings: client.settings(suiteId))
            // A controller belongs to whatever is on screen, and while a game
            // is on screen that is the game.
            engine.watchForControllers()
            // The sensor runs only while a game that asked for it is open.
            if client.tilt(suiteId)?.enabled == true {
                engine.startTilting()
            }
            // Where the player dragged the keys. Read once: an arrangement
            // only changes on the screen that edits it, which this is not.
            placement = keyPlacement()
            turnDevice(to: landscape)
        }
        .onChange(of: landscape) { turnDevice(to: $0) }
        .onDisappear {
            engine.stopTilting()
            engine.stop()
            // The rest of the app is a list of games, and a list reads
            // upright.
            turnDevice(to: false)
        }
    }
}

private extension EmulatorView {

    /// The player's own key positions, in the shape the keypad wants them.
    func keyPlacement() -> KeyPlacement {
        guard let arrangement = client.keypadArrangement(suiteId) else {
            return KeyPlacement()
        }
        var offsets: [String: CGPoint] = [:]
        for key in arrangement.keys {
            offsets[key.button] = CGPoint(x: CGFloat(key.x) / 1000,
                                          y: CGFloat(key.y) / 1000)
        }
        return KeyPlacement(offsets: offsets, scale: arrangement.scale)
    }

    /// One side of the sideways keypad, measured by the core.
    func columnPlan(left: Bool, width: Int, height: Int, key: Int) -> KeypadPlanData? {
        client.keypadPlan(suiteId, width: width, height: height, key: key,
                          landscape: true, left: left)
    }

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
            // Putting the keypad away is its own item rather than a fourth
            // keypad on the cycle: coming back has to bring back the one that
            // was there.
            Button {
                client.toggleKeypad(suiteId)
            } label: {
                Label(settings?.keypadPutAway == true ? "Hiện bàn phím" : "Ẩn bàn phím",
                      systemImage: "keyboard")
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

/// Lời giải thích khi game chết.
///
/// Ba câu và hai nút: hỏng cái gì, vì sao, làm gì tiếp — rồi đóng lại hoặc
/// chơi lại. Phần kỹ thuật gấp sẵn: người chơi không cần nó, người sửa game
/// thì cần, và để nó bung ra sẵn thì câu đáng đọc bị đẩy xuống dưới.
private struct CrashCard: View {

    let crash: CrashReading
    let stack: [String]
    let onClose: () -> Void
    let onRetry: () -> Void

    @State private var showDetail = false

    var body: some View {
        ZStack {
            Color.black.opacity(0.65).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 12) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.title2)
                    .foregroundStyle(Palette.bad)
                Text(crash.title ?? "Game dừng đột ngột")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(Palette.text)
                Text(crash.reason ?? "")
                    .font(.body)
                    .foregroundStyle(Palette.text)
                if let advice = crash.advice, !advice.isEmpty {
                    HStack(alignment: .top, spacing: 10) {
                        Rectangle()
                            .fill(Palette.accent)
                            .frame(width: 3)
                        Text(advice)
                            .font(.footnote)
                            .foregroundStyle(Palette.textDim)
                    }
                    .fixedSize(horizontal: false, vertical: true)
                }
                Button(showDetail ? "Ẩn chi tiết kỹ thuật" : "Chi tiết kỹ thuật") {
                    showDetail.toggle()
                }
                .font(.footnote)
                .tint(Palette.accent)
                if showDetail {
                    // Nguyên văn tiếng Anh: đây là phần để tra cứu, dịch ra
                    // thì không tra được nữa.
                    ScrollView {
                        Text(([crash.technical ?? ""] + stack).joined(separator: "\n"))
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundStyle(Palette.textDim)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(maxHeight: 140)
                }
                HStack(spacing: 12) {
                    Button("Đóng", action: onClose)
                        .buttonStyle(.bordered)
                        .tint(Palette.textDim)
                    Button("Chơi lại", action: onRetry)
                        .buttonStyle(.borderedProminent)
                        .tint(Palette.accent)
                }
                .padding(.top, 4)
            }
            .padding(20)
            .background(Palette.surface, in: RoundedRectangle(cornerRadius: 18))
            .padding(.horizontal, 20)
        }
    }
}
