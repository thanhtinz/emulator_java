import SwiftUI

/// Cover, metadata and everything you can do with one installed game.
struct GameDetailView: View {

    let suiteId: String

    @EnvironmentObject private var client: MobiCoreClient
    @Environment(\.dismiss) private var dismiss
    @State private var confirmUninstall = false
    @State private var playing = false

    private var game: Game? { client.game(suiteId) }

    var body: some View {
        ScrollView {
            if let game {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .top, spacing: 14) {
                        GameArtwork(title: game.title, image: client.artwork(suiteId), size: 84)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(game.title)
                                .font(.title3.weight(.bold))
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
                            Text("Play").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Palette.accentDim)

                        NavigationLink {
                            GameSettingsView(suiteId: suiteId)
                        } label: {
                            Text("Settings").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }

                    SectionCard(title: "DETAILS") {
                        VStack(spacing: 6) {
                            FieldRow(label: "Version", value: game.version)
                            FieldRow(label: "Suite id", value: game.suiteId)
                            FieldRow(label: "Size", value: byteString(game.jarSize))
                            FieldRow(label: "Device",
                                     value: game.settings?.device.resolution ?? "—")
                            FieldRow(label: "Scaling", value: game.settings?.scaleModeName ?? "—")
                            FieldRow(label: "Times played",
                                     value: String(game.settings?.playCount ?? 0))
                        }
                    }

                    NavigationLink {
                        SavesView(suiteId: suiteId)
                    } label: {
                        SectionCard(title: "SAVES", trailing: "\(game.stores) store") {
                            Text(game.stores == 0
                                 ? "This game has not saved anything yet."
                                 : "Manage record stores and backups")
                                .font(.footnote)
                                .foregroundStyle(game.stores == 0 ? Palette.textDim : Palette.accent)
                        }
                    }
                    .buttonStyle(.plain)

                    SectionCard(title: "DANGER ZONE") {
                        VStack(alignment: .leading, spacing: 6) {
                            Button(confirmUninstall ? "Tap again to uninstall" : "Uninstall game") {
                                if confirmUninstall {
                                    client.uninstall(suiteId)
                                    dismiss()
                                } else {
                                    confirmUninstall = true
                                }
                            }
                            .font(.footnote)
                            .foregroundStyle(Palette.bad)
                            Text("Saves are backed up automatically before anything is removed.")
                                .font(.caption2)
                                .foregroundStyle(Palette.textDim)
                        }
                    }
                }
                .padding(16)
            } else {
                Text("This game is no longer installed.")
                    .foregroundStyle(Palette.textDim)
                    .padding(40)
            }
        }
        .background(Palette.background)
        .navigationTitle(game?.title ?? "Game")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button {
                client.toggleFavourite(suiteId)
            } label: {
                Image(systemName: (game?.settings?.favourite ?? false) ? "star.fill" : "star")
            }
            .tint(Palette.warn)
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
