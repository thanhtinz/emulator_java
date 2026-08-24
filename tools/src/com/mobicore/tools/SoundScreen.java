package com.mobicore.tools;

import com.mobicore.core.audio.AudioLog;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

import java.util.List;

/**
 * The sound demo, with what it actually played listed beside it.
 *
 * <p>A screenshot cannot carry sound, so the recording sink is read back and
 * printed: every clip the MIDlet started, how long it is, how many times it
 * repeats and at what volume. That is also the fastest way to see whether a
 * game's audio is reaching the emulator at all.</p>
 */
public final class SoundScreen {

    private static final int GAME_WIDTH = 240;
    private static final int GAME_HEIGHT = 320;
    private static final int SCALE = 2;

    private final String fixtureDir;

    public SoundScreen(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    public Framebuffer render() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, GAME_WIDTH, GAME_HEIGHT,
                new EmulatorScreen.FixedClock());
        session.start("demo.SoundDemo");
        session.renderFrame();

        AudioLog log = (AudioLog) session.audio();
        List<AudioLog.Entry> played = log.entries();

        int barHeight = 40;
        int listHeight = 40 + played.size() * 46 + 16;
        Framebuffer frame = new Framebuffer(GAME_WIDTH * SCALE,
                GAME_HEIGHT * SCALE + barHeight + listHeight);
        frame.setAntialias(true);
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);

        frame.setColor(Theme.SURFACE);
        frame.fillRect(0, 0, frame.width(), barHeight);
        ui.text(ui.small(), "Âm thanh — MIDlet chạy bằng bytecode", Ui.PAD,
                (barHeight - ui.small().height()) / 2, Theme.TEXT);
        ui.textRight(ui.small(), GAME_WIDTH + "×" + GAME_HEIGHT, frame.width() - Ui.PAD,
                (barHeight - ui.small().height()) / 2, Theme.TEXT_DIM);

        Framebuffer scaled = session.screen().scaleSmooth(GAME_WIDTH * SCALE, GAME_HEIGHT * SCALE);
        frame.setBlendMode(Framebuffer.BLEND_REPLACE);
        frame.drawFramebuffer(scaled, 0, barHeight);
        frame.setBlendMode(Framebuffer.BLEND_SRC_OVER);

        int y = barHeight + GAME_HEIGHT * SCALE + 12;
        ui.text(ui.small(), "GAME ĐÃ PHÁT", Ui.PAD, y, Theme.TEXT_DIM);
        y += ui.small().height() + 10;
        for (int i = 0; i < played.size(); i++) {
            row(ui, played.get(i), Ui.PAD, y, frame.width() - Ui.PAD * 2, i);
            y += 46;
        }
        return frame;
    }

    private void row(Ui ui, AudioLog.Entry entry, int x, int y, int width, int index) {
        Framebuffer frame = ui.frame();
        ui.panel(x, y, width, 40, Theme.SURFACE, Theme.BORDER);
        int glyph = 20;
        Icons.draw(frame, index == 0 ? Icons.PLAY : Icons.SAVE, x + 12, y + 10, glyph,
                Theme.ACCENT);
        ui.text(ui.medium(), label(index), x + 12 + glyph + 10, y + 11, Theme.TEXT);
        String detail = entry.clip.durationMs() + "ms  ·  "
                + (entry.loops == 0 ? "lặp mãi" : entry.loops + " lần")
                + "  ·  " + entry.volume + "%";
        ui.textRight(ui.small(), detail, x + width - 12, y + 13, Theme.TEXT_DIM);
    }

    /** What each recorded clip came from, in the order the MIDlet plays them. */
    private String label(int index) {
        if (index == 0) {
            return "playTone — nốt La";
        }
        if (index == 1) {
            return "ToneControl — chuỗi nốt";
        }
        return "WAV — hiệu ứng";
    }
}
