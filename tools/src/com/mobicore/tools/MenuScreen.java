package com.mobicore.tools;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

/**
 * Previews of the high level screens, one PNG per state.
 *
 * <p>These are the screens the emulator draws rather than the game: a MIDlet
 * hands over a List and gets back whatever the device makes of it, so the only
 * way to review them is to look at them.</p>
 */
public final class MenuScreen {

    private static final int GAME_WIDTH = 240;
    private static final int GAME_HEIGHT = 320;
    private static final int SCALE = 2;

    private final String fixtureDir;
    private final String state;

    public MenuScreen(String fixtureDir, String state) {
        this.fixtureDir = fixtureDir;
        this.state = state;
    }

    /** Boots MenuDemo and drives it to the state being photographed. */
    private EmulatorSession boot() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, GAME_WIDTH, GAME_HEIGHT,
                new EmulatorScreen.FixedClock());
        session.start("demo.MenuDemo");
        session.renderFrame();

        if ("form".equals(state) || "menu".equals(state)) {
            select(session, 1);
            // Park the focus on the gauge: it shows a label, a value and the
            // focus highlight in one row.
            session.keyPressed(MidpContext.KEY_DOWN);
            session.keyPressed(MidpContext.KEY_DOWN);
            if ("menu".equals(state)) {
                session.pressButton("softLeft");
            }
        } else if ("textbox".equals(state)) {
            select(session, 2);
            // "tin", typed the way a keypad types it: 8 once, 4 three times,
            // 6 twice.
            type(session, '8', 1);
            type(session, '4', 3);
            type(session, '6', 2);
        } else if ("alert".equals(state)) {
            select(session, 4);
        }
        session.renderFrame();
        return session;
    }

    private static void select(EmulatorSession session, int row) {
        for (int i = 0; i < row; i++) {
            session.keyPressed(MidpContext.KEY_DOWN);
        }
        session.keyPressed(MidpContext.KEY_FIRE);
        session.renderFrame();
    }

    private static void type(EmulatorSession session, int key, int taps) {
        for (int i = 0; i < taps; i++) {
            session.keyPressed(key);
        }
    }

    public Framebuffer render() throws Exception {
        EmulatorSession session = boot();
        int barHeight = 40;
        Framebuffer frame = new Framebuffer(GAME_WIDTH * SCALE, GAME_HEIGHT * SCALE + barHeight);
        frame.setAntialias(true);
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);

        frame.setColor(Theme.SURFACE);
        frame.fillRect(0, 0, frame.width(), barHeight);
        ui.text(ui.small(), caption(), Ui.PAD, (barHeight - ui.small().height()) / 2, Theme.TEXT);
        ui.textRight(ui.small(), GAME_WIDTH + "×" + GAME_HEIGHT,
                frame.width() - Ui.PAD, (barHeight - ui.small().height()) / 2, Theme.TEXT_DIM);

        // Smoothed, like every other preview and like the phone: a handset
        // packed these pixels into about two inches, and blowing them up as
        // hard blocks looks more pixelated than the hardware ever did.
        Framebuffer scaled = session.profile().smoothing()
                ? session.screen().scaleSmooth(GAME_WIDTH * SCALE, GAME_HEIGHT * SCALE)
                : session.screen().scaleNearest(GAME_WIDTH * SCALE, GAME_HEIGHT * SCALE);
        frame.setBlendMode(Framebuffer.BLEND_REPLACE);
        frame.drawFramebuffer(scaled, 0, barHeight);
        frame.setBlendMode(Framebuffer.BLEND_SRC_OVER);
        return frame;
    }

    private String caption() {
        if ("form".equals(state)) {
            return "Form — StringItem, TextField, Gauge, ChoiceGroup";
        }
        if ("menu".equals(state)) {
            return "Tuỳ chọn — các lệnh không vừa hai phím mềm";
        }
        if ("textbox".equals(state)) {
            return "TextBox — gõ đa chạm trên bàn phím số";
        }
        if ("alert".equals(state)) {
            return "Alert — hộp thoại của MIDlet";
        }
        return "List (IMPLICIT) — menu chính của MIDlet";
    }
}
