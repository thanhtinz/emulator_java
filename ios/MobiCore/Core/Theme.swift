import SwiftUI

/// MobiCore's palette, matching the Android shell so both platforms look like
/// one product.
enum Palette {
    static let background = Color(red: 0.055, green: 0.067, blue: 0.086)
    static let surface = Color(red: 0.090, green: 0.110, blue: 0.141)
    static let surfaceAlt = Color(red: 0.122, green: 0.149, blue: 0.188)
    static let border = Color(red: 0.173, green: 0.208, blue: 0.263)
    static let text = Color(red: 0.902, green: 0.929, blue: 0.953)
    static let textDim = Color(red: 0.545, green: 0.596, blue: 0.659)
    static let accent = Color(red: 0.298, green: 0.761, blue: 1.0)
    static let accentDim = Color(red: 0.106, green: 0.306, blue: 0.408)
    static let good = Color(red: 0.337, green: 0.827, blue: 0.392)
    static let warn = Color(red: 0.890, green: 0.702, blue: 0.255)
    static let bad = Color(red: 0.973, green: 0.318, blue: 0.286)
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
