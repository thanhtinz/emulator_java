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
    /**
     * Key metrics taken from J2ME Loader's on-screen keypad, which is the one
     * every player of these games already has their thumbs trained on.
     *
     * <p>Its keys are square and sized off the screen rather than off a
     * designer's guess: {@code keySize = min(width, height) / 6.5} upright,
     * {@code max(width, height) / 12} when the phone is turned. The two
     * softkeys are the one exception — {@code PHONE_KEY_SCALE_X = 2.0f},
     * {@code PHONE_KEY_SCALE_Y = 0.75f} — so they read as a wide, shallow bar
     * rather than as two more keys in the grid.</p>
     */
    private static final float KEY_DIVISOR_UPRIGHT = 6.5f;
    private static final int KEY_DIVISOR_TURNED = 12;
    private static final float SOFT_SCALE_X = 2.0f;
    private static final float SOFT_SCALE_Y = 0.75f;
    /** A hair of daylight between keys; J2ME Loader snaps its keys together. */
    private static final int GAP = 4;

    private final String fixtureDir;
    private EmulatorSession session;

    private final String midletClass;
    private final int gameWidth;
    private final int gameHeight;

    public EmulatorScreen(String fixtureDir) {
        this(fixtureDir, null);
    }

    /** @param midletClass which MIDlet to boot, or null for the default one */
    public EmulatorScreen(String fixtureDir, String midletClass) {
        this(fixtureDir, midletClass, GAME_WIDTH, GAME_HEIGHT);
    }

    private EmulatorScreen(String fixtureDir, String midletClass, int gameWidth, int gameHeight) {
        this.fixtureDir = fixtureDir;
        this.midletClass = midletClass;
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
    }

    /**
     * The same emulator with a game written for a wide screen, which is the
     * case the landscape layout exists for.
     */
    public static EmulatorScreen landscape(String fixtureDir) {
        return new EmulatorScreen(fixtureDir, null, GAME_HEIGHT, GAME_WIDTH);
    }

    public EmulatorSession session() {
        return session;
    }

    /** Boots the suite and advances it far enough to be worth looking at. */
    public EmulatorSession boot() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        session = EmulatorSession.create(suite, gameWidth, gameHeight, new FixedClock());
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
        int key = (int) (Math.min(frame.width(), frame.height()) / KEY_DIVISOR_UPRIGHT);
        // The keypad is sized first and the game takes what is left: the keys
        // are what a thumb has to hit, and a key that misses is worse than a
        // game drawn a few pixels smaller.
        int controlHeight = controlHeight(ui, key);
        int top = frame.height() - controlHeight;

        int shownWidth = frame.width();
        int shownHeight = shownWidth * gameHeight / gameWidth;
        if (shownHeight > top - barHeight) {
            shownHeight = top - barHeight;
            shownWidth = shownHeight * gameWidth / gameHeight;
        }
        drawGame(frame, ui, (frame.width() - shownWidth) / 2, barHeight, shownWidth, shownHeight);

        topBar(ui, barHeight, frame.width());
        controls(ui, top, controlHeight, key);
        return frame;
    }

    /** Softkey bar, then the two pads, at J2ME Loader's proportions. */
    private int controlHeight(Ui ui, int key) {
        int soft = (int) (key * SOFT_SCALE_Y);
        return 12 + soft + 14 + key * 4 + GAP * 3 + 12;
    }

    /**
     * The same session with the phone turned.
     *
     * <p>The game keeps the middle, because that is what the player looks at,
     * and the keypad splits either side of it: with the phone held sideways
     * both thumbs are already at the edges, and a pad stacked under a wide
     * screen would leave the game a strip along the top.</p>
     */
    public Framebuffer renderLandscape() throws Exception {
        if (session == null) {
            boot();
        }
        Framebuffer frame = new Framebuffer(Preview.SCREEN_HEIGHT, Preview.SCREEN_WIDTH);
        frame.setAntialias(true);
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);

        int barHeight = ui.medium().height() + 18;
        topBar(ui, barHeight, frame.width());

        // Turned, J2ME Loader sizes its keys off the long edge instead. Its
        // keypad floats over the game, though, and this one has a column to
        // itself, so the size is also held to what that column can hold.
        int key = Math.max(frame.width(), frame.height()) / KEY_DIVISOR_TURNED;
        int room = (int) ((frame.height() - barHeight - 52 - GAP * 3) / (4 + SOFT_SCALE_Y));
        key = Math.min(key, room);
        int padWidth = key * 3 + GAP * 2;

        // Each hand gets a column wide enough for a pad, and the game takes
        // everything left in the middle.
        int side = padWidth + 34;
        int middle = frame.width() - side * 2;
        frame.setColor(Theme.SURFACE);
        frame.fillRect(0, barHeight, side, frame.height() - barHeight);
        frame.fillRect(frame.width() - side, barHeight, side, frame.height() - barHeight);

        int shownWidth = middle - 16;
        int shownHeight = shownWidth * gameHeight / gameWidth;
        int available = frame.height() - barHeight - 12;
        if (shownHeight > available) {
            shownHeight = available;
            shownWidth = shownHeight * gameWidth / gameHeight;
        }
        drawGame(frame, ui, side + (middle - shownWidth) / 2,
                barHeight + (frame.height() - barHeight - shownHeight) / 2,
                shownWidth, shownHeight);

        int softHeight = (int) (key * SOFT_SCALE_Y);
        int dpadX = (side - padWidth) / 2;
        int numX = frame.width() - side + (side - padWidth) / 2;
        int padY = barHeight + 12;
        directionalPad(ui, dpadX, padY + (key + GAP) / 2, key);
        numericPad(ui, numX, padY, key);

        // The softkeys stay at the bottom outside corners: they are the two
        // keys the game labels, and both thumbs rest there when the phone is
        // held sideways.
        int softWidth = (int) (key * SOFT_SCALE_X);
        int softY = frame.height() - softHeight - 14;
        softKey(ui, (side - softWidth) / 2, softY, softWidth, softHeight,
                session.leftSoftKeyLabel(), "L");
        softKey(ui, frame.width() - side + (side - softWidth) / 2, softY, softWidth, softHeight,
                session.rightSoftKeyLabel(), "R");
        return frame;
    }

    /** Blits the emulated screen, filtered or not as the profile asks. */
    private void drawGame(Framebuffer frame, Ui ui, int x, int y, int width, int height) {
        Framebuffer scaled = session.profile().smoothing()
                ? session.screen().scaleSmooth(width, height)
                : session.screen().scaleNearest(width, height);
        frame.setBlendMode(Framebuffer.BLEND_REPLACE);
        frame.drawFramebuffer(scaled, x, y);
        frame.setBlendMode(Framebuffer.BLEND_SRC_OVER);
    }

    /**
     * The emulator's own bar, kept clearly distinct from the handset chrome
     * below it. It deliberately does not repeat the game's title or offer a
     * second "pause": the MIDlet has its own title bar and its own softkeys,
     * and two sets of the same word is how a player ends up pressing the wrong
     * one.
     */
    private void topBar(Ui ui, int barHeight, int width) {
        Framebuffer frame = ui.frame();
        frame.setColor(Theme.SURFACE);
        frame.fillRect(0, 0, width, barHeight);
        int textY = (barHeight - ui.medium().height()) / 2;
        int glyph = ui.medium().height() + 4;
        Icons.draw(frame, Icons.BACK, Ui.PAD, (barHeight - glyph) / 2, glyph, Theme.ACCENT);
        ui.text(ui.medium(), "Thư viện", Ui.PAD + glyph + 4, textY, Theme.ACCENT);
        ui.textCenter(ui.small(), gameWidth + "×" + gameHeight + "  ·  30 hình/giây",
                width / 2, (barHeight - ui.small().height()) / 2, Theme.TEXT_DIM);
        ui.textRight(ui.medium(), "Menu", width - Ui.PAD, textY, Theme.ACCENT);
    }

    /**
     * The keypad, cut down to the keys a game actually reads: the two softkeys
     * directly under the screen, so they line up with the labels the system
     * draws there, the numbers and the directional pad. The call, end and clear
     * keys a handset carried are gone — they were there because the device was
     * a phone, and on screen they only crowded the keys that matter.
     */
    private void controls(Ui ui, int top, int height, int key) {
        Framebuffer frame = ui.frame();
        frame.setColor(Theme.SURFACE);
        frame.fillRect(0, top, frame.width(), height);
        frame.setColor(Theme.BORDER);
        frame.fillRect(0, top, frame.width(), 1);

        int y = top + 12;
        int padWidth = key * 3 + GAP * 2;
        int margin = (frame.width() - padWidth * 2) / 3;
        int softWidth = (int) (key * SOFT_SCALE_X);
        int softHeight = (int) (key * SOFT_SCALE_Y);
        // Each softkey sits over the pad its thumb is on, at the width J2ME
        // Loader gives them: two keys across, three quarters of a key tall.
        softKey(ui, margin + (padWidth - softWidth) / 2, y, softWidth, softHeight,
                session.leftSoftKeyLabel(), "L");
        softKey(ui, frame.width() - margin - padWidth + (padWidth - softWidth) / 2, y,
                softWidth, softHeight, session.rightSoftKeyLabel(), "R");

        if (session.isTextInputActive()) {
            // The phone's own keyboard covers this half of the screen while a
            // game is asking for text, so the pads are not drawn at all: what
            // goes here is the band the keyboard leaves behind.
            keyboardNotice(ui, Ui.PAD, y + softHeight + 14, frame.width() - Ui.PAD * 2);
            return;
        }

        int padTop = y + softHeight + 14;
        numericPad(ui, margin, padTop, key);
        directionalPad(ui, frame.width() - margin - padWidth, padTop + (key + GAP) / 2, key);
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
     *
     * <p>The corner carries an L or an R. These two are the keys J2ME calls
     * the left and right softkey, and every emulator of the era named them
     * that way; the text in the middle belongs to the game and changes with
     * the screen, so a player told to "press R" still needs to see which key
     * that is while it says "Thoát".</p>
     */
    private void softKey(Ui ui, int x, int y, int width, int height, String label, String mark) {
        boolean bound = label != null && label.length() > 0;
        ui.panel(x, y, width, height, bound ? Theme.SURFACE_ALT : Theme.BG, Theme.BORDER);
        String text = ui.ellipsize(ui.mediumBold(), bound ? label : "—", width - 26);
        int textY = y + (height - ui.mediumBold().height()) / 2;
        ui.textCenter(ui.mediumBold(), text, x + width / 2, textY,
                bound ? Theme.TEXT : Theme.TEXT_DIM);
        ui.text(ui.small(), mark, x + 10, y + (height - ui.small().height()) / 2, Theme.ACCENT);
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
    private void numericPad(Ui ui, int x, int y, int key) {
        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        for (int i = 0; i < labels.length; i++) {
            int keyX = x + (i % 3) * (key + GAP);
            int keyY = y + (i / 3) * (key + GAP);
            ui.panel(keyX, keyY, key, key, Theme.SURFACE_ALT, Theme.BORDER);
            ui.textCenter(ui.large(), labels[i], keyX + key / 2,
                    keyY + (key - ui.large().height()) / 2, Theme.TEXT);
        }
    }

    /**
     * The directional pad: eight ways, with fire in the middle.
     *
     * <p>The corners are not keys of their own. MIDP has no diagonal key code
     * and no handset had a diagonal key — a corner of the pad was two
     * directions held at once, which is exactly what these send.</p>
     */
    private void directionalPad(Ui ui, int x, int y, int key) {
        int centreX = x + key + GAP;
        int rightX = centreX + key + GAP;
        int middleY = y + key + GAP;
        int bottomY = middleY + key + GAP;

        arrowKey(ui, centreX, y, key, 0);
        arrowKey(ui, x, middleY, key, 2);
        arrowKey(ui, rightX, middleY, key, 3);
        arrowKey(ui, centreX, bottomY, key, 1);

        arrowKey(ui, x, y, key, 4);
        arrowKey(ui, rightX, y, key, 5);
        arrowKey(ui, x, bottomY, key, 6);
        arrowKey(ui, rightX, bottomY, key, 7);

        // "F" is what J2ME Loader writes on the fire key, and fire is what
        // MIDP calls it; the pad's middle key has never been an "OK" button.
        ui.panel(centreX, middleY, key, key, Theme.ACCENT_DIM, Theme.ACCENT);
        ui.textCenter(ui.largeBold(), "F", centreX + key / 2,
                middleY + (key - ui.largeBold().height()) / 2, Theme.ACCENT);
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
    private void arrowKey(Ui ui, int x, int y, int key, int direction) {
        ui.panel(x, y, key, key, Theme.KEY, Theme.ACCENT);
        // The corners are quieter than the four main directions: they are
        // there when a game needs them, not competing for the thumb.
        boolean corner = direction >= 4;
        Icons.drawCentred(ui.frame(), ARROWS[direction], x + key / 2, y + key / 2,
                corner ? key * 2 / 5 : key * 3 / 5, Theme.ACCENT);
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
