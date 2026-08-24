import SwiftUI

/// Thư viện: tìm kiếm và duyệt toàn bộ trò chơi đã cài.
struct LibraryView: View {

    @EnvironmentObject private var client: MobiCoreClient
    @State private var query = ""
    @State private var sort = SortMode.title

    enum SortMode: String, CaseIterable, Identifiable {
        case title = "Tên"
        case recent = "Vừa chơi"
        case vendor = "Nhà phát hành"

        var id: String { rawValue }
    }

    private var visible: [Game] {
        let matches = query.isEmpty ? client.games : client.games.filter {
            $0.title.localizedCaseInsensitiveContains(query)
                || $0.vendor.localizedCaseInsensitiveContains(query)
        }
        switch sort {
        case .title:
            return matches.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        case .vendor:
            return matches.sorted { $0.vendor.localizedCaseInsensitiveCompare($1.vendor) == .orderedAscending }
        case .recent:
            return matches.sorted { ($0.settings?.lastPlayed ?? 0) > ($1.settings?.lastPlayed ?? 0) }
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Picker("Sắp xếp", selection: $sort) {
                    ForEach(SortMode.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)

                if visible.isEmpty {
                    Text(client.games.isEmpty ? "Chưa cài trò chơi nào." : "Không có kết quả.")
                        .font(.footnote)
                        .foregroundStyle(Palette.textDim)
                        .padding(.top, 40)
                        .frame(maxWidth: .infinity)
                } else {
                    ForEach(visible) { GameRowLink(game: $0) }
                }
            }
            .padding(16)
        }
        .background(Palette.background)
        .searchable(text: $query, prompt: "Tìm theo tên hoặc nhà phát hành")
        .navigationTitle("Thư viện")
        .navigationDestination(for: String.self) { GameDetailView(suiteId: $0) }
    }
}
