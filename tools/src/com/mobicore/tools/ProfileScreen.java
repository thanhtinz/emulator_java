package com.mobicore.tools;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.model.InputProfile;
import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.util.List;

/**
 * Preview of the per-game settings screen: device profile, display, input
 * mapping and save management, all driven by a real install.
 */
public final class ProfileScreen {

    private final String fixtureDir;

    public ProfileScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public Framebuffer render() throws Exception {
        Vfs vfs = new MemoryVfs();
        StorageLayout layout = new StorageLayout("/data/MobiCore");
        GameLibrary library = new GameLibrary(vfs, layout);
        library.setClock(1_700_000_000_000L);
        library.open();

        LibraryEntry entry = library.install(SampleSuite.jar(fixtureDir), SampleSuite.jad()).entry();
        // Left exactly as the import configured it: the point of the screen
        // is that a player never has to touch any of this.
        GameProfile profile = library.profile(entry.suiteId());
        profile.setVolume(65);
        // Turbo on fire, so the screenshot shows the setting doing something.
        profile.input().setTurbo("fire", 50);
        // One key pointed somewhere else, which is what the row is for: this
        // game reads '2' for up, as plenty of them do.
        profile.input().setMapping("up", '2');
        profile.setFavourite(true);
        profile.markPlayed(1_700_000_000_000L);
        library.saveProfile(profile);

        // Generate a real save so the screen shows real numbers.
        EmulatorSession session = EmulatorSession.create(library.load(entry.suiteId()), profile,
                vfs, layout, null);
        session.start();
        session.vm().callVirtual(session.context().midlet(), "saveScore", "(I)I", Integer.valueOf(1240));
        session.vm().callVirtual(session.context().midlet(), "saveScore", "(I)I", Integer.valueOf(8630));
        session.vm().callVirtual(session.context().midlet(), "saveScore", "(I)I", Integer.valueOf(4110));
        session.destroy();
        String backupPath = library.backup(entry.suiteId());

        return draw(library, entry, profile, backupPath, vfs, layout);
    }

