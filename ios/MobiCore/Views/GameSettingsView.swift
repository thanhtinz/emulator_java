import SwiftUI

/// Cấu hình riêng từng trò chơi: máy giả lập, hiển thị, âm thanh, phím, mạng.
struct GameSettingsView: View {

    let suiteId: String

    @EnvironmentObject private var client: MobiCoreClient
    @State private var settings: GameSettings?
    /// Everything past the automatic card stays hidden until asked for: a
    /// player who just wants to play should not have to scroll a page of
    /// switches to learn there is nothing for them to do.
    @State private var showAdvanced = false
    @State private var presetName = ""
    @State private var keyChoices: [KeyChoice] = []

    var body: some View {
        ScrollView {
            if var current = settings {
                VStack(alignment: .leading, spacing: 14) {
                    SectionCard(
                        title: "ĐÃ TỰ CẤU HÌNH",
                        trailing: current.auto ? "tự động" : "đã chỉnh tay"
                    ) {
                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(current.setupNotes, id: \.self) { note in
                                Label(note, systemImage: "checkmark.circle.fill")
                                    .font(.footnote)
                                    .foregroundStyle(Palette.text)
                            }
                            Text("Không cần chỉnh gì để chơi.")
                                .font(.caption)
                                .foregroundStyle(Palette.textDim)
                            HStack(spacing: 10) {
                                Button {
                                    client.autoSetup(suiteId)
                                    reload()
                                } label: {
                                    Label("Dò lại", systemImage: "arrow.clockwise")
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(.bordered)

                                Button {
                                    showAdvanced.toggle()
                                } label: {
                                    Label(showAdvanced ? "Ẩn nâng cao" : "Nâng cao",
                                          systemImage: "slider.horizontal.3")
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(.bordered)
                            }
                            .padding(.top, 4)
                        }
                    }

                    if showAdvanced {
                    Text("Chỉ chỉnh khi game chạy sai.")
                        .font(.caption)
                        .foregroundStyle(Palette.textDim)

                    // One screen for every game, so this states it rather
                    // than offering a choice nobody has a reason to make.
                    SectionCard(title: "MÀN HÌNH", trailing: current.device.keypadName) {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text("Kích thước")
                                    .font(.footnote)
                                    .foregroundStyle(Palette.text)
                                Spacer()
                                Text(current.device.resolution)
                                    .font(.footnote)
                                    .foregroundStyle(Palette.textDim)
                            }
                            HStack {
                                Text("Chiều màn hình")
                                    .font(.footnote)
                                    .foregroundStyle(Palette.text)
                                Spacer()
                                Text(current.device.name)
                                    .font(.footnote)
                                    .foregroundStyle(Palette.textDim)
                            }
                        }
                    }

                    // Held sideways the keypad sits over the game itself, so
                    // how solid it is decides how much of the game is left to
                    // look at.
                    SectionCard(title: "BÀN PHÍM ẢO", trailing: "\(current.keyOpacity)%") {
                        VStack(alignment: .leading, spacing: 10) {
                            FieldRow(label: "Độ rõ", value: "\(current.keyOpacity)%")
                            Slider(
                                value: Binding(
                                    get: { Double(current.keyOpacity) },
                                    set: { current.keypadOpacity = Int($0) }
                                ),
                                in: 20...100,
                                step: 5,
                                onEditingChanged: { editing in
                                    if !editing {
                                        client.setKeypadOpacity(current.keyOpacity, for: suiteId)
                                        reload()
                                    }
                                }
                            )

                            Picker("Hình phím", selection: Binding(
                                get: { current.keyShape },
                                set: { client.setKeypadShape($0, for: suiteId); reload() }
                            )) {
                                Text("Bo góc").tag(0)
                                Text("Vuông").tag(1)
                                Text("Tròn").tag(2)
                            }
                            .pickerStyle(.segmented)

                            NavigationLink {
                                ArrangeKeysView(suiteId: suiteId)
                            } label: {
                                Text("Sắp xếp bàn phím")
                                    .font(.footnote)
                                    .foregroundStyle(Palette.accent)
                            }

                            // It fades rather than disappears: a keypad that
                            // vanishes leaves the thumb hunting a blank
                            // screen.
                            Picker("Tự mờ khi không dùng", selection: Binding(
                                get: { current.keyFadeDelay },
                                set: { client.setKeypadFadeDelay($0, for: suiteId); reload() }
                            )) {
                                Text("Luôn rõ").tag(0)
                                Text("5 giây").tag(5)
                                Text("10 giây").tag(10)
                                Text("30 giây").tag(30)
                            }
                            .pickerStyle(.segmented)
                        }
                    }

                    SectionCard(title: "HIỂN THỊ") {
                        VStack(alignment: .leading, spacing: 10) {
                            Picker("Phóng ảnh", selection: Binding(
                                get: { current.scaleMode },
                                set: { current.scaleMode = $0; save(current) }
                            )) {
                                ForEach(0..<GameSettings.scaleModeNames.count, id: \.self) { index in
                                    Text(GameSettings.scaleModeNames[index]).tag(index)
                                }
                            }
                            .pickerStyle(.segmented)

                            FieldRow(
                                label: "Giới hạn khung hình",
                                value: current.frameLimit == 0 ? "Không giới hạn" : "\(current.frameLimit) hình/giây"
                            )
                            Slider(
                                value: Binding(
                                    get: { Double(current.frameLimit) },
                                    set: { current.frameLimit = Int($0) }
                                ),
                                in: 0...60,
                                step: 1,
                                onEditingChanged: { editing in
                                    if !editing { save(current) }
                                }
                            )

                            Toggle(isOn: Binding(
                                get: { current.smoothing },
                                set: { current.smoothing = $0; save(current) }
                            )) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Làm mượt")
                                    Text("Khử răng cưa cạnh chéo và làm mượt khi phóng to")
                                        .font(.caption2)
                                        .foregroundStyle(Palette.textDim)
                                }
                            }
                            .font(.footnote)

                            Toggle("Hiện số khung hình", isOn: Binding(
                                get: { current.showFps },
                                set: { current.showFps = $0; save(current) }
                            ))
                            .font(.footnote)
                        }
                    }

                    SectionCard(title: "ÂM THANH") {
                        VStack(alignment: .leading, spacing: 8) {
                            FieldRow(label: "Âm lượng", value: "\(current.volume)%")
                            Slider(
                                value: Binding(
                                    get: { Double(current.volume) },
                                    set: { current.volume = Int($0) }
                                ),
                                in: 0...100,
                                step: 1,
                                onEditingChanged: { editing in
                                    if !editing { save(current) }
                                }
                            )
                            // The buzz was part of the game, so it is on; off
                            // is a real choice, because a game that vibrates
                            // on every hit cannot be played quietly next to
                            // someone.
                            Toggle("Rung", isOn: Binding(
                                get: { current.vibration },
                                set: { current.vibration = $0; save(current) }
                            ))
                            .font(.footnote)
                        }
                    }

                    SectionCard(title: "GÁN PHÍM", trailing: current.input.preset) {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 14) {
                                ForEach(["Nokia", "Sony Ericsson", "Samsung"], id: \.self) { preset in
                                    Button(preset) {
                                        client.setInputPreset(preset, for: suiteId)
                                        reload()
                                    }
                                    .font(.caption)
                                    .tint(current.input.preset == preset ? Palette.accent : Palette.textDim)
                                }
                            }
                            // Changeable, not just shown: a game written for
                            // one handset reads the code that handset sent —
                            // plenty read '2' and '8' for up and down — and a
                            // wrong guess reads as a broken emulator rather
                            // than a wrong key.
                            ForEach(GameSettings.buttonLabels, id: \.button) { entry in
                                Picker(entry.label, selection: Binding(
                                    get: { current.input.mappings[entry.button] ?? 0 },
                                    set: {
                                        client.setKeyMapping($0, button: entry.button,
                                                             for: suiteId)
                                        reload()
                                    }
                                )) {
                                    ForEach(keyChoices) { choice in
                                        Text("\(choice.keyName)  (\(choice.keyCode))")
                                            .tag(choice.keyCode)
                                    }
                                }
                                .font(.subheadline)
                            }
                        }
                    }

