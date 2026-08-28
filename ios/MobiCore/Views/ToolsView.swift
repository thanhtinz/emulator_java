import SwiftUI
import UniformTypeIdentifiers

/// Công cụ nhà phát triển: xem mô tả, MIDlet, lớp Java và tài nguyên của một
/// bộ cài mà không cần chạy nó.
struct ToolsView: View {

    @EnvironmentObject private var client: MobiCoreClient
    @State private var selected: String?
    /// Tệp đang chờ người chơi chọn thứ thay vào.
    /// Vòng tìm số vàng: lần đầu hỏi số đang thấy, lần sau hỏi số mới.
    @State private var scan: SaveScan?
    @State private var round = 0
    @State private var typed = ""
    @State private var note = ""
    /// Bảng vật phẩm và ô tìm kiếm của nó.
    @State private var table: ItemTable?
    @State private var query = ""
    @State private var amount = ""
    @State private var chosen: String?
    @State private var newName = ""

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

                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        treasureTab
                    }
                    .padding(16)
                }
            }
        }
        .background(Palette.background)
        .navigationTitle("Vật phẩm")
        .onAppear {
            if selected == nil, let first = client.games.first {
                open(first.suiteId)
            }
        }
    }

    private func open(_ suiteId: String) {
        selected = suiteId
        table = client.items(suiteId, matching: query)
    }

    /// Bảng vật phẩm: tìm theo tên, gõ số lượng, gửi vào game.
    ///
    /// Phần lưu không có nhãn, nên tìm ra một con số là việc mất công — làm
    /// một lần rồi đặt tên và cất đi, từ đó chỉ còn gõ số lượng.
    private var treasureTab: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionCard(title: "VẬT PHẨM ĐÃ TÌM ĐƯỢC",
                        trailing: "\(table?.count ?? 0) thứ") {
                VStack(alignment: .leading, spacing: 8) {
                    TextField("Tìm vật phẩm…", text: $query)
                        .textFieldStyle(.roundedBorder)
                        .onChange(of: query) { _ in reloadItems() }

                    if (table?.items.isEmpty ?? true) {
                        Text("Chưa có vật phẩm nào. Dùng phần dưới để tìm số vàng, số thuốc… "
                             + "rồi đặt tên cho nó.")
                            .font(.caption2)
                            .foregroundStyle(Palette.textDim)
                    }
                    ForEach(table?.items ?? []) { item in
                        Button {
                            chosen = item.id
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                HStack {
                                    Text(item.name)
                                        .font(.subheadline.weight(
                                            item.id == chosen ? .semibold : .regular))
                                        .foregroundStyle(item.id == chosen ? Palette.accent
                                                                           : Palette.text)
                                    Spacer()
                                    Text("\(item.amount)")
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(Palette.accent)
                                }
                                Text("\(item.places) chỗ trong phần lưu  ·  "
                                     + "nhiều nhất \(item.ceiling)")
                                    .font(.caption2)
                                    .foregroundStyle(Palette.textDim)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            if let chosen, let item = table?.items.first(where: { $0.id == chosen }) {
                SectionCard(title: "GỬI VÀO GAME", trailing: item.name) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            TextField("Số lượng", text: $amount)
                                .keyboardType(.numberPad)
                                .textFieldStyle(.roundedBorder)
                            Button("Gửi") { send(chosen) }
                                .buttonStyle(.borderedProminent)
                                .tint(Palette.accent)
                        }
                        if !note.isEmpty {
                            Text(note).font(.caption2).foregroundStyle(Palette.textDim)
                        }
                        Text("Phần lưu được sao lưu trước khi sửa.")
                            .font(.caption2)
                            .foregroundStyle(Palette.textDim)
                    }
                }
            }

            SectionCard(title: round == 0 ? "TÌM VẬT PHẨM MỚI" : "SỐ MỚI SAU KHI CHƠI TIẾP",
                        trailing: scan.map { "\($0.count) chỗ" }) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(round == 0
                         ? "Mở game, nhìn số đang có (vàng, thuốc, ngọc…) rồi gõ vào đây."
                         : "Chơi cho con số đổi đi rồi gõ số mới. Mỗi lần như vậy lọc bớt "
                           + "những chỗ chỉ tình cờ trùng số.")
                        .font(.caption2)
                        .foregroundStyle(Palette.textDim)
                    TextField("Con số đang thấy", text: $typed)
                        .keyboardType(.numberPad)
                        .textFieldStyle(.roundedBorder)
                    HStack(spacing: 16) {
                        Button(round == 0 ? "Tìm" : "Lọc tiếp") { search() }
                            .font(.footnote)
                            .tint(Palette.accent)
                        if round > 0 {
                            Button("Tìm lại từ đầu") {
                                client.clearSaveScan()
                                scan = nil
                                round = 0
                                typed = ""
                                note = ""
                            }
                            .font(.footnote)
                            .tint(Palette.textDim)
                        }
                    }
                    if round > 0 {
                        TextField("Đặt tên: Vàng, Thuốc hồi máu…", text: $newName)
                            .textFieldStyle(.roundedBorder)
                        Button("Cất vào bảng") { keep() }
                            .font(.footnote)
                            .tint(Palette.accent)
                    }
                    if !note.isEmpty && chosen == nil {
                        Text(note).font(.caption2).foregroundStyle(Palette.textDim)
                    }
                }
            }
        }
    }

    private func reloadItems() {
        guard let suiteId = selected else { return }
        table = client.items(suiteId, matching: query)
    }

    private func keep() {
        guard let suiteId = selected else { return }
        if client.keepItem(newName, in: suiteId)?.ok == true {
            newName = ""
            round = 0
            scan = nil
            note = "Đã cất. Từ giờ chỉ cần gõ số lượng rồi gửi."
            reloadItems()
        }
    }

    private func send(_ itemId: String) {
        guard let suiteId = selected, let value = Int64(amount) else {
            note = "Gõ số lượng trước đã."
            return
        }
        let result = client.sendItem(itemId, amount: value, in: suiteId)
        note = result?.ok == true
            ? "Đã gửi \(value). Mở lại game để thấy."
            : (result?.error ?? "Không gửi được số này.")
        reloadItems()
    }

    private func search() {
        guard let suiteId = selected, let value = Int64(typed) else { return }
        scan = round == 0
            ? client.scanSave(value, in: suiteId)
            : client.narrowSave(value, in: suiteId)
        round += 1
        note = scan?.summary ?? ""
        typed = ""
    }
}
