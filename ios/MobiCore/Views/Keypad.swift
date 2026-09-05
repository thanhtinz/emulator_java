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

    /// The standard key size, before the player's own size is applied.
    static var upright: CGFloat {
        let bounds = UIScreen.main.bounds.size
        return min(bounds.width, bounds.height) / 6.5
    }

    /// How big one key is drawn once the player's own size is applied.
    ///
    /// The screen has the last word. A key size that would push the two pads
    /// off the sides is not honoured as asked — the keypad would be unusable
    /// and nothing would show that it was the size setting that did it — so
    /// it is held to what fits.
    static func upright(_ placement: KeyPlacement) -> CGFloat {
        let fits = (UIScreen.main.bounds.size.width - 36) / 6
        return min(placement.sized(upright), fits)
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
/// Where the keys are, and — while arranging — how to move them.
///
/// `offsets` places each key relative to where the standard layout puts it, in
/// units of one key, so the same arrangement holds upright, sideways and on
/// any size of screen. `onMove` is set only on the arranging screen: while it
/// is set the keys are dragged rather than pressed, because a key cannot be
/// both the thing being moved and the thing being played with.
struct KeyPlacement {
    var offsets: [String: CGPoint] = [:]
    var scale: Int = 100
    var onMove: ((String, CGFloat, CGFloat) -> Void)?

    func offset(_ button: String) -> CGPoint {
        offsets[button] ?? .zero
    }

    /// A key's size, given what the standard layout would have made it.
    func sized(_ standard: CGFloat) -> CGFloat {
        max(8, standard * CGFloat(scale) / 100)
    }
}

private struct KeyPlacementKey: EnvironmentKey {
    static let defaultValue = KeyPlacement()
}

extension EnvironmentValues {
    var keyPlacement: KeyPlacement {
        get { self[KeyPlacementKey.self] }
        set { self[KeyPlacementKey.self] = newValue }
    }
}

extension View {
    /// Says what a touch on one key does.
    ///
    /// Where the key sits is the plan's business — it has already added the
    /// player's drag. Dragging is measured in keys rather than points: a key
    /// is a different number of points upright, sideways and on every
    /// different phone, and one arrangement has to hold for all of them.
    func placed(_ button: String, size: CGFloat, placement: KeyPlacement,
                hold: some Gesture) -> some View {
        gesture(placement.onMove == nil
                     ? AnyGesture(hold.map { _ in () })
                     : AnyGesture(DragGesture()
                        .onChanged { value in
                            placement.onMove?(button,
                                              value.translation.width / size,
                                              value.translation.height / size)
                        }
                        .map { _ in () }))
    }
}

/// The shape every key on a keypad is cut to, carried down the view tree.
///
/// Eleven views would otherwise each grow a parameter none of them decides
/// anything with, and one of them would eventually be missed.
private struct KeyShapeKey: EnvironmentKey {
    static let defaultValue = 0
}

extension EnvironmentValues {
    var keyShape: Int {
        get { self[KeyShapeKey.self] }
        set { self[KeyShapeKey.self] = newValue }
    }
}

/// The corner radius a key is cut with, for the chosen shape.
///
/// A square key and a round one are the same rectangle at two radii — none
/// and half the key — so no key needs a shape type of its own.
func keyRadius(_ shape: Int, size: CGFloat, rounded: CGFloat) -> CGFloat {
    switch shape {
    case 1: return 0
    case 2: return size / 2
    default: return rounded
    }
}

struct Keypad: View {

    let onPress: (String) -> Void
    let onRelease: (String) -> Void
    /// Labels the running screen has mapped to the two softkeys, if any.
    var leftSoftKey: String?
    var rightSoftKey: String?

    /// Where every key goes, measured by the core so that this keypad and the
    /// one Android draws and the one in the screenshots are one keypad.
    var plan: KeypadPlanData?
    /// One key's size, which is what a drag and a corner radius are measured in.
    var key: CGFloat = 0
    /// Which directions a lean on the stick holds; see `PlacedKey.steer`.
    var steer: ((CGFloat, CGFloat, CGFloat) -> [String])?
    /// The key shape; see `GameProfile.KEY_SHAPE_*` in the core.
    var shape: Int = 0
    /// How solid to draw the keypad, in percent. Applied to the keypad as a
    /// whole rather than colour by colour, so keys, outlines and lettering
    /// all step back together.
    var opacity: Int = 100
    /// Where the keys have been dragged to, and how big they are drawn.
    var placement = KeyPlacement()

    private var keySize: CGFloat { key > 0 ? key : KeyMetrics.upright(placement) }

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(plan?.keys ?? [], id: \.button) { placed in
                PlacedKey(placed: placed, key: keySize,
                          leftSoftKey: leftSoftKey, rightSoftKey: rightSoftKey,
                          steer: steer, onPress: onPress, onRelease: onRelease)
                    .offset(x: CGFloat(placed.x), y: CGFloat(placed.y))
            }
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .frame(height: CGFloat(plan?.height ?? 0))
        .environment(\.keyShape, shape)
        .environment(\.keyPlacement, placement)
        .opacity(Double(opacity) / 100.0)
    }
}

