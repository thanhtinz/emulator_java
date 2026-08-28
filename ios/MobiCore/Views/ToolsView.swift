import SwiftUI
import UniformTypeIdentifiers

/// Công cụ nhà phát triển: xem mô tả, MIDlet, lớp Java và tài nguyên của một
/// bộ cài mà không cần chạy nó.
struct ToolsView: View {

    @EnvironmentObject private var client: MobiCoreClient
    @State private var selected: String?
    @State private var inspection: InspectResponse?
    @State private var box: ResourceBox?
    @State private var tab = 0
    /// Tệp đang chờ người chơi chọn thứ thay vào.
    @State private var replacing: GameResource?

    var body: some View {
        VStack(spacing: 0) {
            if client.games.isEmpty {
                Text("Hãy cài một trò chơi để xem bên trong.")
                    .font(.footnote)
                    .foregroundStyle(Palette.textDim)
                    .padding(16)
                Spacer()
            } else {
                if client.games.count > 1 {
                    Picker("Bộ cài", selection: Binding(
                        get: { selected ?? client.games.first?.suiteId ?? "" },
                        set: { open($0) }
                    )) {
                        ForEach(client.games) { game in
                            Text(game.title).tag(game.suiteId)
                        }
                    }
                    .pickerStyle(.menu)
                    .padding(.horizontal, 16)
                }

                // Chia thẻ chứ không xếp thành một trang dài: bốn phần này
                // trả lời bốn câu hỏi khác nhau, và người đang tìm một tấm
                // ảnh để thay không việc gì phải cuộn qua danh sách lớp Java.
                Picker("", selection: $tab) {
                    Text("Tài nguyên").tag(0)
                    Text("Bộ cài").tag(1)
                    Text("MIDlet").tag(2)
                    Text("Lớp Java").tag(3)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)

                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        switch tab {
                        case 0: resourcesTab
                        case 1: suiteTab
                        case 2: midletsTab
                        default: classesTab
                        }
                    }
                    .padding(16)
                }
            }
        }
        .background(Palette.background)
        .navigationTitle("Công cụ")
        .fileImporter(isPresented: Binding(
            get: { replacing != nil },
            set: { if !$0 { replacing = nil } }
        ), allowedContentTypes: [.data]) { result in
            guard let target = replacing, let suiteId = selected else { return }
            replacing = nil
            if case .success(let url) = result {
                let opened = url.startAccessingSecurityScopedResource()
                defer { if opened { url.stopAccessingSecurityScopedResource() } }
                if let data = try? Data(contentsOf: url) {
                    client.replaceResource(target.path, with: data, in: suiteId)
                    box = client.resources(suiteId)
                }
            }
        }
        .onAppear {
            if selected == nil, let first = client.games.first {
                open(first.suiteId)
            }
        }
    }

    private func open(_ suiteId: String) {
        selected = suiteId
        inspection = client.inspect(suiteId)
        box = client.resources(suiteId)
    }

    /// Kho tài nguyên: mọi thứ trong tệp game, và nút để thay.
    private var resourcesTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let box {
                Text("\(box.count) tệp trong game. Thay tệp nào thì tệp đó được phủ lên "
                     + "khi chơi; bản gốc trong bộ cài không bị đụng tới.")
                    .font(.caption2)
                    .foregroundStyle(Palette.textDim)

                ForEach(box.resources) { resource in
                    SectionCard(title: resource.kindName.uppercased(),
                                trailing: resource.replaced ? "đã thay" : resource.format) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(resource.path)
                                .font(.footnote)
                                .foregroundStyle(Palette.text)
                            Text(detail(resource))
                                .font(.caption2)
                                .foregroundStyle(Palette.textDim)
                            HStack(spacing: 16) {
                                Button("Thay tệp") { replacing = resource }
                                    .font(.footnote)
                                    .tint(Palette.accent)
                                if resource.replaced, let suiteId = selected {
                                    Button("Trả về bản gốc") {
                                        client.restoreResource(resource.path, in: suiteId)
                                        box = client.resources(suiteId)
                                    }
                                    .font(.footnote)
                                    .tint(Palette.bad)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private func detail(_ resource: GameResource) -> String {
        var text = byteString(Int64(resource.bytes))
        if resource.width > 0 {
            text += "  ·  \(resource.width)×\(resource.height)"
        }
        if resource.replaced {
            text += "  ·  bản của \(resource.replacedBy)"
        }
        return text
    }

    private var suiteTab: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let inspection {
                SectionCard(title: "MANIFEST / JAD") {
                    VStack(spacing: 6) {
                        ForEach(inspection.attributes.sorted(by: { $0.key < $1.key }),
                                id: \.key) { key, value in
                            FieldRow(label: key, value: value)
                        }
                    }
                }
            }
        }
    }

    private var midletsTab: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let inspection {
                SectionCard(title: "CÁC MIDLET", trailing: "\(inspection.midlets.count)") {
                    VStack(spacing: 6) {
                        ForEach(inspection.midlets) { midlet in
                            FieldRow(label: midlet.name, value: midlet.className)
                        }
                    }
                }
            }
        }
    }

    private var classesTab: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let inspection {
                SectionCard(title: "LỚP JAVA", trailing: "\(inspection.classes.count)") {
                    VStack(alignment: .leading, spacing: 3) {
                        ForEach(inspection.classes, id: \.self) { name in
                            Text(name).font(.caption2).foregroundStyle(Palette.textDim)
                        }
                    }
                }
            }
        }
    }
}

