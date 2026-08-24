import SwiftUI

/// MobiCore's palette, in light and dark, matching the Android shell so both
/// platforms look like one product.
///
/// Light is the default. A dark chrome looks handsome in a screenshot and is
/// tiring to read in daylight, which is where a phone mostly gets used.
///
/// The colours stay static so every view can keep naming them by role.
/// `dark` is set from the client's stored setting, and the views that show
/// them observe the client, so a change repaints the app.
enum Palette {

    static var dark = false

    static var background: Color { dark ? Color(hex: 0x0E1116) : Color(hex: 0xF2F4F7) }
    static var surface: Color { dark ? Color(hex: 0x171C24) : Color(hex: 0xFFFFFF) }
    static var surfaceAlt: Color { dark ? Color(hex: 0x1F2630) : Color(hex: 0xE9EDF2) }
    static var border: Color { dark ? Color(hex: 0x2C3543) : Color(hex: 0xD3DAE3) }
    static var text: Color { dark ? Color(hex: 0xE6EDF3) : Color(hex: 0x16202B) }
    static var textDim: Color { dark ? Color(hex: 0x8B98A8) : Color(hex: 0x5C6B7A) }

    /// Darker on light: the same blue on white is too pale to read.
    static var accent: Color { dark ? Color(hex: 0x4CC2FF) : Color(hex: 0x0A6FA8) }
    static var accentDim: Color { dark ? Color(hex: 0x1B4E68) : Color(hex: 0xD7EBF7) }
    static var good: Color { dark ? Color(hex: 0x56D364) : Color(hex: 0x1A7F37) }
    static var warn: Color { dark ? Color(hex: 0xE3B341) : Color(hex: 0x9A6700) }
    static var bad: Color { dark ? Color(hex: 0xF85149) : Color(hex: 0xC0342B) }
}

private extension Color {
    /// 0xRRGGBB, which is how the palette is written on every platform here.
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

/// Theme choices, matching `AppSettings` in the core.
enum ThemeChoice {
    static let light = 0
    static let dark = 1
    static let system = 2
}

/// Rounded panel used for every grouped block.
struct SectionCard<Content: View>: View {
    var title: String?
    var trailing: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let title {
                HStack {
                    Text(title)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(Palette.textDim)
                    Spacer()
                    if let trailing {
                        Text(trailing).font(.caption2).foregroundStyle(Palette.accent)
                    }
                }
            }
            content
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.border, lineWidth: 1))
    }
}

/// Label on the left, value on the right.
struct FieldRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label).font(.footnote).foregroundStyle(Palette.textDim)
            Spacer()
            Text(value)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Palette.text)
                .lineLimit(1)
                .truncationMode(.middle)
        }
    }
}

struct Chip: View {
    let text: String
    var color: Color = Palette.accent
    var background: Color = Palette.accentDim

    var body: some View {
        Text(text)
            .font(.caption2)
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(background, in: Capsule())
    }
}

/// Placeholder shown when a screen has nothing to display.
struct EmptyState: View {
    let icon: String
    let title: String
    let body_: String

    init(icon: String, title: String, body: String) {
        self.icon = icon
        self.title = title
        self.body_ = body
    }

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 36))
                .foregroundStyle(Palette.textDim)
            Text(title).font(.headline).foregroundStyle(Palette.text)
            Text(body_)
                .font(.footnote)
                .foregroundStyle(Palette.textDim)
                .multilineTextAlignment(.center)
        }
        .padding(32)
    }
}

/// Cover art with an initial as the fallback, because many suites ship no icon.
struct GameArtwork: View {
    let title: String
    let image: Image?
    var size: CGFloat = 48

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size / 4).fill(Palette.surfaceAlt)
            if let image {
                image
                    .interpolation(.none)
                    .resizable()
                    .scaledToFill()
                    .clipShape(RoundedRectangle(cornerRadius: size / 4))
            } else {
                Text(title.prefix(1).uppercased())
                    .font(.system(size: size / 2.4, weight: .bold))
                    .foregroundStyle(Palette.accent)
            }
        }
        .frame(width: size, height: size)
    }
}
