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

                    SectionCard(title: "MÁY GIẢ LẬP", trailing: current.device.keypadName) {
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(current.devices ?? []) { device in
                                Button {
                                    client.setDevice(device.id, for: suiteId)
                                    reload()
                                } label: {
                                    HStack {
                                        Text(device.name)
                                            .font(.footnote)
                                            .foregroundStyle(
                                                device.id == current.device.id
                                                    ? Palette.accent : Palette.text
                                            )
                                        Spacer()
                                        Text(device.resolution)
                                            .font(.footnote)
                                            .foregroundStyle(Palette.textDim)
                                    }
                                }
                            }
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
                            ForEach(GameSettings.buttonLabels, id: \.button) { entry in
                                FieldRow(
                                    label: entry.label,
                                    value: String(current.input.mappings[entry.button] ?? 0)
                                )
                            }
                        }
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
        .onAppear(perform: reload)
    }

    private func reload() {
        settings = client.settings(suiteId)
    }

    private func save(_ updated: GameSettings) {
        var copy = updated
        // The device catalog is read-only decoration; it must not be written
        // back into the stored profile.
        copy.devices = nil
        client.update(copy)
        settings = client.settings(suiteId)
    }
}
