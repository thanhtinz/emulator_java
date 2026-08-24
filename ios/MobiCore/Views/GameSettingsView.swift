import SwiftUI

/// Cấu hình riêng từng trò chơi: máy giả lập, hiển thị, âm thanh, phím, mạng.
struct GameSettingsView: View {

    let suiteId: String

    @EnvironmentObject private var client: MobiCoreClient
    @State private var settings: GameSettings?

    var body: some View {
        ScrollView {
            if var current = settings {
                VStack(alignment: .leading, spacing: 14) {
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
