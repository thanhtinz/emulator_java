package com.mobicore.tools;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.emu.SaveState;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngWriter;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.library.LibraryEntry;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

/**
 * The save slots one game has.
 *
 * <p>Real ones: the fixture is played for a while and saved into two slots
 * through the same code the app uses, so the pictures beside them are the
 * screens those saves actually hold.</p>
 */
public final class SlotsScreen {

    private final String fixtureDir;

    public SlotsScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public Framebuffer render() throws Exception {
        Vfs vfs = new MemoryVfs();
        StorageLayout layout = new StorageLayout("/data/MobiCore");
        GameLibrary library = new GameLibrary(vfs, layout);
        library.setClock(1_700_000_000_000L);
        library.open();
        LibraryEntry entry = library.install(SampleSuite.jar(fixtureDir), SampleSuite.jad())
                .entry();

        EmulatorScreen emulator = new EmulatorScreen(fixtureDir);
        EmulatorSession session = emulator.boot();
        // Slot 2 first, then the automatic one, so the times differ the way
        // they would after a session: save, play on, quit.
        save(library, entry.suiteId(), session, 2);
        for (int i = 0; i < 12; i++) {
            session.vm().callVirtual(session.context().current(), "tick", "()V");
            session.renderFrame();
        }
        save(library, entry.suiteId(), session, StorageLayout.SLOT_AUTO);

        Framebuffer frame = Preview.newPage();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);

        int bar = 74;
        frame.setColor(Theme.SURFACE);
        frame.fillRect(0, 0, frame.width(), bar);
        frame.setColor(Theme.BORDER);
        frame.fillRect(0, bar - 1, frame.width(), 1);
        int glyph = 26;
        Icons.draw(frame, Icons.BACK, 12, (bar - glyph) / 2, glyph, Theme.ACCENT);
        ui.text(ui.large(), "Ô lưu trạng thái", 12 + glyph + 8,
                (bar - ui.large().height()) / 2, Theme.TEXT);
        ui.textRight(ui.small(), "2/5 ô đã dùng", frame.width() - 16,
                (bar - ui.small().height()) / 2, Theme.TEXT_DIM);

        int margin = 16;
        int width = frame.width() - margin * 2;
        int height = 128;
        int y = bar + 14;
        for (int slot = 0; slot <= StorageLayout.SLOTS; slot++) {
            ui.panel(margin, y, width, height, Theme.SURFACE, Theme.BORDER);

            int shotWidth = 78;
            int shotHeight = height - 24;
            byte[] png = library.saveStateThumbnail(entry.suiteId(), slot);
            ui.panel(margin + 14, y + 12, shotWidth, shotHeight, Theme.SURFACE_ALT, Theme.BORDER);
            if (png != null && png.length > 0) {
                com.mobicore.core.gfx.PngReader.Image decoded =
                        com.mobicore.core.gfx.PngReader.decode(png);
                Framebuffer shot = Framebuffer.wrap(decoded.pixels, decoded.width, decoded.height);
                int drawHeight = shotHeight - 8;
                int drawWidth = drawHeight * shot.width() / shot.height();
                frame.setBlendMode(Framebuffer.BLEND_REPLACE);
                frame.drawFramebuffer(shot.scaleSmooth(drawWidth, drawHeight),
                        margin + 14 + (shotWidth - drawWidth) / 2, y + 16);
                frame.setBlendMode(Framebuffer.BLEND_SRC_OVER);
            }

            int textX = margin + 14 + shotWidth + 16;
            boolean used = library.hasSaveState(entry.suiteId(), slot);
            String name = slot == StorageLayout.SLOT_AUTO ? "Tự động (khi thoát)" : "Ô " + slot;
            ui.text(ui.mediumBold(), name, textX, y + 34, Theme.TEXT);
            ui.text(ui.small(), used ? "20:13  17/11/2023" : "Trống", textX,
                    y + 34 + ui.mediumBold().height() + 8, used ? Theme.TEXT_DIM : Theme.TEXT_DIM);
            if (used) {
                ui.textRight(ui.small(), "Xoá", margin + width - 16, y + 34, Theme.BAD);
            }
            y += height + 12;
        }
        return Preview.fit(frame);
    }

    private void save(GameLibrary library, String suiteId, EmulatorSession session, int slot)
            throws Exception {
        library.writeSaveState(suiteId, slot, SaveState.capture(session),
                PngWriter.encode(session.screen()));
    }
}
