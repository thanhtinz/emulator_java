import SwiftUI

/// The virtual phone keypad.
///
/// Buttons report press and release separately: a J2ME game reads held keys
/// through `GameCanvas.getKeyStates`, and a press-only button reads as stuck.
struct Keypad: View {

    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    var body: some View {
        VStack(spacing: 10) {
            HStack(alignment: .center, spacing: 16) {
                directionalPad
                Spacer(minLength: 0)
                numericPad
            }
            HStack(spacing: 10) {
                KeyButton(label: "Soft 1", button: "softLeft", width: 92,
                          onPress: onPress, onRelease: onRelease)
                KeyButton(label: "Clear", button: "clear", width: 92,
                          onPress: onPress, onRelease: onRelease)
                KeyButton(label: "Soft 2", button: "softRight", width: 92,
                          onPress: onPress, onRelease: onRelease)
            }
        }
    }

    private var directionalPad: some View {
        VStack(spacing: 4) {
            ArrowKey(symbol: "chevron.up", button: "up", onPress: onPress, onRelease: onRelease)
            HStack(spacing: 4) {
                ArrowKey(symbol: "chevron.left", button: "left", onPress: onPress, onRelease: onRelease)
                KeyButton(label: "OK", button: "fire", width: 54, accent: true,
                          onPress: onPress, onRelease: onRelease)
                ArrowKey(symbol: "chevron.right", button: "right", onPress: onPress, onRelease: onRelease)
            }
            ArrowKey(symbol: "chevron.down", button: "down", onPress: onPress, onRelease: onRelease)
        }
    }

    private var numericPad: some View {
        VStack(spacing: 6) {
            ForEach(Self.rows, id: \.first!.1) { row in
                HStack(spacing: 6) {
                    ForEach(row, id: \.1) { label, button in
                        KeyButton(label: label, button: button, width: 46,
                                  onPress: onPress, onRelease: onRelease)
                    }
                }
            }
        }
    }

    private static let rows: [[(String, String)]] = [
        [("1", "num1"), ("2", "num2"), ("3", "num3")],
        [("4", "num4"), ("5", "num5"), ("6", "num6")],
        [("7", "num7"), ("8", "num8"), ("9", "num9")],
        [("*", "star"), ("0", "num0"), ("#", "hash")],
    ]
}

private struct KeyButton: View {
    let label: String
    let button: String
    var width: CGFloat = 46
    var accent: Bool = false
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    var body: some View {
        Text(label)
            .font(.subheadline)
            .foregroundStyle(accent ? Palette.accent : Palette.text)
            .frame(width: width, height: 40)
            .background(accent ? Palette.accentDim : Palette.surfaceAlt,
                        in: RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(accent ? Palette.accent : Palette.border, lineWidth: 1)
            )
            .opacity(held ? 0.6 : 1)
            .gesture(holdGesture(button: button, held: $held, onPress: onPress, onRelease: onRelease))
    }
}

private struct ArrowKey: View {
    let symbol: String
    let button: String
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    var body: some View {
        Image(systemName: symbol)
            .foregroundStyle(Palette.accent)
            .frame(width: 54, height: 38)
            .background(Palette.accentDim, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.accent, lineWidth: 1))
            .opacity(held ? 0.6 : 1)
            .gesture(holdGesture(button: button, held: $held, onPress: onPress, onRelease: onRelease))
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
