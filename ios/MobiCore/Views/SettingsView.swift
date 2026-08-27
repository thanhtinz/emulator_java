import SwiftUI
import UniformTypeIdentifiers

/// The library archive as a document, which is all a file exporter needs.
struct LibraryBackupFile: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }

    let data: Data

    init(data: Data) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

/// Thông tin chung: bộ giả lập hỗ trợ gì, dữ liệu nằm ở đâu, vùng cách ly ra sao.
struct SettingsView: View {

    @EnvironmentObject private var client: MobiCoreClient
    @State private var exporting = false
    @State private var importing = false
    @State private var backupNote: String?

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

                SectionCard(title: "SAO LƯU TOÀN BỘ") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Một tệp gồm trò chơi, cấu hình, dữ liệu lưu, ảnh chụp và bộ "
                             + "cấu hình — để mang sang máy khác.")
                            .font(.footnote)
                            .foregroundStyle(Palette.textDim)
                        HStack(spacing: 20) {
                            Button("Xuất tệp") { exporting = true }
                            Button("Khôi phục") { importing = true }
                        }
                        .font(.subheadline)
                        if let note = backupNote {
                            Text(note)
                                .font(.caption)
                                .foregroundStyle(Palette.textDim)
                        }
                    }
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
        .fileExporter(
            isPresented: $exporting,
            document: LibraryBackupFile(data: client.exportLibrary() ?? Data()),
            contentType: .data,
            defaultFilename: "mobicore-library"
        ) { result in
            backupNote = (try? result.get()) != nil
                ? "Đã lưu bản sao lưu"
                : "Lưu thất bại"
        }
        .fileImporter(
            isPresented: $importing,
            allowedContentTypes: [.data],
            allowsMultipleSelection: false
        ) { result in
            guard case .success(let urls) = result, let url = urls.first else { return }
            // A file picked from another app is read inside a security scope.
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: url) else {
                backupNote = "Không đọc được tệp"
                return
            }
            backupNote = client.importLibrary(data)
        }
        .navigationTitle("Cài đặt")
    }
}
