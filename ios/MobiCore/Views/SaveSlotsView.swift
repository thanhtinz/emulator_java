import SwiftUI

/// Ô lưu trạng thái của một trò chơi.
///
/// Four slots the player writes by hand and one the emulator writes when the
/// game is left. They are kept apart because quitting must not overwrite the
/// place someone saved deliberately, and each carries the screen as it looked:
/// coming back to four saves, the picture says which is which far faster than
/// a date does.
struct SaveSlotsView: View {

    let suiteId: String

    @EnvironmentObject private var client: MobiCoreClient
    @State private var slots: [SaveSlot] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(slots) { slot in
                    SectionCard {
                        HStack(spacing: 14) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 10).fill(Palette.surfaceAlt)
                                if let image = client.saveSlotThumbnail(suiteId, slot: slot.slot) {
                                    image
                                        .resizable()
                                        .interpolation(.medium)
                                        .aspectRatio(contentMode: .fit)
                                }
                            }
                            .frame(width: 72, height: 96)

                            VStack(alignment: .leading, spacing: 4) {
                                Text(slot.auto ? "Tự động (khi thoát)" : "Ô \(slot.slot)")
                                    .font(.body.weight(.semibold))
                                    .foregroundStyle(Palette.text)
                                Text(slot.used ? whenSaved(slot.savedAt) : "Trống")
                                    .font(.footnote)
                                    .foregroundStyle(Palette.textDim)
                            }
                            Spacer()
                            if slot.used {
                                Button("Xoá") {
                                    client.deleteSaveState(suiteId, slot: slot.slot)
                                    slots = client.saveSlots(suiteId)
                                }
                                .font(.footnote)
                                .tint(Palette.bad)
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(Palette.background)
        .navigationTitle("Ô lưu trạng thái")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { slots = client.saveSlots(suiteId) }
    }

    /// The moment a slot was written, in the phone's own format.
    private func whenSaved(_ millis: Int64) -> String {
        guard millis > 0 else { return "Trống" }
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        return date.formatted(date: .abbreviated, time: .shortened)
    }
}
