import SwiftUI
import UniformTypeIdentifiers

/// Trang chủ: vừa chơi, yêu thích, rồi toàn bộ trò chơi đã cài.
struct HomeView: View {

    @EnvironmentObject private var client: MobiCoreClient
    @State private var importing = false

    var body: some View {
        ScrollView {
            if client.games.isEmpty {
                EmptyLibraryView(onImport: { importing = true })
                    .padding(.top, 60)
            } else {
                VStack(alignment: .leading, spacing: 18) {
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
        .navigationTitle("MobiCore")
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

    private func handleImport(_ result: Result<[URL], Error>) {
        guard case .success(let urls) = result else { return }
        var jar: Data?
        var descriptor: Data?
        for url in urls {
            guard let data = try? Data(contentsOf: url) else { continue }
            // A JAR is a ZIP, so it starts with "PK"; a JAD is plain text.
            if data.count > 2, data[0] == 0x50, data[1] == 0x4B {
                jar = data
            } else {
                descriptor = data
            }
        }
        guard let jar else { return }
        client.importSuite(jar: jar, descriptor: descriptor)
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
