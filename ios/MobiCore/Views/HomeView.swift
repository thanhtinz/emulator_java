import SwiftUI
import UniformTypeIdentifiers

/// Trang chủ: một danh sách phẳng những trò chơi đã cài.
///
/// Shaped after the emulators people already use — the games, sorted, with
/// find, sort and everything else on the toolbar, and the one button that adds
/// a game. The sections and cards this screen used to carry were the app
/// talking about itself; every row spent on a heading is a row not spent on a
/// game.
struct HomeView: View {

    @EnvironmentObject private var client: MobiCoreClient
    @State private var importing = false
    @State private var query = ""
    @State private var showingTools = false
    @State private var showingSettings = false
    /// The link sheet, and what has been typed into it.
    /// Which shelf the library is filtered to, or empty for all of them.
    @State private var shelf = ""

    private var shelves: [Collection] { client.collections() }

    private var shown: [Game] {
        let found = query.trimmingCharacters(in: .whitespaces).isEmpty
            ? client.sorted(client.games, by: client.librarySort)
            : client.search(query, sort: client.librarySort)
        if shelf.isEmpty {
            return found
        }
        let ids = Set(client.gamesOn(shelf).map { $0.suiteId })
        return found.filter { ids.contains($0.suiteId) }
    }

    var body: some View {
        Group {
            if client.games.isEmpty {
                EmptyLibraryView(onImport: { importing = true })
            } else {
                // Shelves only appear once there are some: a row of one chip
                // saying "Tất cả" tells nobody anything.
                if !shelves.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ShelfChip(label: "Tất cả", selected: shelf.isEmpty) { shelf = "" }
                            ForEach(shelves) { collection in
                                ShelfChip(label: collection.name,
                                          selected: collection.name == shelf) {
                                    shelf = collection.name == shelf ? "" : collection.name
                                }
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                    }
                }
                List {
                    // The game they were playing, offered before the list
                    // they would have to search. Only while nothing is being
                    // searched or filtered: then the list is the answer to a
                    // question, and this would answer a different one.
                    if query.isEmpty, shelf.isEmpty, let card = client.continueCard(),
                       let game = card.game {
                        NavigationLink(value: game.suiteId) {
                            HStack(spacing: 12) {
                                GameArtwork(title: game.title,
                                            image: client.artwork(game.suiteId), size: 48)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(card.action ?? "Chơi tiếp")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(Palette.accent)
                                    Text(game.title)
                                        .font(.headline)
                                        .foregroundStyle(Palette.text)
                                    Text(card.resumes == true
                                         ? "Tiếp tục từ chỗ đã lưu" : "Bắt đầu lại từ đầu")
                                        .font(.caption)
                                        .foregroundStyle(Palette.textDim)
                                }
                                Spacer()
                                Image(systemName: "play.fill")
                                    .foregroundStyle(Palette.accent)
                            }
                            .padding(.vertical, 4)
                        }
                        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                        .listRowBackground(Palette.surfaceAlt)
                    }
                    ForEach(shown) { game in
                        GameRowLink(game: game)
                            .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8,
                                                      trailing: 16))
                            .listRowBackground(Palette.background)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .background(Palette.background)
        // Importing is the first thing a new install must do and the reason
        // for most later visits, so it gets the one floating button on the
        // screen: small, always in the same corner.
        .overlay(alignment: .bottomTrailing) {
            Button {
                importing = true
            } label: {
                Image(systemName: "plus")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(Palette.background)
                    .frame(width: 56, height: 56)
                    .background(Palette.accent, in: Circle())
                    .shadow(radius: 4, y: 2)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Nhập trò chơi")
            .padding(16)
        }
        .searchable(text: $query, prompt: "Tìm trò chơi")
        .navigationTitle("MobiCore")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // Sort, then everything else: the two things a toolbar over a
            // list is for.
            Menu {
                Picker("Sắp xếp", selection: Binding(
                    get: { client.librarySort },
                    set: { client.setLibrarySort($0) }
                )) {
                    Text("Theo tên").tag(0)
                    Text("Vừa chơi").tag(1)
                    Text("Nhà phát hành").tag(2)
                    Text("Chơi lâu nhất").tag(3)
                }
            } label: {
                Image(systemName: "arrow.up.arrow.down")
            }
            .accessibilityLabel("Sắp xếp")

            Menu {
                Button {
                    client.cycleTheme()
                } label: {
                    Label(Palette.dark ? "Giao diện sáng" : "Giao diện tối",
                          systemImage: Palette.dark ? "sun.max" : "moon")
                }
                Button {
                    showingTools = true
                } label: {
                    Label("Công cụ", systemImage: "wrench.and.screwdriver")
                }
                Button {
                    showingSettings = true
                } label: {
                    Label("Cài đặt", systemImage: "gearshape")
                }
            } label: {
                Image(systemName: "ellipsis")
            }
            .accessibilityLabel("Thêm")
        }
        .navigationDestination(for: String.self) { GameDetailView(suiteId: $0) }
        .navigationDestination(isPresented: $showingTools) { ToolsView() }
        .navigationDestination(isPresented: $showingSettings) { SettingsView() }
        .fileImporter(
            isPresented: $importing,
            allowedContentTypes: MobiCoreTypes.importable,
            allowsMultipleSelection: true
        ) { result in
            handleImport(result)
        }
    }

