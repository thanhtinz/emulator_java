import SwiftUI

/// Kho bản ghi, bản sao lưu và đặt lại. Mọi thao tác xoá đều sao lưu trước.
struct SavesView: View {

    let suiteId: String

    @EnvironmentObject private var client: MobiCoreClient
    @State private var saves: SavesResponse?
    @State private var status: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                SectionCard(title: "KHO BẢN GHI", trailing: "\(saves?.stores.count ?? 0)") {
                    VStack(spacing: 6) {
                        if let stores = saves?.stores, !stores.isEmpty {
                            ForEach(stores) { store in
                                FieldRow(
                                    label: store.name,
                                    value: "\(store.records) bản ghi · \(store.bytes) B"
                                )
                            }
                        } else {
                            Text("Chưa lưu gì.")
                                .font(.footnote)
                                .foregroundStyle(Palette.textDim)
                        }
                    }
                }

                SectionCard(title: "SAO LƯU", trailing: "\(saves?.backups.count ?? 0)") {
                    VStack(alignment: .leading, spacing: 10) {
                        if let backups = saves?.backups, !backups.isEmpty {
                            ForEach(backups, id: \.self) { name in
                                FieldRow(label: name, value: "bản sao lưu")
                            }
                        } else {
                            Text("Chưa có bản sao lưu nào.")
                                .font(.footnote)
                                .foregroundStyle(Palette.textDim)
                        }
                        HStack(spacing: 10) {
                            Button("Sao lưu ngay") {
                                client.backup(suiteId)
                                status = "Đã tạo bản sao lưu"
                                reload()
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(Palette.accentDim)

                            Button("Khôi phục bản mới nhất") {
                                client.restoreLatest(suiteId)
                                status = client.lastError ?? "Đã khôi phục"
                                reload()
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }

                SectionCard(title: "ĐẶT LẠI") {
                    VStack(alignment: .leading, spacing: 6) {
                        Button("Xoá toàn bộ dữ liệu lưu") {
                            client.resetData(suiteId)
                            status = "Đã xoá, một bản sao lưu đã được tạo trước"
                            reload()
                        }
                        .font(.footnote)
                        .foregroundStyle(Palette.bad)
                        Text("Một bản sao lưu luôn được tạo trước khi xoá.")
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
        .navigationTitle("Dữ liệu lưu")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: reload)
    }

    private func reload() {
        saves = client.saves(suiteId)
        client.refresh()
    }
}
