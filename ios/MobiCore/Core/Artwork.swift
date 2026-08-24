import UIKit

/// Turns a picture the user picked into cover art the library will accept.
///
/// The photo picker hands back whatever the library holds — usually HEIC or
/// JPEG — and the emulator decodes only PNG, because that is the one format it
/// reads on every platform it runs on, MIDP included. So the picture is
/// decoded once here and re-encoded.
///
/// It is squared off and bounded too: a cover is shown at about ninety points
/// and a modern photo is several thousand pixels across, so keeping it whole
/// would cost megabytes per game for nothing anyone can see.
enum Artwork {

    /// Longest edge kept. Twice what the largest tile shows, for sharpness.
    private static let maxEdge: CGFloat = 256

    static func png(from data: Data) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        return squared(image).pngData()
    }

    private static func squared(_ image: UIImage) -> UIImage {
        let edge = min(image.size.width, image.size.height)
        let side = min(edge, maxEdge)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format)
            .image { _ in
                // Centre crop, drawn straight into the target square: what a
                // tile shows is the middle of the picture anyway.
                let scale = side / edge
                let width = image.size.width * scale
                let height = image.size.height * scale
                image.draw(in: CGRect(
                    x: (side - width) / 2,
                    y: (side - height) / 2,
                    width: width,
                    height: height
                ))
            }
    }
}
