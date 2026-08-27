import SwiftUI

/// Thông tin chung: bộ giả lập hỗ trợ gì, dữ liệu nằm ở đâu, vùng cách ly ra sao.
struct SettingsView: View {

    @EnvironmentObject private var client: MobiCoreClient

    private var totalBytes: Int64 {
        client.games.reduce(0) { $0 + $1.jarSize }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                SectionCard(title: "GIAO DIỆN") {
                    Picker("Sáng tối", selection: Binding(
                        get: { client.theme },
                        set: { client.setTheme($0) }
                    )) {
                        Text("Sáng").tag(ThemeChoice.light)
                        Text("Tối").tag(ThemeChoice.dark)
                        Text("Theo hệ thống").tag(ThemeChoice.system)
                    }
                    .pickerStyle(.segmented)
                }

                SectionCard(title: "BỘ CẤU HÌNH MẶC ĐỊNH") {
                    if client.presets.isEmpty {
                        Text("Lưu một bộ cấu hình trong phần cài đặt của game, rồi chọn ở đây "
                             + "để mọi game nhập vào sau đều dùng bộ đó.")
                            .font(.footnote)
                            .foregroundStyle(Palette.textDim)
                    } else {
                        // "Không dùng" is the default: with nothing chosen, a
                        // new game is configured from what is inside it,
                        // which is right until someone has decided otherwise
                        // for their own phone.
                        Picker("Game mới sẽ dùng", selection: Binding(
                            get: { client.defaultPreset },
                            set: { client.setDefaultPreset($0) }
                        )) {
                            Text("Không dùng").tag("")
                            ForEach(client.presets, id: \.self) { preset in
                                Text(preset).tag(preset)
                            }
                        }
                        .pickerStyle(.inline)
                    }
                }

                SectionCard(title: "BỘ GIẢ LẬP") {
                    VStack(spacing: 6) {
                        FieldRow(label: "Cấu hình", value: "CLDC 1.0 / 1.1")
                        FieldRow(label: "Hồ sơ", value: "MIDP 1.0 / 2.0")
                        FieldRow(label: "Kết xuất", value: "Điểm gần nhất, phóng bội số nguyên")
                    }
                }

                SectionCard(title: "LƯU TRỮ") {
                    VStack(alignment: .leading, spacing: 6) {
                        FieldRow(label: "Trò chơi đã cài", value: "\(client.games.count)")
                        FieldRow(label: "Dung lượng bộ cài", value: byteString(totalBytes))
                        Text(client.storageRoot)
                            .font(.caption2)
                            .foregroundStyle(Palette.textDim)
                            .lineLimit(2)
                            .truncationMode(.middle)
                    }
                }

                SectionCard(title: "BẢO MẬT") {
                    VStack(spacing: 6) {
                        FieldRow(label: "Vùng cách ly", value: "Mỗi trò chơi một thư mục riêng")
                        FieldRow(label: "Truy cập tệp", value: "Chỉ khi nhập")
                        FieldRow(label: "Mạng", value: "Tắt cho tới khi hồ sơ cho phép")
                    }
                }

                SectionCard(title: "GIỚI THIỆU") {
                    VStack(alignment: .leading, spacing: 6) {
                        FieldRow(label: "MobiCore", value: "1.0")
                        Text("Nền tảng chơi game J2ME: chạy, quản lý và tuỳ biến game Java ME "
                             + "trên thiết bị hiện đại.")
                            .font(.caption)
                            .foregroundStyle(Palette.textDim)
                    }
                }
            }
            .padding(16)
        }
        .background(Palette.background)
        .navigationTitle("Cài đặt")
    }
}
