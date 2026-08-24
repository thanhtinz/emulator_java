package com.mobicore.tools;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.vm.VmHost;
import com.mobicore.tools.ui.Icons;
import com.mobicore.tools.ui.Theme;
import com.mobicore.tools.ui.Ui;

/**
 * Preview of the emulator screen.
 *
 * <p>The game fills the display edge to edge — there is no room to waste on a
 * decorative phone bezel when the emulated screen is already small — and the
 * controls sit below it. The directional pad is on the right, where a thumb
 * reaches it while the other hand holds the phone.</p>
 */
public final class EmulatorScreen {

    private static final int GAME_WIDTH = 240;
    private static final int GAME_HEIGHT = 320;
    private static final int SCALE = 2;

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
        session = EmulatorSession.create(suite, GAME_WIDTH, GAME_HEIGHT, new FixedClock());
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

        int barHeight = ui.medium().height() + 22;
        int gameHeight = GAME_HEIGHT * SCALE;
        Framebuffer scaled = session.profile().smoothing()
                ? session.screen().scaleSmooth(GAME_WIDTH * SCALE, gameHeight)
                : session.screen().scaleNearest(GAME_WIDTH * SCALE, gameHeight);
        frame.setBlendMode(Framebuffer.BLEND_REPLACE);
        frame.drawFramebuffer(scaled, 0, barHeight);
        frame.setBlendMode(Framebuffer.BLEND_SRC_OVER);

