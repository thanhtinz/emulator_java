import SwiftUI

/// Application-wide information: what the emulator supports, where data lives
/// and how the sandbox is enforced.
struct SettingsView: View {

    @EnvironmentObject private var client: MobiCoreClient

    private var totalBytes: Int64 {
        client.games.reduce(0) { $0 + $1.jarSize }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                SectionCard(title: "EMULATOR") {
                    VStack(spacing: 6) {
                        FieldRow(label: "Configuration", value: "CLDC 1.0 / 1.1")
                        FieldRow(label: "Profile", value: "MIDP 1.0 / 2.0")
                        FieldRow(label: "Rendering", value: "Nearest neighbour, integer scale")
                    }
                }

                SectionCard(title: "STORAGE") {
                    VStack(alignment: .leading, spacing: 6) {
                        FieldRow(label: "Installed games", value: "\(client.games.count)")
                        FieldRow(label: "Suites on disk", value: byteString(totalBytes))
                        Text(client.storageRoot)
                            .font(.caption2)
                            .foregroundStyle(Palette.textDim)
                            .lineLimit(2)
                            .truncationMode(.middle)
                    }
                }

                SectionCard(title: "SECURITY") {
                    VStack(spacing: 6) {
                        FieldRow(label: "Sandbox", value: "One directory per game")
                        FieldRow(label: "Filesystem access", value: "Import only")
                        FieldRow(label: "Network", value: "Off until a profile allows it")
                    }
                }

                SectionCard(title: "ABOUT") {
                    VStack(alignment: .leading, spacing: 6) {
                        FieldRow(label: "MobiCore", value: "1.0")
                        Text("A J2ME game platform: run, manage and customise Java ME games "
                             + "on a modern device.")
                            .font(.caption)
                            .foregroundStyle(Palette.textDim)
                    }
                }
            }
            .padding(16)
        }
        .background(Palette.background)
        .navigationTitle("Settings")
    }
}
