package com.mobicore.tools;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

/**
 * The screenshots one game has: what the in-game camera button leaves behind.
 *
 * <p>Every frame here is a real one, taken from the running fixture a few
 * ticks apart — the same bytes the app writes to
 * {@code screenshots/<game>/<when>.png}, shown the way the gallery shows
 * them.</p>
 */
public final class ShotsScreen {

    private final String fixtureDir;

    public ShotsScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public Framebuffer render() throws Exception {
        EmulatorScreen emulator = new EmulatorScreen(fixtureDir);
        EmulatorSession session = emulator.boot();

        Framebuffer[] shots = new Framebuffer[4];
        for (int i = 0; i < shots.length; i++) {
            for (int tick = 0; tick < 9; tick++) {
                session.vm().callVirtual(session.context().current(), "tick", "()V");
                session.renderFrame();
            }
            if (i == 1) {
                session.keyPressed(MidpContext.KEY_FIRE);
            }
            shots[i] = session.screen().copy();
        }

        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);

        int bar = 74;
        frame.setColor(Theme.SURFACE);
        frame.fillRect(0, 0, frame.width(), bar);
        frame.setColor(Theme.BORDER);
        frame.fillRect(0, bar - 1, frame.width(), 1);
        int glyph = 26;
        Icons.draw(frame, Icons.BACK, 12, (bar - glyph) / 2, glyph, Theme.ACCENT);
        ui.text(ui.large(), "Ảnh chụp", 12 + glyph + 8, (bar - ui.large().height()) / 2,
                Theme.TEXT);
        ui.textRight(ui.small(), (shots.length - 1) + " ảnh, 1 đoạn quay", frame.width() - 16,
                (bar - ui.small().height()) / 2, Theme.TEXT_DIM);

        int gap = 12;
        int columns = 2;
        int tile = (frame.width() - gap * (columns + 1)) / columns;
        int tileHeight = tile * 4 / 3;
        for (int i = 0; i < shots.length; i++) {
            int x = gap + (i % columns) * (tile + gap);
            int y = bar + gap + (i / columns) * (tileHeight + gap);
            ui.panel(x, y, tile, tileHeight, Theme.SURFACE_ALT, Theme.BORDER);

            // Fitted, never cropped: a screenshot cut to fit a square tile is
            // a screenshot of something other than what was on screen.
            Framebuffer shot = shots[i];
            int width = tile - 12;
            int height = width * shot.height() / shot.width();
            if (height > tileHeight - 12) {
                height = tileHeight - 12;
                width = height * shot.width() / shot.height();
            }
            frame.setBlendMode(Framebuffer.BLEND_REPLACE);
            frame.drawFramebuffer(shot.scaleSmooth(width, height),
                    x + (tile - width) / 2, y + (tileHeight - height) / 2);
            frame.setBlendMode(Framebuffer.BLEND_SRC_OVER);

            // A clip and a picture share this gallery, so the clip says which
            // it is; its thumbnail is its own first frame.
            if (i == shots.length - 1) {
                int badgeHeight = ui.small().height() + 10;
                int badgeWidth = ui.small().stringWidth("Đoạn quay") + 34;
                int badgeX = x + 8;
                int badgeY = y + tileHeight - badgeHeight - 8;
                frame.setColor(0xCC000000);
                frame.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 10, 10);
                Icons.draw(frame, Icons.RECORD, badgeX + 6, badgeY + 5,
                        ui.small().height(), Theme.ACCENT);
                ui.text(ui.small(), "Đoạn quay", badgeX + 6 + ui.small().height() + 4,
                        badgeY + 5, 0xFFFFFFFF);
            }
        }

        // One tile open, showing the only action a picture needs.
        int openX = gap;
        int openY = bar + gap;
        frame.setColor(0x66000000);
        frame.fillRoundRect(openX, openY, tile, tileHeight, 18, 18);
        Icons.drawCentred(frame, Icons.DELETE, openX + tile - 26, openY + 26, 24, Theme.BAD);
        return frame;
    }
}
