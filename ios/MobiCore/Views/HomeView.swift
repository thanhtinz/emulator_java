import SwiftUI
import UniformTypeIdentifiers

/// Trang chủ: vừa chơi, yêu thích, rồi toàn bộ trò chơi đã cài.
struct HomeView: View {

    @EnvironmentObject private var client: MobiCoreClient
    @State private var importing = false
    @State private var query = ""
    private var searching: Bool { !query.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        ScrollView {
            if client.games.isEmpty {
                EmptyLibraryView(onImport: { importing = true })
                    .padding(.top, 60)
            } else {
                VStack(alignment: .leading, spacing: 18) {
                    // Typing replaces the whole screen with the matches:
                    // someone searching has stopped browsing. There is no
                    // separate library tab — it only ever held this box over
                    // the same games.
                    if searching {
                        Picker("Sắp xếp", selection: Binding(
                            get: { client.librarySort },
                            set: { client.setLibrarySort($0) }
                        )) {
                            Text("Tên").tag(0)
                            Text("Vừa chơi").tag(1)
                            Text("Nhà phát hành").tag(2)
                        }
                        .pickerStyle(.segmented)

                        let matches = client.search(query, sort: client.librarySort)
                        Text("\(matches.count) kết quả")
                            .font(.caption2)
                            .foregroundStyle(Palette.textDim)
                        ForEach(matches) { game in
                            GameRowLink(game: game)
                        }
                        if matches.isEmpty {
                            Text("Không tìm thấy. Thử một từ khoá khác.")
                                .font(.footnote)
                                .foregroundStyle(Palette.textDim)
                        }
                    } else {
                    if !client.recent.isEmpty {
                        Text("VỪA CHƠI")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(Palette.textDim)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(client.recent) { game in
                                    NavigationLink(value: game.suiteId) {
                                        VStack(spacing: 6) {
                                            GameArtwork(
                                                title: game.title,
                                                image: client.artwork(game.suiteId),
                                                size: 84
                                            )
                                            Text(game.title)
                                                .font(.footnote)
                                                .foregroundStyle(Palette.text)
                                                .lineLimit(1)
                                                .frame(width: 88)
                                        }
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }

                    if !client.favourites.isEmpty {
                        Text("YÊU THÍCH")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(Palette.textDim)
                        ForEach(client.favourites) { game in
                            GameRowLink(game: game)
                        }
                    }

                    Text("TẤT CẢ TRÒ CHƠI")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(Palette.textDim)
                    ForEach(client.games) { game in
                        GameRowLink(game: game)
                    }
                    }
                }
                .padding(16)
            }
        }
        .background(Palette.background)
        // Importing is the first thing a new install must do and the reason
        // for most later visits, so it gets the one floating button on the
        // screen: small, always in the same corner, and never taking a band
        // of the screen away from the games.
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
        .searchable(text: $query, prompt: "Tìm theo tên hoặc nhà phát hành")
        .navigationTitle("MobiCore")
        .toolbar {
            // One tap, always in the same corner: light and dark is the
            // setting people change often enough to want it on the way.
            Button {
                client.cycleTheme()
            } label: {
                Image(systemName: Palette.dark ? "sun.max" : "moon")
            }
            .accessibilityLabel("Đổi giao diện sáng tối")
        }
        .navigationDestination(for: String.self) { GameDetailView(suiteId: $0) }
        .toolbar {
            Button {
                importing = true
            } label: {
                Label("Nhập trò chơi", systemImage: "square.and.arrow.down")
            }
        }
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

    var body: some View {
        NavigationLink(value: game.suiteId) {
            SectionCard {
                HStack(spacing: 12) {
                    GameArtwork(title: game.title, image: client.artwork(game.suiteId))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(game.title)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(Palette.text)
                            .lineLimit(1)
                        Text("\(game.vendor) · \(game.version)")
                            .font(.footnote)
                            .foregroundStyle(Palette.textDim)
                            .lineLimit(1)
                    }
                    Spacer()
                    Chip(text: game.settings?.device.resolution ?? game.profile)
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
