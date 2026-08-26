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

        /// Matches `GameLibrary.SORT_*` in the core.
        var coreValue: Int {
            switch self {
            case .title: return 0
            case .recent: return 1
            case .vendor: return 2
            }
        }

        static func from(_ coreValue: Int) -> SortMode {
            switch coreValue {
            case 1: return .recent
            case 2: return .vendor
            default: return .title
            }
        }
    }

    /// Filtering and ordering are the core's, not this view's: the same query
    /// then gives the same list on both platforms, marks ignored and renamed
    /// games found under either name.
    private var visible: [Game] {
        client.search(query, sort: sort.coreValue)
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
        .onAppear { sort = SortMode.from(client.librarySort) }
        .onChange(of: sort) { _, mode in client.setLibrarySort(mode.coreValue) }
        .navigationTitle("Thư viện")
        .navigationDestination(for: String.self) { GameDetailView(suiteId: $0) }
    }
}