/// One key of a plan, in whatever it turned out to be.
private struct PlacedKey: View {
    let placed: PlacedKeyData
    let key: CGFloat
    let leftSoftKey: String?
    let rightSoftKey: String?
    /// Which directions a lean this far off the stick's middle holds, asked
    /// of the core so the phone and the preview steer alike.
    var steer: ((CGFloat, CGFloat, CGFloat) -> [String])?
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    var body: some View {
        let width = CGFloat(placed.w)
        let height = CGFloat(placed.h)
        switch placed.kind {
        case KeypadPlanData.kindSoft:
            SoftKey(label: placed.button == "softLeft" ? leftSoftKey : rightSoftKey,
                    button: placed.button, key: key, width: width, height: height,
                    onPress: onPress, onRelease: onRelease)
        case KeypadPlanData.kindStick:
            StickKey(button: placed.button, size: min(width, height), key: key,
                     steer: steer, onPress: onPress, onRelease: onRelease)
        case KeypadPlanData.kindFire:
            FireKey(size: min(width, height), width: width, height: height,
                    round: placed.round, onPress: onPress, onRelease: onRelease)
        case KeypadPlanData.kindArrow:
            ArrowKey(symbol: PlacedKey.symbol(placed.arrow), button: placed.button,
                     label: PlacedKey.name(placed.arrow), size: min(width, height),
                     width: width, height: height,
                     corner: placed.arrow >= 4, round: placed.round,
                     onPress: onPress, onRelease: onRelease)
        default:
            NumberKey(label: placed.label, button: placed.button,
                      size: min(width, height), width: width, height: height,
                      round: placed.round, onPress: onPress, onRelease: onRelease)
        }
    }

    static func symbol(_ direction: Int) -> String {
        switch direction {
        case 0: return "chevron.up"
        case 1: return "chevron.down"
        case 2: return "chevron.left"
        case 3: return "chevron.right"
        case 4: return "arrow.up.left"
        case 5: return "arrow.up.right"
        case 6: return "arrow.down.left"
        default: return "arrow.down.right"
        }
    }

    static func name(_ direction: Int) -> String {
        switch direction {
        case 0: return "Lên"
        case 1: return "Xuống"
        case 2: return "Trái"
        case 3: return "Phải"
        case 4: return "Lên trái"
        case 5: return "Lên phải"
        case 6: return "Xuống trái"
        default: return "Xuống phải"
        }
    }
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
    /// Asks the core for this column's half of the keypad, once the column
    /// knows how much room it has. Called with width, height and key size.
    var planFor: ((Int, Int, Int) -> KeypadPlanData?)?
    /// Which directions a lean on the stick holds; see `PlacedKey.steer`.
    var steer: ((CGFloat, CGFloat, CGFloat) -> [String])?
    /// The key shape; see `GameProfile.KEY_SHAPE_*` in the core.
    var shape: Int = 0
    /// How solid to draw the column, in percent.
    var opacity: Int = 100
    /// Where the keys have been dragged to, and how big they are drawn.
    var placement = KeyPlacement()

