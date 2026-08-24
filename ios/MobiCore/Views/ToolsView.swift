import SwiftUI

/// Công cụ nhà phát triển: xem mô tả, MIDlet, lớp Java và tài nguyên của một
/// bộ cài mà không cần chạy nó.
struct ToolsView: View {

    @EnvironmentObject private var client: MobiCoreClient
    @State private var selected: String?
    @State private var inspection: InspectResponse?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if client.games.isEmpty {
                    Text("Hãy cài một trò chơi để xem bên trong.")
                        .font(.footnote)
                        .foregroundStyle(Palette.textDim)
                } else {
                    SectionCard(title: "BỘ CÀI") {
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

                        SectionCard(title: "CÁC MIDLET", trailing: "\(inspection.midlets.count)") {
                            VStack(spacing: 6) {
                                ForEach(inspection.midlets) { midlet in
                                    FieldRow(label: midlet.name, value: midlet.className)
                                }
                            }
                        }

                        SectionCard(title: "LỚP JAVA", trailing: "\(inspection.classes.count)") {
                            VStack(alignment: .leading, spacing: 3) {
                                ForEach(inspection.classes.prefix(20), id: \.self) { name in
                                    Text(name).font(.caption2).foregroundStyle(Palette.textDim)
                                }
                            }
                        }

                        SectionCard(title: "TÀI NGUYÊN",
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
        .navigationTitle("Công cụ")
        .onAppear {
            if selected == nil, let first = client.games.first {
                selected = first.suiteId
                inspection = client.inspect(first.suiteId)
            }
        }
    }
}
