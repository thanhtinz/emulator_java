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
        ui.appBar(entry.title(), "Game settings");

        int margin = 16;
        int width = frame.width() - margin * 2;
        int y = 60;

        // Device profile -------------------------------------------------
        ui.panel(margin, y, width, 96, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "DEVICE PROFILE", margin + 14, y + 10, Theme.TEXT_DIM);
        int chipX = margin + 14;
        int chipY = y + 28;
        for (DeviceProfile candidate : DeviceProfile.catalog()) {
            boolean selected = candidate.id().equals(profile.device().id());
            int chipWidth = ui.small().stringWidth(candidate.resolution()) + 12;
            if (chipX + chipWidth > margin + width - 14) {
                chipX = margin + 14;
                chipY += 20;
            }
            ui.chip(candidate.resolution(), chipX, chipY,
                    selected ? Theme.ACCENT : Theme.TEXT_DIM,
                    selected ? Theme.ACCENT_DIM : Theme.SURFACE_ALT);
            chipX += chipWidth + 6;
        }
        ui.field("Keypad", profile.device().keypadName(), margin + 14, y + 74, width - 28);
        y += 108;

        // Display --------------------------------------------------------
        ui.panel(margin, y, width, 96, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "DISPLAY & AUDIO", margin + 14, y + 10, Theme.TEXT_DIM);
        ui.field("Scaling", profile.scaleModeName(), margin + 14, y + 30, width - 28);
        ui.field("Frame limit", profile.frameLimit() + " fps", margin + 14, y + 50, width - 28);
        ui.field("Volume", profile.volume() + "%", margin + 14, y + 70, width - 28);
        y += 108;

        // Input mapping --------------------------------------------------
        String[] shown = {"up", "down", "left", "right", "fire", "softLeft", "softRight", "num5"};
        int mappingHeight = 48 + ((shown.length + 1) / 2) * 20;
        ui.panel(margin, y, width, mappingHeight, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "INPUT MAPPING", margin + 14, y + 10, Theme.TEXT_DIM);
        ui.textRight(ui.small(), profile.input().presetName(), margin + width - 14, y + 10, Theme.ACCENT);
        int rowY = y + 30;
        int columnWidth = (width - 28) / 2;
        for (int i = 0; i < shown.length; i++) {
            String button = shown[i];
            int column = i % 2;
            int x = margin + 14 + column * columnWidth;
            int code = profile.input().keyCodeFor(button);
            ui.text(ui.small(), button, x, rowY, Theme.TEXT_DIM);
            String label = MidpContext.keyName(code) + " (" + code + ")";
            int turbo = profile.input().turboFor(button);
            if (turbo > 0) {
                label = label + " T" + turbo;
            }
            ui.textRight(ui.smallBold(), label, x + columnWidth - 12, rowY, Theme.TEXT);
            if (column == 1) {
                rowY += 20;
            }
        }
        y += mappingHeight + 12;

        // Saves ----------------------------------------------------------
        RecordStoreManager records = library.records(entry.suiteId());
        List<String> stores = records.listStoreNames();
        int saveHeight = 52 + stores.size() * 20;
        ui.panel(margin, y, width, saveHeight, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "SAVES (RMS)", margin + 14, y + 10, Theme.TEXT_DIM);
        int storeY = y + 30;
        for (String storeName : stores) {
            RecordStoreManager.Store store = records.openStore(storeName, false);
            ui.text(ui.small(), storeName, margin + 14, storeY, Theme.TEXT);
            ui.textRight(ui.smallBold(), store.size() + " records / " + store.byteSize() + " B",
                    margin + width - 14, storeY, Theme.GOOD);
            storeY += 20;
        }
        ui.field("Backups", library.backupsFor(entry.suiteId()).size() + " snapshot",
                margin + 14, storeY + 4, width - 28);
        y += saveHeight + 12;

        // Sandbox + network ----------------------------------------------
        ui.panel(margin, y, width, 76, Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "SANDBOX", margin + 14, y + 10, Theme.TEXT_DIM);
        ui.field("Network", profile.networkModeName(), margin + 14, y + 30, width - 28);
        ui.text(ui.small(), ui.ellipsize(ui.small(), backupPath, width - 28),
                margin + 14, y + 52, Theme.TEXT_DIM);

        return frame;
    }
}
