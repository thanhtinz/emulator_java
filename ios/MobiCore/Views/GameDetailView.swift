import PhotosUI
import SwiftUI

/// Ảnh bìa, thông tin và mọi thao tác với một trò chơi đã cài.
struct GameDetailView: View {

    let suiteId: String

    @EnvironmentObject private var client: MobiCoreClient
    /// The shelves, re-read after every change so the ticks follow.
    @State private var shelves: [Collection] = []
    @State private var newShelf = ""

    private var onShelves: [Collection] { shelves.filter { $0.holds } }
    @Environment(\.dismiss) private var dismiss
    @State private var confirmUninstall = false
    @State private var playing = false
    @State private var renaming = false
    @State private var newTitle = ""
    @State private var coverPick: PhotosPickerItem?
    @State private var coverError: String?
    @State private var midlets: [MidletChoice] = []

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
                            // Whether the game runs at all, decided at
                            // import: a J2ME game missing a package does not
                            // run badly, it fails to start with nothing on
                            // screen to explain it.
                            CompatibilityLabel(level: game.settings?.compatibility ?? 0)
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

                    if client.hasSaveState(suiteId) {
                        SectionCard(title: "ĐANG CHƠI DỞ") {
                            HStack(spacing: 14) {
                                if let shot = client.saveStateThumbnail(suiteId) {
                                    shot.resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .frame(width: 72, height: 96)
                                }
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("Chơi tiếp từ chỗ đã dừng")
                                        .font(.subheadline)
                                        .foregroundStyle(Palette.text)
                                    Text("Tự lưu khi bạn thoát game")
                                        .font(.caption)
                                        .foregroundStyle(Palette.textDim)
                                    Button("Chơi tiếp") { playing = true }
                                        .buttonStyle(.borderedProminent)
                                        .tint(Palette.accentDim)
                                    Button("Bỏ và chơi lại từ đầu") {
                                        client.deleteSaveState(suiteId)
                                    }
                                    .font(.caption)
                                    .foregroundStyle(Palette.textDim)
                                }
                            }
                        }
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
                            FieldRow(label: "Màn hình",
                                     value: game.settings?.device.resolution ?? "—")
                            FieldRow(label: "Phóng ảnh", value: game.settings?.scaleModeName ?? "—")
                            FieldRow(label: "Đã chơi",
                                     value: game.settings?.playedName ?? "chưa chơi")
                            FieldRow(label: "Số lần chơi",
                                     value: String(game.settings?.playCount ?? 0))
                        }
                    }

                    NavigationLink {
                        SavesView(suiteId: suiteId)
                    } label: {
                        // A shelf is how a person finds a game whose name they do
                    // not remember, so putting one on a shelf belongs here.
                    SectionCard(title: "BỘ SƯU TẬP",
                                trailing: onShelves.isEmpty ? nil : "\(onShelves.count)") {
                        VStack(alignment: .leading, spacing: 8) {
                            if shelves.isEmpty {
                                Text("Chưa có bộ sưu tập nào. Tạo một cái để xếp game vào.")
                                    .font(.footnote)
                                    .foregroundStyle(Palette.textDim)
                            }
                            ForEach(shelves) { collection in
                                HStack {
                                    Text(collection.name)
                                        .font(.footnote)
                                        .foregroundStyle(Palette.text)
                                    Spacer()
                                    Image(systemName: collection.holds
                                          ? "checkmark.circle.fill" : "circle")
                                        .foregroundStyle(collection.holds
                                                         ? Palette.accent : Palette.textDim)
                                }
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    client.toggleCollection(collection.name, for: suiteId)
                                    shelves = client.collections(for: suiteId)
                                }
                            }
                            HStack {
                                TextField("Tên bộ sưu tập mới", text: $newShelf)
                                    .font(.footnote)
                                Button("Thêm") {
                                    let name = newShelf.trimmingCharacters(in: .whitespaces)
                                    guard !name.isEmpty else { return }
                                    client.createCollection(name)
                                    client.toggleCollection(name, for: suiteId)
                                    newShelf = ""
                                    shelves = client.collections(for: suiteId)
                                }
                                .font(.footnote)
                                .foregroundStyle(newShelf.trimmingCharacters(in: .whitespaces)
                                                 .isEmpty ? Palette.textDim : Palette.accent)
                            }
                        }
                    }

                    SectionCard(title: "DỮ LIỆU LƯU", trailing: "\(game.stores) kho") {
                            Text(game.stores == 0
                                 ? "Trò chơi này chưa lưu gì."
                                 : "Quản lý kho bản ghi và bản sao lưu")
                                .font(.footnote)
                                .foregroundStyle(game.stores == 0 ? Palette.textDim : Palette.accent)
                        }
                    }
                    .buttonStyle(.plain)

                    if midlets.count > 1 {
                        // Only when there is more than one: a picker over a
                        // list of one is a question with a single answer.
                        SectionCard(title: "TRONG GÓI NÀY",
                                    trailing: "\(midlets.count) ứng dụng") {
                            VStack(alignment: .leading, spacing: 6) {
                                ForEach(midlets) { midlet in
                                    Button {
                                        client.chooseMidlet(midlet.className, for: suiteId)
                                        midlets = client.midlets(suiteId)
                                    } label: {
                                        Text(midlet.name)
                                            .font(midlet.chosen
                                                  ? .subheadline.weight(.semibold)
                                                  : .subheadline)
                                            .foregroundStyle(midlet.chosen
                                                             ? Palette.accent : Palette.text)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }

                    NavigationLink {
                        SaveSlotsView(suiteId: suiteId)
                    } label: {
                        SectionCard(title: "Ô LƯU TRẠNG THÁI") {
                            Text("Xem và xoá các ô đã lưu")
                                .font(.footnote)
                                .foregroundStyle(Palette.accent)
                        }
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        ScreenshotsView(suiteId: suiteId)
                    } label: {
                        SectionCard(title: "ẢNH CHỤP") {
                            Text("Xem lại ảnh đã chụp trong lúc chơi")
                                .font(.footnote)
                                .foregroundStyle(Palette.accent)
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
        .onAppear {
            midlets = client.midlets(suiteId)
            shelves = client.collections(for: suiteId)
        }
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


/// "Chạy tốt", or exactly what is missing.
private struct CompatibilityLabel: View {

    let level: Int

    var body: some View {
        Label(text, systemImage: level >= 2 ? "xmark.circle.fill" : "checkmark.circle.fill")
            .font(.footnote.weight(.semibold))
            .foregroundStyle(colour)
    }

    private var text: String {
        switch level {
        case 0: return "Chạy tốt"
        case 1: return "Thiếu vài thứ"
        default: return "Chưa chạy được"
        }
    }

    private var colour: Color {
        switch level {
        case 0: return Palette.good
        case 1: return Palette.warn
        default: return Palette.bad
        }
    }
}