    var body: some View {
        // Turned, J2ME Loader sizes its keys off the long edge. Its keypad
        // floats over the game, though, and this one has a column to itself,
        // so the size is also held to what the column can hold.
        GeometryReader { geometry in
            let room = (geometry.size.height - KeyMetrics.gap * 4)
                / (4 + KeyMetrics.softScaleY)
            let key = min(placement.sized(KeyMetrics.turned), room)
            let plan = planFor?(Int(geometry.size.width), Int(geometry.size.height), Int(key))
            ZStack(alignment: .topLeading) {
                ForEach(plan?.keys ?? [], id: \.button) { placed in
                    PlacedKey(placed: placed, key: key,
                              leftSoftKey: softKeyLabel, rightSoftKey: softKeyLabel,
                              steer: steer, onPress: onPress, onRelease: onRelease)
                        .offset(x: CGFloat(placed.x), y: CGFloat(placed.y))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .frame(width: KeyMetrics.turned * 3 + KeyMetrics.gap * 2 + 24)
        .environment(\.keyShape, shape)
        .environment(\.keyPlacement, placement)
        .opacity(Double(opacity) / 100.0)
    }
}

private struct NumberKey: View {
    let label: String
    let button: String
    let size: CGFloat
    let width: CGFloat
    let height: CGFloat
    var round = false
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false
    @Environment(\.keyShape) private var shape
    @Environment(\.keyPlacement) private var placement

    var body: some View {
        let radius = keyRadius(round ? 2 : shape, size: size, rounded: 12)
        Text(label)
            .font(.title3)
            .foregroundStyle(Palette.text)
        .frame(width: width, height: height)
        .background(held ? Palette.accentDim : Palette.surfaceAlt,
                    in: RoundedRectangle(cornerRadius: radius))
        .overlay(
            RoundedRectangle(cornerRadius: radius)
                .stroke(held ? Palette.accent : Palette.border, lineWidth: 1)
        )
        .placed(button, size: size, placement: placement,
                hold: holdGesture(button: button, held: $held,
                                  onPress: onPress, onRelease: onRelease))
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
    let width: CGFloat
    let height: CGFloat
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false
    @Environment(\.keyShape) private var shape
    @Environment(\.keyPlacement) private var placement

    private var bound: Bool { !(label ?? "").isEmpty }

    /// L and R name the key itself, the way every J2ME emulator labels these
    /// two; the text in the middle is the game's command and changes with the
    /// screen, so both are needed.
    private var mark: String { button == "softLeft" ? "L" : "R" }

    var body: some View {
        // A wide key, so the round shape gives a pill rather than a circle.
        let radius = keyRadius(shape, size: height, rounded: 12)
        // With no command on it, the key is simply called what it is: an "L"
        // in the middle rather than an "L" in the corner beside a dash
        // standing in for something that is not there.
        Text(bound ? label! : mark)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(bound ? Palette.text : Palette.accent)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .frame(width: width - 24, alignment: .center)
            .overlay(alignment: .leading) {
                if bound {
                    Text(mark)
                        .font(.caption2)
                        .foregroundStyle(Palette.accent)
                }
            }
            .padding(.horizontal, 12)
            .frame(height: height)
            .background(held ? Palette.accentDim : (bound ? Palette.surfaceAlt : Palette.background),
                        in: RoundedRectangle(cornerRadius: radius))
            .overlay(
                RoundedRectangle(cornerRadius: radius)
                    .stroke(held ? Palette.accent : Palette.border, lineWidth: 1)
            )
            .placed(button, size: key, placement: placement,
                    hold: holdGesture(button: button, held: $held,
                                      onPress: onPress, onRelease: onRelease))
    }
}

private struct ArrowKey: View {
    let symbol: String
    let button: String
    let label: String
    let size: CGFloat
    let width: CGFloat
    let height: CGFloat
    /// Corners are drawn quieter: there when a game needs them, not
    /// competing for the thumb.
    var corner = false
    var round = false
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false
    @Environment(\.keyShape) private var shape
    @Environment(\.keyPlacement) private var placement

    var body: some View {
        let radius = keyRadius(round ? 2 : shape, size: size, rounded: 14)
        Image(systemName: symbol)
            .font(.system(size: corner ? size * 0.34 : size * 0.5, weight: .semibold))
            .foregroundStyle(Palette.accent)
            .frame(width: width, height: height)
            .background(Palette.accentDim.opacity(held ? 0.6 : 1),
                        in: RoundedRectangle(cornerRadius: radius))
            .overlay(RoundedRectangle(cornerRadius: radius).stroke(Palette.accent, lineWidth: 1))
            .accessibilityLabel(label)
            .placed(button, size: size, placement: placement,
                    hold: holdGesture(button: button, held: $held,
                                      onPress: onPress, onRelease: onRelease))
    }
}

/// Cần điều khiển: một phím duy nhất, và hướng là chỗ ngón cái tì vào.
///
/// Not four keys drawn as a circle. A thumb rests on it and leans, and the
/// lean decides which directions are held — so a corner is reached by leaning
/// into it rather than by finding an edge the thumb cannot feel.
private struct StickKey: View {
    let button: String
    let size: CGFloat
    let key: CGFloat
    let steer: ((CGFloat, CGFloat, CGFloat) -> [String])?
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held: [String] = []
    @State private var knob: CGSize = .zero
    @Environment(\.keyPlacement) private var placement

    var body: some View {
        Circle()
            .fill(held.isEmpty ? Palette.accentDim : Palette.accent.opacity(0.35))
            .overlay(Circle().stroke(Palette.accent, lineWidth: 1))
            .overlay(
                // The knob follows the thumb, so the stick shows how far it
                // is being pushed — which separate keys never could.
                Circle()
                    .fill(Palette.surfaceAlt)
                    .overlay(Circle().stroke(Palette.accent, lineWidth: 1))
                    .frame(width: size * 0.4, height: size * 0.4)
                    .offset(x: knob.width / 2, y: knob.height / 2)
            )
            .frame(width: size, height: size)
            .gesture(placement.onMove == nil ? AnyGesture(leaning.map { _ in () })
                                             : AnyGesture(dragging.map { _ in () }))
    }

    /// Playing: the lean steers.
    private var leaning: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let radius = size / 2
                let dx = value.location.x - radius
                let dy = value.location.y - radius
                let want = steer?(dx, dy, radius) ?? []
                // Released before pressed, so a turn from left to right is
                // never briefly both at once.
                for direction in held where !want.contains(direction) {
                    onRelease(direction)
                }
                for direction in want where !held.contains(direction) {
                    onPress(direction)
                }
                held = want
                knob = want.isEmpty ? .zero : CGSize(width: dx, height: dy)
            }
            .onEnded { _ in
                held.forEach(onRelease)
                held = []
                knob = .zero
            }
    }

    /// Arranging: the stick is the thing being moved, not played with.
    private var dragging: some Gesture {
        DragGesture()
            .onChanged { value in
                placement.onMove?(button,
                                  value.translation.width / key,
                                  value.translation.height / key)
            }
    }
}

private struct FireKey: View {
    let size: CGFloat
    let width: CGFloat
    let height: CGFloat
    var round = false
    let onPress: (String) -> Void
    let onRelease: (String) -> Void

    @State private var held = false
    @Environment(\.keyShape) private var shape
    @Environment(\.keyPlacement) private var placement

    var body: some View {
        let radius = keyRadius(round ? 2 : shape, size: size, rounded: 14)
        // "F" is what J2ME Loader writes here, and fire is what MIDP calls
        // it; this key has never been an "OK" button.
        Text("F")
            .font(.system(size: size * 0.4, weight: .semibold))
            .foregroundStyle(Palette.accent)
            .frame(width: width, height: height)
            .background(Palette.accentDim.opacity(held ? 0.6 : 1),
                        in: RoundedRectangle(cornerRadius: radius))
            .overlay(RoundedRectangle(cornerRadius: radius).stroke(Palette.accent, lineWidth: 1))
            .placed("fire", size: size, placement: placement,
                    hold: holdGesture(button: "fire", held: $held,
                                      onPress: onPress, onRelease: onRelease))
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
