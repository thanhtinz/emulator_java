import SwiftUI

/// Ảnh chụp màn hình của một trò chơi.
///
/// A screenshot nothing can show again is a dead end, and a J2ME game has no
/// way of showing anyone what happened in it — which is the whole reason the
/// in-game menu can take one. They live in the app's own folder, so this is
/// where they are looked at and thrown away.
struct ScreenshotsView: View {

    let suiteId: String

    @EnvironmentObject private var client: MobiCoreClient
    @State private var shots: [Screenshot] = []
    @State private var opened: String?

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        Group {
            if shots.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "camera")
                        .font(.system(size: 40))
                        .foregroundStyle(Palette.textDim)
                    Text("Chưa có ảnh nào")
                        .font(.headline)
                        .foregroundStyle(Palette.text)
                    Text("Trong lúc chơi, mở Menu rồi chọn \"Chụp màn hình\".")
                        .font(.footnote)
                        .foregroundStyle(Palette.textDim)
                        .multilineTextAlignment(.center)
                }
                .padding(32)
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(shots) { shot in
                            ZStack(alignment: .topTrailing) {
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Palette.surfaceAlt)
                                if let image = client.screenshotImage(suiteId, named: shot.name) {
                                    image
                                        .resizable()
                                        .interpolation(.medium)
                                        .aspectRatio(contentMode: .fit)
                                }
                                if opened == shot.name {
                                    // The one action a picture needs, shown on
                                    // the picture rather than behind a long
                                    // press nobody discovers.
                                    Button {
                                        client.deleteScreenshot(suiteId, named: shot.name)
                                        opened = nil
                                        shots = client.screenshots(suiteId)
                                    } label: {
                                        Image(systemName: "trash")
                                            .padding(8)
                                            .foregroundStyle(Palette.bad)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .aspectRatio(0.75, contentMode: .fit)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .onTapGesture {
                                opened = opened == shot.name ? nil : shot.name
                            }
                        }
                    }
                    .padding(12)
                }
            }
        }
        .background(Palette.background)
        .navigationTitle("Ảnh chụp")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { shots = client.screenshots(suiteId) }
    }
}
