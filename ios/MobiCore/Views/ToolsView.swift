import SwiftUI

/// Developer tools: inspect a suite's descriptor, MIDlets, classes and
/// resources without running it.
struct ToolsView: View {

    @EnvironmentObject private var client: MobiCoreClient
    @State private var selected: String?
    @State private var inspection: InspectResponse?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if client.games.isEmpty {
                    Text("Install a game to inspect it.")
                        .font(.footnote)
                        .foregroundStyle(Palette.textDim)
                } else {
                    SectionCard(title: "SUITE") {
                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(client.games) { game in
                                Button(game.title) {
                                    selected = game.suiteId
                                    inspection = client.inspect(game.suiteId)
                                }
                                .font(.footnote)
                                .tint(game.suiteId == selected ? Palette.accent : Palette.text)
                            }
                        }
                    }

                    if let inspection {
                        SectionCard(title: "MANIFEST / JAD") {
                            VStack(spacing: 6) {
                                ForEach(inspection.attributes.sorted(by: { $0.key < $1.key }),
                                        id: \.key) { key, value in
                                    FieldRow(label: key, value: value)
                                }
                            }
                        }

                        SectionCard(title: "MIDLETS", trailing: "\(inspection.midlets.count)") {
                            VStack(spacing: 6) {
                                ForEach(inspection.midlets) { midlet in
                                    FieldRow(label: midlet.name, value: midlet.className)
                                }
                            }
                        }

                        SectionCard(title: "CLASSES", trailing: "\(inspection.classes.count)") {
                            VStack(alignment: .leading, spacing: 3) {
                                ForEach(inspection.classes.prefix(20), id: \.self) { name in
                                    Text(name).font(.caption2).foregroundStyle(Palette.textDim)
                                }
                            }
                        }

                        SectionCard(title: "RESOURCES",
                                    trailing: byteString(inspection.uncompressed)) {
                            VStack(spacing: 6) {
                                ForEach(inspection.resources) { resource in
                                    FieldRow(label: resource.name,
                                             value: byteString(Int64(resource.bytes)))
                                }
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(Palette.background)
        .navigationTitle("Tools")
        .onAppear {
            if selected == nil, let first = client.games.first {
                selected = first.suiteId
                inspection = client.inspect(first.suiteId)
            }
        }
    }
}
