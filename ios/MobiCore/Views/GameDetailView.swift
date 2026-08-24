import PhotosUI
import SwiftUI

/// Ảnh bìa, thông tin và mọi thao tác với một trò chơi đã cài.
struct GameDetailView: View {

    let suiteId: String

    @EnvironmentObject private var client: MobiCoreClient
    @Environment(\.dismiss) private var dismiss
    @State private var confirmUninstall = false
    @State private var playing = false
    @State private var renaming = false
    @State private var newTitle = ""
    @State private var coverPick: PhotosPickerItem?
    @State private var coverError: String?

    private var game: Game? { client.game(suiteId) }

    var body: some View {
        ScrollView {
            if let game {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .top, spacing: 14) {
                        GameArtwork(title: game.title, image: client.artwork(suiteId), size: 84)
                            .id(client.artworkRevision)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(game.title)
                                .font(.title2.weight(.bold))
                                .foregroundStyle(Palette.text)
                            Text(game.vendor)
                                .font(.footnote)
                                .foregroundStyle(Palette.textDim)
                            HStack(spacing: 6) {
                                Chip(text: game.configuration)
                                Chip(text: game.profile)
                            }
                        }
                    }

                    HStack(spacing: 10) {
                        Button {
                            playing = true
                        } label: {
                            Text("Chơi").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Palette.accentDim)

                        NavigationLink {
                            GameSettingsView(suiteId: suiteId)
                        } label: {
                            Text("Cài đặt").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }

                    SectionCard(title: "TÊN VÀ ẢNH BÌA") {
                        VStack(alignment: .leading, spacing: 6) {
                            FieldRow(label: "Tên hiển thị", value: game.title)
                            if game.renamed {
                                FieldRow(label: "Tên gốc", value: game.originalTitle)
                            }
                            HStack(spacing: 10) {
                                Button {
                                    newTitle = game.title
                                    renaming = true
                                } label: {
                                    Label(game.renamed ? "Đổi tên" : "Đặt tên", systemImage: "pencil")
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(.bordered)

                                PhotosPicker(selection: $coverPick, matching: .images) {
                                    Label("Chọn ảnh", systemImage: "photo.on.rectangle")
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(.bordered)
                            }
                            .padding(.top, 4)

                            if game.renamed || game.hasArtwork {
                                Button("Trả về mặc định") {
                                    client.resetTitle(suiteId)
                                    client.resetArtwork(suiteId)
                                }
                                .font(.footnote)
                                .foregroundStyle(Palette.accent)
                            }
                            if let coverError {
                                Text(coverError)
                                    .font(.footnote)
                                    .foregroundStyle(Palette.bad)
                            }
                        }
                    }

                    SectionCard(title: "THÔNG TIN") {
                        VStack(spacing: 6) {
                            FieldRow(label: "Phiên bản", value: game.version)
                            FieldRow(label: "Mã bộ cài", value: game.suiteId)
                            FieldRow(label: "Dung lượng", value: byteString(game.jarSize))
                            FieldRow(label: "Máy giả lập",
                                     value: game.settings?.device.resolution ?? "—")
                            FieldRow(label: "Phóng ảnh", value: game.settings?.scaleModeName ?? "—")
                            FieldRow(label: "Số lần chơi",
                                     value: String(game.settings?.playCount ?? 0))
                        }
                    }

                    NavigationLink {
                        SavesView(suiteId: suiteId)
                    } label: {
                        SectionCard(title: "DỮ LIỆU LƯU", trailing: "\(game.stores) kho") {
                            Text(game.stores == 0
                                 ? "Trò chơi này chưa lưu gì."
                                 : "Quản lý kho bản ghi và bản sao lưu")
                                .font(.footnote)
                                .foregroundStyle(game.stores == 0 ? Palette.textDim : Palette.accent)
                        }
                    }
                    .buttonStyle(.plain)

                    SectionCard(title: "VÙNG NGUY HIỂM") {
                        VStack(alignment: .leading, spacing: 6) {
                            Button(confirmUninstall ? "Chạm lần nữa để gỡ" : "Gỡ trò chơi") {
                                if confirmUninstall {
                                    client.uninstall(suiteId)
                                    dismiss()
                                } else {
                                    confirmUninstall = true
                                }
                            }
                            .font(.footnote)
                            .foregroundStyle(Palette.bad)
                            Text("Dữ liệu lưu luôn được sao lưu trước khi xoá bất cứ thứ gì.")
                                .font(.caption2)
                                .foregroundStyle(Palette.textDim)
                        }
                    }
                }
                .padding(16)
            } else {
                EmptyState(icon: "questionmark.folder",
                           title: "Không tìm thấy trò chơi",
                           body: "Có thể nó đã được gỡ.")
            }
        }
        .background(Palette.background)
        .navigationTitle(game?.title ?? "Trò chơi")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button {
                client.toggleFavourite(suiteId)
            } label: {
                Image(systemName: (game?.settings?.favourite ?? false) ? "star.fill" : "star")
            }
            .tint(Palette.warn)
        }
        .onChange(of: coverPick) { _, item in
            guard let item else { return }
            Task {
                guard let raw = try? await item.loadTransferable(type: Data.self),
                      let png = Artwork.png(from: raw) else {
                    coverError = "Không đọc được ảnh này."
                    return
                }
                coverError = nil
                client.setArtwork(png, for: suiteId)
            }
        }
        // A blank name is refused while the keyboard is still up, rather than
        // through a failure after the fact.
        .alert("Tên trò chơi", isPresented: $renaming) {
            TextField("Tên trò chơi", text: $newTitle)
            Button("Huỷ", role: .cancel) { }
            Button("Lưu") {
                let trimmed = newTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    client.rename(suiteId, to: trimmed)
                }
            }
        }
        .fullScreenCover(isPresented: $playing) {
            EmulatorView(suiteId: suiteId)
                .environmentObject(client)
        }
    }
}

func byteString(_ bytes: Int64) -> String {
    if bytes >= 1024 * 1024 {
        return String(format: "%.1f MB", Double(bytes) / 1_048_576)
    }
    if bytes >= 1024 {
        return String(format: "%.1f KB", Double(bytes) / 1024)
    }
    return "\(bytes) B"
}