                    SectionCard(title: "LIÊN THANH") {
                        // Turbo where it belongs: beside the keys it acts on.
                        // Only fire gets it — a d-pad that repeats is a d-pad
                        // that stutters.
                        Picker("Phím Chọn", selection: Binding(
                            get: { current.input.turbo?["fire"] ?? 0 },
                            set: { client.setTurbo($0, button: "fire", for: suiteId); reload() }
                        )) {
                            Text("Tắt").tag(0)
                            Text("Chậm").tag(120)
                            Text("Nhanh").tag(50)
                        }
                        .pickerStyle(.segmented)
                    }

                    // Somebody with eighty games has one answer to "how big,
                    // how loud, how many frames". A preset is that answer with
                    // a name on it: worked out here, applied to the rest.
                    SectionCard(title: "BỘ CẤU HÌNH",
                                trailing: "\(client.presets.count) bộ") {
                        VStack(alignment: .leading, spacing: 8) {
                            if client.presets.isEmpty {
                                Text("Chưa có bộ nào. Lưu cấu hình của game này rồi áp cho "
                                     + "các game khác.")
                                    .font(.footnote)
                                    .foregroundStyle(Palette.textDim)
                            }
                            ForEach(client.presets, id: \.self) { preset in
                                HStack {
                                    Text(preset)
                                        .font(.subheadline)
                                        .foregroundStyle(Palette.text)
                                    Spacer()
                                    Button("Áp dụng") {
                                        client.applyPreset(preset, to: suiteId)
                                        reload()
                                    }
                                    .font(.footnote)
                                    Button("Xoá") { client.deletePreset(preset) }
                                        .font(.footnote)
                                        .tint(Palette.bad)
                                }
                            }
                            HStack {
                                // Typed rather than picked: the useful names
                                // are the player's own words, and no list the
                                // app writes would contain them.
                                TextField("Tên bộ cấu hình", text: $presetName)
                                    .textFieldStyle(.roundedBorder)
                                Button("Lưu") {
                                    let name = presetName.trimmingCharacters(in: .whitespaces)
                                    guard !name.isEmpty else { return }
                                    client.savePreset(name, from: suiteId)
                                    presetName = ""
                                }
                                .disabled(presetName.trimmingCharacters(in: .whitespaces).isEmpty)
                            }
                        }
                    }

                    SectionCard(title: "MẠNG") {
                        Picker("Truy cập mạng", selection: Binding(
                            get: { current.networkMode },
                            set: { current.networkMode = $0; save(current) }
                        )) {
                            ForEach(0..<GameSettings.networkModeNames.count, id: \.self) { index in
                                Text(GameSettings.networkModeNames[index]).tag(index)
                            }
                        }
                        .pickerStyle(.segmented)
                    }
                    }
                }
                .padding(16)
            } else {
                ProgressView().padding(40)
            }
        }
        .background(Palette.background)
        .navigationTitle("Cài đặt trò chơi")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            reload()
            keyChoices = client.keyChoices()
        }
    }

    private func reload() {
        settings = client.settings(suiteId)
    }

    private func save(_ updated: GameSettings) {
        client.update(updated)
        settings = client.settings(suiteId)
    }
}
