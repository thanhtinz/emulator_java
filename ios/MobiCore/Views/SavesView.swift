import SwiftUI

/// Record stores, snapshots and reset. Every destructive action backs up first.
struct SavesView: View {

    let suiteId: String

    @EnvironmentObject private var client: MobiCoreClient
    @State private var saves: SavesResponse?
    @State private var status: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                SectionCard(title: "RECORD STORES", trailing: "\(saves?.stores.count ?? 0)") {
                    VStack(spacing: 6) {
                        if let stores = saves?.stores, !stores.isEmpty {
                            ForEach(stores) { store in
                                FieldRow(
                                    label: store.name,
                                    value: "\(store.records) records · \(store.bytes) B"
                                )
                            }
                        } else {
                            Text("Nothing saved yet.")
                                .font(.footnote)
                                .foregroundStyle(Palette.textDim)
                        }
                    }
                }

                SectionCard(title: "BACKUPS", trailing: "\(saves?.backups.count ?? 0)") {
                    VStack(alignment: .leading, spacing: 10) {
                        if let backups = saves?.backups, !backups.isEmpty {
                            ForEach(backups, id: \.self) { name in
                                FieldRow(label: name, value: "snapshot")
                            }
                        } else {
                            Text("No snapshots yet.")
                                .font(.footnote)
                                .foregroundStyle(Palette.textDim)
                        }
                        HStack(spacing: 10) {
                            Button("Back up now") {
                                client.backup(suiteId)
                                status = "Snapshot saved"
                                reload()
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(Palette.accentDim)

                            Button("Restore latest") {
                                client.restoreLatest(suiteId)
                                status = client.lastError ?? "Restored"
                                reload()
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }

                SectionCard(title: "RESET") {
                    VStack(alignment: .leading, spacing: 6) {
                        Button("Clear all saved data") {
                            client.resetData(suiteId)
                            status = "Cleared, a snapshot was taken first"
                            reload()
                        }
                        .font(.footnote)
                        .foregroundStyle(Palette.bad)
                        Text("A snapshot is taken automatically before anything is cleared.")
                            .font(.caption2)
                            .foregroundStyle(Palette.textDim)
                    }
                }

                if let status {
                    Text(status).font(.caption).foregroundStyle(Palette.good)
                }
            }
            .padding(16)
        }
        .background(Palette.background)
        .navigationTitle("Saves")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: reload)
    }

    private func reload() {
        saves = client.saves(suiteId)
        client.refresh()
    }
}
