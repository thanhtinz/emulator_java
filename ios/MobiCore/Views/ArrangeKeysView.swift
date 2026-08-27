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

            Spacer(minLength: 0)

            // The real keypad, at the real size, with the real arrangement:
            // arranging keys on a picture of a keypad would be arranging them
            // somewhere other than where they are used.
            Keypad(
                onPress: { _ in },
                onRelease: { _ in },
                layout: settings?.keypadLayout ?? 0,
                showSoftKeys: (settings?.keypadLayout ?? 0) != 3,
                shape: settings?.keyShape ?? 0,
                placement: placement
            )
            .frame(maxWidth: .infinity)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Palette.background)
        .navigationTitle("Sắp xếp bàn phím")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            arrangement = client.keypadArrangement(suiteId)
            scale = Double(arrangement?.scale ?? 100)
        }
    }
}
