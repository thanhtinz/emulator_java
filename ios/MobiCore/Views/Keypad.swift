import SwiftUI

/// Bàn phím ảo của điện thoại.
///
/// The directional pad sits on the right, where the thumb of the hand not
/// holding the phone naturally rests.
///
/// Buttons report press and release separately: a J2ME game reads held keys
/// through `GameCanvas.getKeyStates`, and a press-only button reads as stuck.
struct Keypad: View {

    let onPress: (String) -> Void
    let onRelease: (String) -> Void
    /// Labels the running screen has mapped to the two softkeys, if any.
    var leftSoftKey: String?
    var rightSoftKey: String?

    var body: some View {
        VStack(spacing: 9) {
            // Directly under the screen, so they line up with the labels the
            // system draws along its bottom edge, as they do on a handset.
            HStack(spacing: 10) {
                SoftKey(label: leftSoftKey, button: "softLeft",
                        onPress: onPress, onRelease: onRelease)
                SoftKey(label: rightSoftKey, button: "softRight",
                        onPress: onPress, onRelease: onRelease)
            }
            HStack(spacing: 6) {
                PhoneKey(label: "Gọi", button: "send", tint: Palette.good,
                         onPress: onPress, onRelease: onRelease)
                PhoneKey(label: "Xóa", button: "clear", tint: Palette.text,
                         onPress: onPress, onRelease: onRelease)
                PhoneKey(label: "Kết thúc", button: "end", tint: Palette.bad,
                         onPress: onPress, onRelease: onRelease)
            }
            HStack(alignment: .center) {
                numericPad
                Spacer(minLength: 12)
                directionalPad
            }
        }
    }

    /// The 3x4 grid, laid out the way a handset does.
    private var numericPad: some View {
        VStack(spacing: 7) {
            ForEach(Self.rows, id: \.first!.button) { row in
                HStack(spacing: 7) {
                    ForEach(row, id: \.button) { key in
                        NumberKey(key: key, onPress: onPress, onRelease: onRelease)
                    }
                }
            }
        }
    }

    /// The directional cluster: eight ways, with fire in the middle.
    ///
    /// The corners are not keys of their own. MIDP has no diagonal key code
    /// and no handset had a diagonal key — a corner of the pad was two
    /// directions held at once, which is what these send.
    private var directionalPad: some View {
        VStack(spacing: 5) {
            HStack(spacing: 5) {
                ArrowKey(symbol: "arrow.up.left", button: "upLeft", label: "Lên trái",
                         corner: true, onPress: onPress, onRelease: onRelease)
                ArrowKey(symbol: "chevron.up", button: "up", label: "Lên",
                         onPress: onPress, onRelease: onRelease)
                ArrowKey(symbol: "arrow.up.right", button: "upRight", label: "Lên phải",
                         corner: true, onPress: onPress, onRelease: onRelease)
            }
            HStack(spacing: 5) {
                ArrowKey(symbol: "chevron.left", button: "left", label: "Trái",
                         onPress: onPress, onRelease: onRelease)
                FireKey(onPress: onPress, onRelease: onRelease)
                ArrowKey(symbol: "chevron.right", button: "right", label: "Phải",
                         onPress: onPress, onRelease: onRelease)
            }
            HStack(spacing: 5) {
                ArrowKey(symbol: "arrow.down.left", button: "downLeft", label: "Xuống trái",
                         corner: true, onPress: onPress, onRelease: onRelease)
                ArrowKey(symbol: "chevron.down", button: "down", label: "Xuống",
                         onPress: onPress, onRelease: onRelease)
                ArrowKey(symbol: "arrow.down.right", button: "downRight", label: "Xuống phải",
                         corner: true, onPress: onPress, onRelease: onRelease)
            }
        }
    }

    struct Key {
        let label: String
        let button: String
    }

