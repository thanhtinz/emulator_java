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

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 8) {
                SoftKey(label: "Phím mềm 1", button: "softLeft", onPress: onPress, onRelease: onRelease)
                SoftKey(label: "Xóa", button: "clear", onPress: onPress, onRelease: onRelease)
                SoftKey(label: "Phím mềm 2", button: "softRight", onPress: onPress, onRelease: onRelease)
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

    /// The directional cluster, with fire in the middle.
    private var directionalPad: some View {
        VStack(spacing: 5) {
            ArrowKey(symbol: "chevron.up", button: "up", label: "Lên",
                     onPress: onPress, onRelease: onRelease)
            HStack(spacing: 5) {
                ArrowKey(symbol: "chevron.left", button: "left", label: "Trái",
                         onPress: onPress, onRelease: onRelease)
                FireKey(onPress: onPress, onRelease: onRelease)
                ArrowKey(symbol: "chevron.right", button: "right", label: "Phải",
                         onPress: onPress, onRelease: onRelease)
            }
            ArrowKey(symbol: "chevron.down", button: "down", label: "Xuống",
                     onPress: onPress, onRelease: onRelease)
        }
    }

    struct Key {
        let label: String
        let button: String
        let hint: String
    }

    private static let rows: [[Key]] = [
        [Key(label: "1", button: "num1", hint: ""),
         Key(label: "2", button: "num2", hint: "abc"),
         Key(label: "3", button: "num3", hint: "def")],
        [Key(label: "4", button: "num4", hint: "ghi"),
         Key(label: "5", button: "num5", hint: "jkl"),
         Key(label: "6", button: "num6", hint: "mno")],
        [Key(label: "7", button: "num7", hint: "pqrs"),
         Key(label: "8", button: "num8", hint: "tuv"),
         Key(label: "9", button: "num9", hint: "wxyz")],
        [Key(label: "*", button: "star", hint: ""),
         Key(label: "0", button: "num0", hint: "+"),
         Key(label: "#", button: "hash", hint: "")],
    ]
}

private struct NumberKey: View {
    let key: Keypad.Key
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    var body: some View {
        VStack(spacing: 0) {
            Text(key.label).font(.title3).foregroundStyle(Palette.text)
            if !key.hint.isEmpty {
                Text(key.hint).font(.system(size: 10)).foregroundStyle(Palette.textDim)
            }
        }
        .frame(width: 64, height: 50)
        .background(held ? Palette.accentDim : Palette.surfaceAlt,
                    in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(held ? Palette.accent : Palette.border, lineWidth: 1)
        )
        .gesture(holdGesture(button: key.button, held: $held, onPress: onPress, onRelease: onRelease))
    }
}

private struct SoftKey: View {
    let label: String
    let button: String
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    var body: some View {
        Text(label)
            .font(.subheadline)
            .foregroundStyle(Palette.text)
            .frame(maxWidth: .infinity)
            .frame(height: 46)
            .background(held ? Palette.accentDim : Palette.surfaceAlt,
                        in: RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(held ? Palette.accent : Palette.border, lineWidth: 1)
            )
            .gesture(holdGesture(button: button, held: $held, onPress: onPress, onRelease: onRelease))
    }
}

private struct ArrowKey: View {
    let symbol: String
    let button: String
    let label: String
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    var body: some View {
        Image(systemName: symbol)
            .font(.title2.weight(.semibold))
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