    private Framebuffer draw(GameLibrary library, LibraryEntry entry, GameProfile profile,
                             String backupPath, Vfs vfs, StorageLayout layout) throws Exception {
        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar(entry.title(), "Cài đặt");

        int margin = Ui.PAD;
        int width = frame.width() - margin * 2;
        int fieldX = margin + Ui.PAD;
        int fieldWidth = width - Ui.PAD * 2;
        int y = Ui.APP_BAR + 18;
        int row;

        // What the emulator worked out on its own ------------------------
        List<String> notes = profile.setupNotes();
        int autoHeight = 12 + ui.small().height() + 10 + notes.size() * 24
                + ui.medium().height() + 34;
        row = ui.section(margin, y, width, autoHeight, "ĐÃ TỰ CẤU HÌNH",
                profile.isAuto() ? "tự động" : "đã chỉnh tay");
        for (int i = 0; i < notes.size(); i++) {
            int glyph = ui.small().height() + 2;
            Icons.draw(frame, Icons.CHECK, fieldX, row + i * 24, glyph, Theme.GOOD);
            ui.text(ui.small(), ui.ellipsize(ui.small(), notes.get(i), fieldWidth - glyph - 8),
                    fieldX + glyph + 8, row + i * 24 + 1, Theme.TEXT);
        }
        ui.text(ui.small(), "Không cần chỉnh gì để chơi.", fieldX,
                row + notes.size() * 24 + 4, Theme.TEXT_DIM);
        y += autoHeight + 14;

        ui.text(ui.small(), "NÂNG CAO — chỉ mở khi game chạy sai", margin + Ui.PAD, y,
                Theme.TEXT_DIM);
        y += ui.small().height() + 12;

        // Screen ---------------------------------------------------------
        // One screen, so this states it rather than offering a choice.
        int deviceHeight = ui.sectionHeight(2);
        row = ui.section(margin, y, width, deviceHeight, "MÀN HÌNH", null);
        ui.field("Kích thước", profile.device().resolution(), fieldX, row, fieldWidth);
        ui.field("Kiểu bàn phím", profile.device().keypadName(), fieldX, row + Ui.ROW,
                fieldWidth);
        y += deviceHeight + 14;

        // Display and audio ----------------------------------------------
        int displayHeight = ui.sectionHeight(6);
        row = ui.section(margin, y, width, displayHeight, "HIỂN THỊ & ÂM THANH", null);
        ui.field("Phóng ảnh", scaleName(profile), fieldX, row, fieldWidth);
        ui.field("Làm mượt", profile.smoothingName(), fieldX, row + Ui.ROW, fieldWidth);
        ui.field("Giới hạn khung hình", profile.frameLimit() + " hình/giây", fieldX,
                row + Ui.ROW * 2, fieldWidth);
        ui.field("Âm lượng", profile.volume() + "%", fieldX, row + Ui.ROW * 3, fieldWidth);
        ui.field("Giữ tỉ lệ khung", profile.keepAspect() ? "Bật" : "Tắt", fieldX,
                row + Ui.ROW * 4, fieldWidth);
        ui.field("Rung", profile.vibration() ? "Bật" : "Tắt", fieldX,
                row + Ui.ROW * 5, fieldWidth);
        y += displayHeight + 14;

        // Presets --------------------------------------------------------
        // Real ones: saved out of this very profile through the same store
        // the app uses, so the screenshot cannot show a feature that does not
        // work.
        com.mobicore.core.library.PresetStore presets =
                new com.mobicore.core.library.PresetStore(vfs, layout);
        presets.save("Điện thoại của tôi", profile);
        presets.save("Chơi ban đêm", profile);
        java.util.List<String> names = presets.names();
        int presetHeight = ui.sectionHeight(names.size() + 1);
        row = ui.section(margin, y, width, presetHeight, "BỘ CẤU HÌNH",
                names.size() + " bộ");
        for (int i = 0; i < names.size(); i++) {
            ui.text(ui.medium(), names.get(i), fieldX, row, Theme.TEXT);
            ui.textRight(ui.small(), "Áp dụng    Xoá", fieldX + fieldWidth,
                    row + (ui.medium().height() - ui.small().height()) / 2, Theme.ACCENT);
            row += Ui.ROW;
        }
        ui.field("Lưu cấu hình này thành", "Tên bộ cấu hình  +", fieldX, row, fieldWidth);
        y += presetHeight + 14;

        // Input mapping --------------------------------------------------
        String[][] buttons = {
                {"up", "Lên"}, {"down", "Xuống"}, {"left", "Trái"}, {"right", "Phải"},
                {"fire", "Chọn"}, {"softLeft", "Phím mềm L"}, {"softRight", "Phím mềm R"},
                {"num5", "Phím 5"},
        };
        int mappingHeight = ui.sectionHeight(buttons.length);
        row = ui.section(margin, y, width, mappingHeight, "GÁN PHÍM", profile.input().presetName());
        for (String[] button : buttons) {
            int code = profile.input().keyCodeFor(button[0]);
            String value = MidpContext.keyName(code) + "  (" + code + ")";
            int turbo = profile.input().turboFor(button[0]);
            if (turbo > 0) {
                value = value + "  ·  liên thanh " + turbo + "ms";
            }
            ui.field(button[1], value, fieldX, row, fieldWidth);
            row += Ui.ROW;
        }
        y += mappingHeight + 14;

        // Saves ----------------------------------------------------------
        RecordStoreManager records = library.records(entry.suiteId());
        List<String> stores = records.listStoreNames();
        int saveHeight = ui.sectionHeight(Math.max(1, stores.size()) + 1);
        row = ui.section(margin, y, width, saveHeight, "DỮ LIỆU LƯU (RMS)", null);
        for (String storeName : stores) {
            RecordStoreManager.Store store = records.openStore(storeName, false);
            ui.field(storeName, store.size() + " bản ghi  ·  " + store.byteSize() + " B",
                    fieldX, row, fieldWidth);
            row += Ui.ROW;
        }
        ui.field("Sao lưu", library.backupsFor(entry.suiteId()).size() + " bản", fieldX, row,
                fieldWidth);
        y += saveHeight + 14;

        // Network --------------------------------------------------------
        int netHeight = 12 + ui.small().height() + 8 + Ui.ROW + ui.small().height() + 12;
        row = ui.section(margin, y, width, netHeight, "MẠNG", null);
        ui.field("Truy cập mạng", networkName(profile), fieldX, row, fieldWidth);
        ui.text(ui.small(), ui.ellipsize(ui.small(), backupPath, fieldWidth), fieldX,
                row + Ui.ROW, Theme.TEXT_DIM);

        return frame;
    }

    private static String scaleName(GameProfile profile) {
        switch (profile.scaleMode()) {
            case GameProfile.SCALE_FIT: return "Vừa khung";
            case GameProfile.SCALE_STRETCH: return "Kéo đầy";
            case GameProfile.SCALE_ORIGINAL: return "Nguyên cỡ";
            default: return "Bội số nguyên";
        }
    }

    private static String networkName(GameProfile profile) {
        switch (profile.networkMode()) {
            case GameProfile.NETWORK_BLOCKED: return "Chặn";
            case GameProfile.NETWORK_ALLOWED: return "Cho phép";
            default: return "Hỏi trước";
        }
    }
}