        topBar(ui, barHeight);
        controls(ui, barHeight + gameHeight, frame.height() - barHeight - gameHeight);
        return frame;
    }

    /**
     * The emulator's own bar, kept clearly distinct from the handset chrome
     * below it. It deliberately does not repeat the game's title or offer a
     * second "pause": the MIDlet has its own title bar and its own softkeys,
     * and two sets of the same word is how a player ends up pressing the wrong
     * one.
     */
    private void topBar(Ui ui, int barHeight) {
        Framebuffer frame = ui.frame();
        frame.setColor(Theme.SURFACE);
        frame.fillRect(0, 0, frame.width(), barHeight);
        int textY = (barHeight - ui.medium().height()) / 2;
        int glyph = ui.medium().height() + 4;
        Icons.draw(frame, Icons.BACK, Ui.PAD, (barHeight - glyph) / 2, glyph, Theme.ACCENT);
        ui.text(ui.medium(), "Thư viện", Ui.PAD + glyph + 4, textY, Theme.ACCENT);
        ui.textCenter(ui.small(), GAME_WIDTH + "×" + GAME_HEIGHT + "  ·  " + SCALE
                        + "×  ·  30 hình/giây",
                frame.width() / 2, (barHeight - ui.small().height()) / 2, Theme.TEXT_DIM);
        ui.textRight(ui.medium(), "Menu", frame.width() - Ui.PAD, textY, Theme.ACCENT);
    }

    /**
     * The keypad, cut down to the keys a game actually reads: the two softkeys
     * directly under the screen, so they line up with the labels the system
     * draws there, the numbers and the directional pad. The call, end and clear
     * keys a handset carried are gone — they were there because the device was
     * a phone, and on screen they only crowded the keys that matter.
     */
    private void controls(Ui ui, int top, int height) {
        Framebuffer frame = ui.frame();
        frame.setColor(Theme.SURFACE);
        frame.fillRect(0, top, frame.width(), height);
        frame.setColor(Theme.BORDER);
        frame.fillRect(0, top, frame.width(), 1);

        int y = top + 12;
        // Both softkeys get exactly the same box, and the row is symmetric
        // about the middle: whatever the two labels are, neither key looks
        // like the more important one.
        int gap = 12;
        int softWidth = (frame.width() - Ui.PAD * 2 - gap) / 2;
        int softHeight = ui.medium().height() + 18;
        int rightX = frame.width() - Ui.PAD - softWidth;
        softKey(ui, Ui.PAD, y, softWidth, session.leftSoftKeyLabel());
        softKey(ui, rightX, y, softWidth, session.rightSoftKeyLabel());

        int padTop = y + softHeight + 14;
        numericPad(ui, Ui.PAD + 4, padTop);
        directionalPad(ui, frame.width() - Ui.PAD - 208, padTop + 16);
    }

    /**
     * A softkey button showing whatever label the running screen has mapped to
     * it, which is the whole point of the pair: on a handset these are blank
     * until a MIDlet registers a Command.
     *
     * <p>The label is centred. Which side of the screen the key is on already
     * says which command it runs — the label bar the system draws inside the
     * screen sits directly above it — so pushing the text out to the edges
     * only made "Tạm dừng" and "Thoát" lean away from each other.</p>
     */
    private void softKey(Ui ui, int x, int y, int width, String label) {
        int height = ui.medium().height() + 18;
        boolean bound = label != null && label.length() > 0;
        ui.panel(x, y, width, height, bound ? Theme.SURFACE_ALT : Theme.BG, Theme.BORDER);
        String text = bound ? label : "—";
        int textY = y + (height - ui.mediumBold().height()) / 2;
        ui.textCenter(ui.mediumBold(), text, x + width / 2, textY,
                bound ? Theme.TEXT : Theme.TEXT_DIM);
    }

    /** The 3x4 grid, in the order a handset lays it out. */
    private void numericPad(Ui ui, int x, int y) {
        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        String[] hints = {"", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz", "", "+", ""};
        int keyWidth = 62;
        int keyHeight = 44;
        int gap = 6;
        for (int i = 0; i < labels.length; i++) {
            int column = i % 3;
            int row = i / 3;
            int keyX = x + column * (keyWidth + gap);
            int keyY = y + row * (keyHeight + gap);
            ui.panel(keyX, keyY, keyWidth, keyHeight, Theme.SURFACE_ALT, Theme.BORDER);
            boolean hasHint = hints[i].length() > 0;
            int labelY = hasHint ? keyY + 3 : keyY + (keyHeight - ui.large().height()) / 2;
            ui.textCenter(ui.large(), labels[i], keyX + keyWidth / 2, labelY, Theme.TEXT);
            if (hasHint) {
                ui.textCenter(ui.small(), hints[i], keyX + keyWidth / 2,
                        keyY + keyHeight - ui.small().height() - 3, Theme.TEXT_DIM);
            }
        }
    }

    /** The directional pad, with fire in the middle as MIDP's select action. */
    private void directionalPad(Ui ui, int x, int y) {
        int keyWidth = 66;
        int keyHeight = 54;
        int gap = 5;
        int centreX = x + keyWidth + gap;
        int middleY = y + keyHeight + gap;

        arrowKey(ui, centreX, y, keyWidth, keyHeight, 0);
        arrowKey(ui, x, middleY, keyWidth, keyHeight, 2);
        arrowKey(ui, centreX + keyWidth + gap, middleY, keyWidth, keyHeight, 3);
        arrowKey(ui, centreX, middleY + keyHeight + gap, keyWidth, keyHeight, 1);

        ui.panel(centreX, middleY, keyWidth, keyHeight, Theme.ACCENT_DIM, Theme.ACCENT);
        ui.textCenter(ui.mediumBold(), "OK", centreX + keyWidth / 2,
                middleY + (keyHeight - ui.medium().height()) / 2, Theme.ACCENT);
    }

    /** The Material arrow for each direction: 0 up, 1 down, 2 left, 3 right. */
    private static final String[] ARROWS = {
            Icons.UP, Icons.DOWN, Icons.LEFT, Icons.RIGHT,
    };

    /**
     * Directional key. The arrow is the Material chevron the Android keypad
     * puts on the same key, so the two keypads cannot drift apart.
     */
    private void arrowKey(Ui ui, int x, int y, int w, int h, int direction) {
        ui.panel(x, y, w, h, Theme.KEY, Theme.ACCENT);
        Icons.drawCentred(ui.frame(), ARROWS[direction], x + w / 2, y + h / 2, 40, Theme.ACCENT);
    }

    /** Deterministic clock so screenshots are reproducible. */
    public static final class FixedClock implements VmHost {

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