    /// Digits only: the letters under them were for multi-tap, and this phone
    /// has a keyboard that comes up by itself when a game asks for text.
    private static let rows: [[Key]] = [
        [Key(label: "1", button: "num1"),
         Key(label: "2", button: "num2"),
         Key(label: "3", button: "num3")],
        [Key(label: "4", button: "num4"),
         Key(label: "5", button: "num5"),
         Key(label: "6", button: "num6")],
        [Key(label: "7", button: "num7"),
         Key(label: "8", button: "num8"),
         Key(label: "9", button: "num9")],
        [Key(label: "*", button: "star"),
         Key(label: "0", button: "num0"),
         Key(label: "#", button: "hash")],
    ]
}

private struct NumberKey: View {
    let key: Keypad.Key
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    var body: some View {
        Text(key.label)
            .font(.title3)
            .foregroundStyle(Palette.text)
        .frame(width: 62, height: 46)
        .background(held ? Palette.accentDim : Palette.surfaceAlt,
                    in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(held ? Palette.accent : Palette.border, lineWidth: 1)
        )
        .gesture(holdGesture(button: key.button, held: $held, onPress: onPress, onRelease: onRelease))
    }
}

/// A softkey. Blank until the running screen registers a Command, and then
/// showing that command's label — the only way a player can reach a MIDlet's
/// own menu.
///
/// The two keys share a width and centre their labels, so neither reads as the
/// more important one; which side a key is on already says which command it
/// runs, because the label bar the system draws inside the screen sits directly
/// above it.
private struct SoftKey: View {
    let label: String?
    let button: String
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    private var bound: Bool { !(label ?? "").isEmpty }

    var body: some View {
        Text(bound ? label! : "—")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(bound ? Palette.text : Palette.textDim)
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.horizontal, 14)
            .frame(height: 44)
            .background(held ? Palette.accentDim : (bound ? Palette.surfaceAlt : Palette.background),
                        in: RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(held ? Palette.accent : Palette.border, lineWidth: 1)
            )
            .gesture(holdGesture(button: button, held: $held, onPress: onPress, onRelease: onRelease))
    }
}

/// The call, clear and end trio every J2ME handset carried.
private struct PhoneKey: View {
    let label: String
    let button: String
    let tint: Color
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    var body: some View {
        Text(label)
            .font(.footnote)
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity)
            .frame(height: 38)
            .background(held ? Palette.accentDim : Palette.surfaceAlt,
                        in: RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(held ? Palette.accent : Palette.border, lineWidth: 1)
            )
            .gesture(holdGesture(button: button, held: $held, onPress: onPress, onRelease: onRelease))
    }
}

private struct ArrowKey: View {
    let symbol: String
    let button: String
    let label: String
    /// Corners are drawn quieter: there when a game needs them, not
    /// competing for the thumb.
    var corner = false
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    var body: some View {
        Image(systemName: symbol)
            .font(corner ? .body.weight(.semibold) : .title2.weight(.semibold))
            .foregroundStyle(Palette.accent)
            .frame(width: 68, height: 56)
            .background(Palette.accentDim.opacity(held ? 0.6 : 1),
                        in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.accent, lineWidth: 1))
            .accessibilityLabel(label)
            .gesture(holdGesture(button: button, held: $held, onPress: onPress, onRelease: onRelease))
    }
}

private struct FireKey: View {
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    var body: some View {
        Text("OK")
            .font(.headline)
            .foregroundStyle(Palette.accent)
            .frame(width: 68, height: 56)
            .background(Palette.accentDim.opacity(held ? 0.6 : 1),
                        in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.accent, lineWidth: 1))
            .gesture(holdGesture(button: "fire", held: $held, onPress: onPress, onRelease: onRelease))
    }
}

/// A drag gesture with zero minimum distance is the only reliable way to get
/// separate press and release callbacks out of SwiftUI.
private func holdGesture(
    button: String,
    held: Binding<Bool>,
    onPress: @escaping (String) -> Void,
    onRelease: @escaping (String) -> Void
) -> some Gesture {
    DragGesture(minimumDistance: 0)
        .onChanged { _ in
            if !held.wrappedValue {
                held.wrappedValue = true
                onPress(button)
            }
        }
        .onEnded { _ in
            held.wrappedValue = false
            onRelease(button)
        }
}
