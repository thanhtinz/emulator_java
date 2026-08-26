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

    private final String midletClass;

    public EmulatorScreen(String fixtureDir) {
        this(fixtureDir, null);
    }

    /** @param midletClass which MIDlet to boot, or null for the default one */
    public EmulatorScreen(String fixtureDir, String midletClass) {
        this.fixtureDir = fixtureDir;
        this.midletClass = midletClass;
    }

    public EmulatorSession session() {
        return session;
    }

    /** Boots the suite and advances it far enough to be worth looking at. */
    public EmulatorSession boot() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        session = EmulatorSession.create(suite, GAME_WIDTH, GAME_HEIGHT, new FixedClock());
        if (midletClass != null) {
            session.start(midletClass);
            return session;
        }
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

        if (session.isTextInputActive()) {
            // The phone's own keyboard covers this half of the screen while a
            // game is asking for text, so the pads are not drawn at all: what
            // goes here is the band the keyboard leaves behind.
            keyboardNotice(ui, Ui.PAD, y + softHeight + 14, frame.width() - Ui.PAD * 2);
            return;
        }

        int padTop = y + softHeight + 14;
        numericPad(ui, Ui.PAD + 4, padTop);
        directionalPad(ui, frame.width() - Ui.PAD - 208, padTop + 16);
    }

    /**
     * What the keypad area says while the phone's keyboard is up.
     *
     * <p>No mock keyboard is drawn here: the real one belongs to the phone,
     * and drawing a picture of one would be inventing an interface the app
     * does not own.</p>
     */
    private void keyboardNotice(Ui ui, int x, int y, int width) {
        Framebuffer frame = ui.frame();
        int height = ui.medium().height() + 34;
        ui.panel(x, y, width, height, Theme.ACCENT_DIM, Theme.ACCENT);
        int glyph = ui.medium().height() + 6;
        int textWidth = ui.medium().stringWidth("Bàn phím máy đang mở — gõ trực tiếp");
        int left = x + (width - glyph - 10 - textWidth) / 2;
        Icons.draw(frame, Icons.KEYBOARD, left, y + (height - glyph) / 2, glyph, Theme.ACCENT);
        ui.text(ui.medium(), "Bàn phím máy đang mở — gõ trực tiếp", left + glyph + 10,
                y + (height - ui.medium().height()) / 2, Theme.ACCENT);
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

    /**
     * The 3x4 grid, in the order a handset lays it out.
     *
     * <p>Digits only. The letters printed under them were there because
     * multi-tap was the only way that keypad could enter a name; the phone
     * running this has a keyboard, and it comes up on its own when a game
     * asks for text — so the hints were three rows of instructions for
     * something nobody has to do any more.</p>
     */
    private void numericPad(Ui ui, int x, int y) {
        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        int keyWidth = 62;
        int keyHeight = 44;
        int gap = 6;
        for (int i = 0; i < labels.length; i++) {
            int column = i % 3;
            int row = i / 3;
            int keyX = x + column * (keyWidth + gap);
            int keyY = y + row * (keyHeight + gap);
            ui.panel(keyX, keyY, keyWidth, keyHeight, Theme.SURFACE_ALT, Theme.BORDER);
            ui.textCenter(ui.large(), labels[i], keyX + keyWidth / 2,
                    keyY + (keyHeight - ui.large().height()) / 2, Theme.TEXT);
        }
    }

    /**
     * The directional pad: eight ways, with fire in the middle.
     *
     * <p>The corners are not keys of their own. MIDP has no diagonal key code
     * and no handset had a diagonal key — a corner of the pad was two
     * directions held at once, which is exactly what these send.</p>
     */
    private void directionalPad(Ui ui, int x, int y) {
        int keyWidth = 66;
        int keyHeight = 54;
        int gap = 5;
        int centreX = x + keyWidth + gap;
        int rightX = centreX + keyWidth + gap;
        int middleY = y + keyHeight + gap;
        int bottomY = middleY + keyHeight + gap;

        arrowKey(ui, centreX, y, keyWidth, keyHeight, 0);
        arrowKey(ui, x, middleY, keyWidth, keyHeight, 2);
        arrowKey(ui, rightX, middleY, keyWidth, keyHeight, 3);
        arrowKey(ui, centreX, bottomY, keyWidth, keyHeight, 1);

        arrowKey(ui, x, y, keyWidth, keyHeight, 4);
        arrowKey(ui, rightX, y, keyWidth, keyHeight, 5);
        arrowKey(ui, x, bottomY, keyWidth, keyHeight, 6);
        arrowKey(ui, rightX, bottomY, keyWidth, keyHeight, 7);

        ui.panel(centreX, middleY, keyWidth, keyHeight, Theme.ACCENT_DIM, Theme.ACCENT);
        ui.textCenter(ui.mediumBold(), "OK", centreX + keyWidth / 2,
                middleY + (keyHeight - ui.medium().height()) / 2, Theme.ACCENT);
    }

    /** Arrows in the order the pad draws them, corners last. */
    private static final String[] ARROWS = {
            Icons.UP, Icons.DOWN, Icons.LEFT, Icons.RIGHT,
            Icons.UP_LEFT, Icons.UP_RIGHT, Icons.DOWN_LEFT, Icons.DOWN_RIGHT,
    };

    /**
     * Directional key. The arrow is the Material chevron the Android keypad
     * puts on the same key, so the two keypads cannot drift apart.
     */
    private void arrowKey(Ui ui, int x, int y, int w, int h, int direction) {
        ui.panel(x, y, w, h, Theme.KEY, Theme.ACCENT);
        // The corners are quieter than the four main directions: they are
        // there when a game needs them, not competing for the thumb.
        boolean corner = direction >= 4;
        Icons.drawCentred(ui.frame(), ARROWS[direction], x + w / 2, y + h / 2,
                corner ? 30 : 40, Theme.ACCENT);
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
