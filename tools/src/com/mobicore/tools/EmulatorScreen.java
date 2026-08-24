package com.mobicore.tools;

import com.mobicore.core.emu.EmulatorLog;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.vm.VmHost;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.util.List;

/**
 * Preview of the emulator screen: the demo MIDlet running inside the virtual
 * phone, with the on-screen keypad below it.
 */
public final class EmulatorScreen {

    private static final int SCREEN_WIDTH = 240;
    private static final int SCREEN_HEIGHT = 320;

    private final String fixtureDir;
    private EmulatorSession session;

    public EmulatorScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public EmulatorSession session() {
        return session;
    }

    /** Boots the suite and advances it far enough to be worth looking at. */
    public EmulatorSession boot() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        session = EmulatorSession.create(suite, SCREEN_WIDTH, SCREEN_HEIGHT, new FixedClock());
        session.start();
        for (int i = 0; i < 42; i++) {
            session.vm().callVirtual(session.context().current(), "tick", "()V");
            session.renderFrame();
        }
        session.keyPressed(MidpContext.KEY_RIGHT);
        session.keyPressed(MidpContext.KEY_FIRE);
        for (int i = 0; i < 4; i++) {
            session.vm().callVirtual(session.context().current(), "tick", "()V");
            session.renderFrame();
        }
        return session;
    }

    public Framebuffer render() throws Exception {
        if (session == null) {
            boot();
        }
        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);
        ui.appBar("Sky Runner", session.info().vendor());

        // Phone bezel with the emulated screen inside it -------------------
        int scale = 1;
        int screenX = (frame.width() - SCREEN_WIDTH * scale) / 2;
        int screenY = 58;
        frame.setColor(0xFF11161D);
        frame.fillRoundRect(screenX - 12, screenY - 12, SCREEN_WIDTH * scale + 24,
                SCREEN_HEIGHT * scale + 24, 24, 24);
        frame.setColor(0xFF2C3543);
        frame.drawRoundRect(screenX - 12, screenY - 12, SCREEN_WIDTH * scale + 23,
                SCREEN_HEIGHT * scale + 23, 24, 24);
        frame.setBlendMode(Framebuffer.BLEND_REPLACE);
        frame.drawFramebuffer(session.screen(), screenX, screenY);
        frame.setBlendMode(Framebuffer.BLEND_SRC_OVER);

        int y = screenY + SCREEN_HEIGHT * scale + 24;
        drawKeypad(ui, screenX - 12, y, SCREEN_WIDTH + 24);

        y += 132;
        List<EmulatorLog.Entry> entries = session.log().entries();
        ui.panel(16, y, frame.width() - 32, 26 + Math.min(3, entries.size()) * 16,
                Theme.SURFACE, Theme.BORDER);
        ui.text(ui.small(), "EMULATOR LOG", 30, y + 8, Theme.TEXT_DIM);
        int lineY = y + 24;
        for (int i = Math.max(0, entries.size() - 3); i < entries.size(); i++) {
            EmulatorLog.Entry entry = entries.get(i);
            ui.text(ui.small(), entry.toString(), 30, lineY,
                    entry.level == EmulatorLog.LEVEL_ERROR ? Theme.BAD : Theme.TEXT);
            lineY += 16;
        }
        return frame;
    }

    /** Nokia-style keypad: D-pad, softkeys and the numeric grid. */
    private void drawKeypad(Ui ui, int x, int y, int width) {
        Framebuffer frame = ui.frame();
        frame.setColor(0xFF161C24);
        frame.fillRoundRect(x, y, width, 124, 18, 18);
        frame.setColor(0xFF2C3543);
        frame.drawRoundRect(x, y, width - 1, 123, 18, 18);

        int padCenterX = x + 58;
        int padCenterY = y + 44;
        arrowKey(ui, padCenterX - 14, padCenterY - 34, 28, 22, 0);
        arrowKey(ui, padCenterX - 14, padCenterY + 12, 28, 22, 1);
        arrowKey(ui, padCenterX - 48, padCenterY - 11, 30, 22, 2);
        arrowKey(ui, padCenterX + 18, padCenterY - 11, 30, 22, 3);
        key(ui, padCenterX - 14, padCenterY - 11, 28, 22, "OK", true);

        key(ui, x + 12, y + 96, 52, 20, "Pause", false);
        key(ui, x + width - 64, y + 96, 52, 20, "Exit", false);

        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        int gridX = x + width - 128;
        for (int i = 0; i < labels.length; i++) {
            int column = i % 3;
            int row = i / 3;
            key(ui, gridX + column * 40, y + 6 + row * 22, 34, 19, labels[i], false);
        }
    }

    /** Directional key with a drawn triangle: 0 up, 1 down, 2 left, 3 right. */
    private void arrowKey(Ui ui, int x, int y, int w, int h, int direction) {
        Framebuffer frame = ui.frame();
        frame.setColor(0xFF1D3547);
        frame.fillRoundRect(x, y, w, h, 8, 8);
        frame.setColor(Theme.ACCENT);
        frame.drawRoundRect(x, y, w - 1, h - 1, 8, 8);
        int cx = x + w / 2;
        int cy = y + h / 2;
        int size = 5;
        switch (direction) {
            case 0: frame.fillTriangle(cx, cy - size, cx - size, cy + size, cx + size, cy + size); break;
            case 1: frame.fillTriangle(cx, cy + size, cx - size, cy - size, cx + size, cy - size); break;
            case 2: frame.fillTriangle(cx - size, cy, cx + size, cy - size, cx + size, cy + size); break;
            default: frame.fillTriangle(cx + size, cy, cx - size, cy - size, cx - size, cy + size); break;
        }
    }

    private void key(Ui ui, int x, int y, int w, int h, String label, boolean accent) {
        Framebuffer frame = ui.frame();
        frame.setColor(accent ? 0xFF1D3547 : 0xFF222A35);
        frame.fillRoundRect(x, y, w, h, 8, 8);
        frame.setColor(accent ? Theme.ACCENT : Theme.BORDER);
        frame.drawRoundRect(x, y, w - 1, h - 1, 8, 8);
        ui.textCenter(ui.small(), label, x + w / 2, y + (h - ui.small().height()) / 2 + 1,
                accent ? Theme.ACCENT : Theme.TEXT_DIM);
    }

    /** Deterministic clock so screenshots are reproducible. */
    static final class FixedClock implements VmHost {

        private long now = 1_700_000_000_000L;

        public long currentTimeMillis() {
            now += 33;
            return now;
        }

        public void print(boolean error, String text) {
        }

        public void exit(int code) {
        }

        public String property(String name) {
            return null;
        }

        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(Math.min(millis, 5));
        }
    }
}
