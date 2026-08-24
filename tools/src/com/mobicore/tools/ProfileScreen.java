package com.mobicore.tools;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.model.DeviceProfile;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.model.InputProfile;
import com.mobicore.core.rms.RecordStoreManager;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
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
        GameProfile profile = library.profile(entry.suiteId());
        profile.setDevice(DeviceProfile.QVGA_240x320);
        profile.setInput(InputProfile.sonyEricsson());
        profile.input().remap("fire", '5');
        profile.input().setTurbo("num5", 60);
        profile.setScaleMode(GameProfile.SCALE_INTEGER);
        profile.setFrameLimit(30);
        profile.setVolume(65);
        profile.setFavourite(true);
        profile.setNetworkMode(GameProfile.NETWORK_ASK);
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

        return draw(library, entry, profile, backupPath);
    }

    private Framebuffer draw(GameLibrary library, LibraryEntry entry, GameProfile profile,
                             String backupPath) throws Exception {
        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar(entry.title(), "Cài đặt");

        int margin = Ui.PAD;
        int width = frame.width() - margin * 2;
        int fieldX = margin + Ui.PAD;
        int fieldWidth = width - Ui.PAD * 2;
        int y = Ui.APP_BAR + 18;

        // Device profile -------------------------------------------------
        int chipRow = ui.chipHeight() + 8;
        int deviceHeight = 12 + ui.small().height() + 8 + chipRow * 2 + Ui.ROW + 6;
        int row = ui.section(margin, y, width, deviceHeight, "MÁY GIẢ LẬP", null);
        int chipX = fieldX;
        int chipY = row;
        for (DeviceProfile candidate : DeviceProfile.catalog()) {
            boolean selected = candidate.id().equals(profile.device().id());
            int chipWidth = ui.small().stringWidth(candidate.resolution()) + 18;
            if (chipX + chipWidth > margin + width - Ui.PAD) {
                chipX = fieldX;
                chipY += chipRow;
            }
            ui.chip(candidate.resolution(), chipX, chipY,
                    selected ? Theme.ACCENT : Theme.TEXT_DIM,
                    selected ? Theme.ACCENT_DIM : Theme.SURFACE_ALT);
            chipX += chipWidth + 8;
        }
        ui.field("Kiểu bàn phím", profile.device().keypadName(), fieldX, chipY + chipRow + 2,
                fieldWidth);
        y += deviceHeight + 14;

        // Display and audio ----------------------------------------------
        int displayHeight = ui.sectionHeight(4);
        row = ui.section(margin, y, width, displayHeight, "HIỂN THỊ & ÂM THANH", null);
        ui.field("Phóng ảnh", scaleName(profile), fieldX, row, fieldWidth);
        ui.field("Giới hạn khung hình", profile.frameLimit() + " hình/giây", fieldX,
                row + Ui.ROW, fieldWidth);
        ui.field("Âm lượng", profile.volume() + "%", fieldX, row + Ui.ROW * 2, fieldWidth);
        ui.field("Giữ tỉ lệ khung", profile.keepAspect() ? "Bật" : "Tắt", fieldX,
                row + Ui.ROW * 3, fieldWidth);
        y += displayHeight + 14;

        // Input mapping --------------------------------------------------
        String[][] buttons = {
                {"up", "Lên"}, {"down", "Xuống"}, {"left", "Trái"}, {"right", "Phải"},
                {"fire", "Chọn"}, {"softLeft", "Phím mềm 1"}, {"softRight", "Phím mềm 2"},
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
