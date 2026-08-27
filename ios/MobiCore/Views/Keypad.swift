import SwiftUI

/// Key metrics taken from J2ME Loader's on-screen keypad, which is the one
/// every player of these games already has their thumbs trained on.
///
/// Its keys are square and sized off the screen rather than off a designer's
/// guess: `keySize = min(width, height) / 6.5` upright, `max(width, height) /
/// 12` when the phone is turned. The two softkeys are the one exception —
/// `PHONE_KEY_SCALE_X = 2.0f`, `PHONE_KEY_SCALE_Y = 0.75f` — so they read as a
/// wide, shallow bar rather than as two more keys in the grid.
enum KeyMetrics {
    static let softScaleX: CGFloat = 2.0
    static let softScaleY: CGFloat = 0.75
    /// A hair of daylight between keys; J2ME Loader snaps its keys together.
    static let gap: CGFloat = 4

    static var upright: CGFloat {
        let bounds = UIScreen.main.bounds.size
        return min(bounds.width, bounds.height) / 6.5
    }

    static var turned: CGFloat {
        let bounds = UIScreen.main.bounds.size
        return max(bounds.width, bounds.height) / 12
    }
}

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

    /// Square, and sized off the screen the way J2ME Loader does it.
    var key: CGFloat = KeyMetrics.upright
    /// Which keys to show; see `GameProfile.KEYPAD_*` in the core.
    var layout: Int = 0

    private var arrows: Bool { layout == 0 || layout == 1 }
    private var numbers: Bool { layout == 0 || layout == 2 }

    var body: some View {
        VStack(spacing: KeyMetrics.gap * 3) {
            // Directly under the screen, so they line up with the labels the
            // system draws along its bottom edge, as they do on a handset.
            HStack {
                SoftKey(label: leftSoftKey, button: "softLeft", key: key,
                        onPress: onPress, onRelease: onRelease)
                Spacer(minLength: 12)
                SoftKey(label: rightSoftKey, button: "softRight", key: key,
                        onPress: onPress, onRelease: onRelease)
            }
            // A pad left on its own takes the middle: no reason to keep a
            // hole where the other half of the keypad used to be.
            HStack(alignment: .center) {
                if numbers {
                    numericPad
                }
                if arrows && numbers {
                    Spacer(minLength: 12)
                }
                if arrows {
                    directionalPad
                }
            }
        }
    }

    /// The 3x4 grid, laid out the way a handset does.
    private var numericPad: some View {
        VStack(spacing: KeyMetrics.gap) {
            ForEach(Self.rows, id: \.first!.button) { row in
                HStack(spacing: KeyMetrics.gap) {
                    ForEach(row, id: \.button) { entry in
                        NumberKey(entry: entry, size: key,
                                  onPress: onPress, onRelease: onRelease)
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
        VStack(spacing: KeyMetrics.gap) {
            HStack(spacing: KeyMetrics.gap) {
                ArrowKey(symbol: "arrow.up.left", button: "upLeft", label: "Lên trái", size: key,
                         corner: true, onPress: onPress, onRelease: onRelease)
                ArrowKey(symbol: "chevron.up", button: "up", label: "Lên", size: key,
                         onPress: onPress, onRelease: onRelease)
                ArrowKey(symbol: "arrow.up.right", button: "upRight", label: "Lên phải", size: key,
                         corner: true, onPress: onPress, onRelease: onRelease)
            }
            HStack(spacing: KeyMetrics.gap) {
                ArrowKey(symbol: "chevron.left", button: "left", label: "Trái", size: key,
                         onPress: onPress, onRelease: onRelease)
                FireKey(size: key, onPress: onPress, onRelease: onRelease)
                ArrowKey(symbol: "chevron.right", button: "right", label: "Phải", size: key,
                         onPress: onPress, onRelease: onRelease)
            }
            HStack(spacing: KeyMetrics.gap) {
                ArrowKey(symbol: "arrow.down.left", button: "downLeft", label: "Xuống trái", size: key,
                         corner: true, onPress: onPress, onRelease: onRelease)
                ArrowKey(symbol: "chevron.down", button: "down", label: "Xuống", size: key,
                         onPress: onPress, onRelease: onRelease)
                ArrowKey(symbol: "arrow.down.right", button: "downRight", label: "Xuống phải", size: key,
                         corner: true, onPress: onPress, onRelease: onRelease)
            }
        }
    }

    /// The directional half of the pad, for a game held sideways.
    var directionalColumn: some View { directionalPad }

    /// The numeric half, likewise.
    var numericColumn: some View { numericPad }

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

/// One hand's worth of keys, for when the phone is held sideways.
///
/// The game keeps the middle of a landscape screen, because that is what the
/// player is looking at, and each hand gets a column: a shoulder key on top,
/// its pad in the middle, and the softkey the game labels at the bottom, where
/// the thumb already rests.
struct ControlColumn: View {

    /// True for the pad hand, false for the numbers.
    let directional: Bool
    let softKeyLabel: String?
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    var body: some View {
        // Turned, J2ME Loader sizes its keys off the long edge. Its keypad
        // floats over the game, though, and this one has a column to itself,
        // so the size is also held to what the column can hold.
        GeometryReader { geometry in
            let room = (geometry.size.height - KeyMetrics.gap * 4)
                / (4 + KeyMetrics.softScaleY)
            let key = min(KeyMetrics.turned, room)
            let pad = Keypad(onPress: onPress, onRelease: onRelease, key: key)
            VStack(spacing: KeyMetrics.gap * 3) {
                if directional {
                    pad.directionalColumn
                } else {
                    pad.numericColumn
                }
                Spacer(minLength: 0)
                SoftKey(label: softKeyLabel, button: directional ? "softLeft" : "softRight",
                        key: key, onPress: onPress, onRelease: onRelease)
            }
            .frame(maxWidth: .infinity)
        }
        .frame(width: KeyMetrics.turned * 3 + KeyMetrics.gap * 2 + 24)
    }
}

private struct NumberKey: View {
    let entry: Keypad.Key
    let size: CGFloat
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    var body: some View {
        Text(entry.label)
            .font(.title3)
            .foregroundStyle(Palette.text)
        .frame(width: size, height: size)
        .background(held ? Palette.accentDim : Palette.surfaceAlt,
                    in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(held ? Palette.accent : Palette.border, lineWidth: 1)
        )
        .gesture(holdGesture(button: entry.button, held: $held, onPress: onPress, onRelease: onRelease))
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
    let key: CGFloat
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    private var bound: Bool { !(label ?? "").isEmpty }

    /// L and R name the key itself, the way every J2ME emulator labels these
    /// two; the text in the middle is the game's command and changes with the
    /// screen, so both are needed.
    private var mark: String { button == "softLeft" ? "L" : "R" }

    var body: some View {
        Text(bound ? label! : "—")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(bound ? Palette.text : Palette.textDim)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .frame(width: key * KeyMetrics.softScaleX, alignment: .center)
            .overlay(alignment: .leading) {
                Text(mark)
                    .font(.caption2)
                    .foregroundStyle(Palette.accent)
            }
            .padding(.horizontal, 12)
            .frame(height: key * KeyMetrics.softScaleY)
            .background(held ? Palette.accentDim : (bound ? Palette.surfaceAlt : Palette.background),
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
    let size: CGFloat
    /// Corners are drawn quieter: there when a game needs them, not
    /// competing for the thumb.
    var corner = false
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: corner ? size * 0.34 : size * 0.5, weight: .semibold))
            .foregroundStyle(Palette.accent)
            .frame(width: size, height: size)
            .background(Palette.accentDim.opacity(held ? 0.6 : 1),
                        in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.accent, lineWidth: 1))
            .accessibilityLabel(label)
            .gesture(holdGesture(button: button, held: $held, onPress: onPress, onRelease: onRelease))
    }
}

private struct FireKey: View {
    let size: CGFloat
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false

    var body: some View {
        // "F" is what J2ME Loader writes here, and fire is what MIDP calls
        // it; this key has never been an "OK" button.
        Text("F")
            .font(.system(size: size * 0.4, weight: .semibold))
            .foregroundStyle(Palette.accent)
            .frame(width: size, height: size)
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
