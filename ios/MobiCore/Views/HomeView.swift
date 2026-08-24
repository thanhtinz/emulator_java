import SwiftUI
import UniformTypeIdentifiers

/// Home: recently played first, then favourites, then everything installed.
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
                        Text("RECENTLY PLAYED")
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
                                                .font(.caption)
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
                        Text("FAVOURITES")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(Palette.textDim)
                        ForEach(client.favourites) { game in
                            GameRowLink(game: game)
                        }
                    }

                    Text("ALL GAMES")
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
        .navigationTitle("MobiCore")
        .navigationDestination(for: String.self) { GameDetailView(suiteId: $0) }
        .toolbar {
            Button("Import") { importing = true }
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
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Palette.text)
                            .lineLimit(1)
                        Text("\(game.vendor) · \(game.version)")
                            .font(.caption)
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
            Text("No games yet")
                .font(.headline)
                .foregroundStyle(Palette.text)
            Text("Import a .jar file, or a .jar and .jad pair, to get started.")
                .font(.footnote)
                .foregroundStyle(Palette.textDim)
                .multilineTextAlignment(.center)
            Button("Import game", action: onImport)
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