    /// Everything picked, not just the first. A collection is a folder of
    /// games; the core pairs each descriptor with its archive, unpacks a zip
    /// of games, and reports on every file separately.
    private func handleImport(_ result: Result<[URL], Error>) {
        guard case .success(let urls) = result else { return }
        var names: [String] = []
        var payloads: [Data] = []
        for url in urls {
            // Files picked from another app are read inside a security scope.
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: url) else { continue }
            names.append(url.lastPathComponent)
            payloads.append(data)
        }
        guard !names.isEmpty else { return }
        client.importMany(names: names, payloads: payloads)
    }
}

/// One row in a game list.
struct GameRowLink: View {
    let game: Game
    @EnvironmentObject private var client: MobiCoreClient

    /// A flat row rather than a card: a list of eighty games in eighty cards
    /// is eighty rectangles to look past, and the icon already tells one row
    /// from the next.
    var body: some View {
        NavigationLink(value: game.suiteId) {
            HStack(spacing: 14) {
                GameArtwork(title: game.title, image: client.artwork(game.suiteId), size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(game.title)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(Palette.text)
                            .lineLimit(1)
                        if game.settings?.favourite == true {
                            Image(systemName: "star.fill")
                                .font(.caption2)
                                .foregroundStyle(Palette.warn)
                        }
                    }
                    HStack {
                        Text(game.vendor)
                            .font(.caption)
                            .foregroundStyle(Palette.textDim)
                            .lineLimit(1)
                        Spacer()
                        Text(game.version)
                            .font(.caption)
                            .foregroundStyle(Palette.textDim)
                    }
                }
            }
        }
        .buttonStyle(.plain)
    }
}

struct EmptyLibraryView: View {
    let onImport: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "gamecontroller")
                .font(.system(size: 44))
                .foregroundStyle(Palette.textDim)
            Text("Chưa có trò chơi nào")
                .font(.headline)
                .foregroundStyle(Palette.text)
            Text("Chọn một tệp .jar, hoặc cặp .jar và .jad, để bắt đầu.")
                .font(.footnote)
                .foregroundStyle(Palette.textDim)
                .multilineTextAlignment(.center)
            Button("Nhập trò chơi", action: onImport)
                .buttonStyle(.borderedProminent)
                .tint(Palette.accentDim)
                .padding(.top, 8)
        }
        .padding(32)
    }
}

enum MobiCoreTypes {
    /// JAR and JAD have no system-declared types on iOS, so the picker accepts
    /// archives and plain data and the importer sniffs the bytes.
    static let importable: [UTType] = [.zip, .data, .plainText]
}

/// One shelf, as a chip over the list.
private struct ShelfChip: View {
    let label: String
    let selected: Bool
    let onTap: () -> Void

    var body: some View {
        Text(label)
            .font(.footnote)
            .foregroundStyle(selected ? Palette.background : Palette.text)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(selected ? Palette.accent : Palette.surfaceAlt,
                        in: RoundedRectangle(cornerRadius: 14))
            .onTapGesture(perform: onTap)
    }
}
