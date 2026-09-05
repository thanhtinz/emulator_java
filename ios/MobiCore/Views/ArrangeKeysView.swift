import SwiftUI

/// Sắp xếp lại bàn phím ảo.
///
/// The keypad is laid out the way a handset was, because that is what the
/// thumbs of anyone who played these games are trained on. But no two hands
/// are the same and a phone is far bigger than a handset was: the fire key
/// that sat under one player's thumb is a stretch for the next.
///
/// The keys themselves are the control — drag one and it stays there. There is
/// no list of coordinates to fill in, because nobody knows where a key belongs
/// until their thumb is on it.
struct ArrangeKeysView: View {

    let suiteId: String

    @EnvironmentObject private var client: MobiCoreClient
    @State private var arrangement: KeypadArrangement?
    @State private var scale: Double = 100
    /// Những bộ bàn phím đã sắp, và bộ đang dùng.
    @State private var layouts: KeypadLayouts?
    @State private var naming = false
    @State private var layoutName = ""

    private var settings: GameSettings? { client.game(suiteId)?.settings }

    /// Offsets in the shape the keypad wants them: keys, not thousandths.
    private var placement: KeyPlacement {
        var offsets: [String: CGPoint] = [:]
        for key in arrangement?.keys ?? [] {
            offsets[key.button] = CGPoint(x: CGFloat(key.x) / 1000,
                                          y: CGFloat(key.y) / 1000)
        }
        return KeyPlacement(
            offsets: offsets,
            scale: arrangement?.scale ?? 100,
            onMove: { button, dx, dy in
                let current = offsets[button] ?? .zero
                client.moveKey(button,
                               x: Int((current.x + dx) * 1000),
                               y: Int((current.y + dy) * 1000),
                               for: suiteId)
                arrangement = client.keypadArrangement(suiteId)
            }
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Kéo phím tới chỗ vừa tay")
                .font(.headline)
                .foregroundStyle(Palette.text)
            Text((arrangement?.custom ?? false)
                 ? "Đã sửa — chạm Đặt lại để về như cũ"
                 : "Bàn phím đang ở vị trí mặc định")
                .font(.footnote)
                .foregroundStyle(Palette.textDim)

            HStack {
                Text("Cỡ phím")
                    .font(.subheadline)
                    .foregroundStyle(Palette.textDim)
                Spacer()
                Text("\(Int(scale))%")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Palette.text)
            }
            Slider(value: $scale, in: 60...160, step: 5) { editing in
                if !editing {
                    client.setKeyScale(Int(scale), for: suiteId)
                    arrangement = client.keypadArrangement(suiteId)
                }
            }

            Button("Đặt lại") {
                client.resetKeypad(suiteId)
                arrangement = client.keypadArrangement(suiteId)
                scale = Double(arrangement?.scale ?? 100)
            }
            .font(.subheadline)
            .foregroundStyle(Palette.accent)

            // Bộ bàn phím: tay người chơi không đổi từ game này sang game
            // khác, nên sắp một lần rồi dùng lại là đúng cái người ta muốn.
            Text("BỘ BÀN PHÍM")
                .font(.caption2)
                .foregroundStyle(Palette.textDim)
                .padding(.top, 10)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(layouts?.layouts ?? []) { item in
                        Button(item.name) {
                            client.applyKeypadLayout(item.id, for: suiteId)
                            reload()
                        }
                        .font(.footnote.weight(item.id == (layouts?.current ?? "")
                                               ? .semibold : .regular))
                        .tint(item.id == (layouts?.current ?? "") ? Palette.accent
                                                                  : Palette.textDim)
                    }
                    Button("+ Lưu") { naming = true }
                        .font(.footnote)
                        .tint(Palette.accent)
                }
            }
            if naming {
                HStack {
                    TextField("Tên bộ: Tay tôi, Cầm dọc…", text: $layoutName)
                        .textFieldStyle(.roundedBorder)
                    Button("Lưu") {
                        if !layoutName.trimmingCharacters(in: .whitespaces).isEmpty {
                            client.saveKeypadLayout(layoutName, for: suiteId)
                            layoutName = ""
                            naming = false
                            reload()
                        }
                    }
                    .font(.footnote)
                    .tint(Palette.accent)
                    Button("Thôi") { naming = false }
                        .font(.footnote)
                        .tint(Palette.textDim)
                }
            }

            Spacer(minLength: 0)

            // The real keypad, at the real size, with the real arrangement:
            // arranging keys on a picture of a keypad would be arranging them
            // somewhere other than where they are used.
            GeometryReader { geometry in
                let key = KeyMetrics.upright(placement)
                Keypad(
                    onPress: { _ in },
                    onRelease: { _ in },
                    plan: client.keypadPlan(suiteId, width: Int(geometry.size.width),
                                            height: Int(geometry.size.height),
                                            key: Int(key), landscape: false, left: true),
                    key: key,
                    shape: settings?.keyShape ?? 0,
                    placement: placement
                )
            }
            .frame(maxWidth: .infinity)
            .frame(height: KeyMetrics.upright * 5)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Palette.background)
        .navigationTitle("Sắp xếp bàn phím")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { reload() }
    }

    private func reload() {
        arrangement = client.keypadArrangement(suiteId)
        scale = Double(arrangement?.scale ?? 100)
        layouts = client.keypadLayouts(suiteId)
    }
}
