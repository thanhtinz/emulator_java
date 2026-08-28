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
    /** Down each side of the keypad, and between the two pads. */
    private static final int MARGIN = 12;

    /**
     * How tall a pad key is, given the size of a numeric key.
     *
     * <p>The pad has three rows against the grid's four, so at one key size
     * it ends up markedly shorter than the numbers beside it and reads as the
     * lesser of the two. It is the other way round: the pad is what a game is
     * played with, and the numbers are mostly for typing a name. So the pad
     * keeps the same width — both pads still fit across the screen at J2ME
     * Loader's key size — and gains the height, three of its rows coming to
     * exactly four of theirs.</p>
     */
    private static int padKeyHeight(int key) {
        return (key * 4 + GAP) / 3;
    }

    private final String fixtureDir;
    private EmulatorSession session;

    private final String midletClass;
    private final int gameWidth;
    private final int gameHeight;
    private int keypadLayout = com.mobicore.core.model.GameProfile.KEYPAD_FULL;
    private int keypadShape = com.mobicore.core.model.GameProfile.KEY_SHAPE_ROUNDED;
    private int keypadOpacity = 100;
    private boolean menuOpen;
    private boolean arrangeMode;

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

    /** Which keys the keypad shows; see {@code GameProfile.KEYPAD_*}. */
    public EmulatorScreen withKeypad(int layout) {
        this.keypadLayout = layout;
        return this;
    }

    /**
     * How the keys are drawn: their shape, and how solid they are.
     *
     * @param shape one of {@code GameProfile.KEY_SHAPE_*}
     * @param opacity how solid, in percent
     */
    public EmulatorScreen withKeyLook(int shape, int opacity) {
        this.keypadShape = shape;
        this.keypadOpacity = opacity;
        return this;
    }

    /**
     * Draws the keypad the way the arranging screen shows it: keys where the
     * player has dragged them, over a dimmed game, with the hint and the way
     * back at the top.
     */
    public EmulatorScreen arranging() {
        this.arrangeMode = true;
        return this;
    }

    /** Draws the in-game menu over the screen, as if "Menu" was tapped. */
    public EmulatorScreen withMenu() {
        this.menuOpen = true;
        return this;
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

    /**
     * How big one key is drawn, once the player's own size is applied.
     *
     * <p>The screen has the last word. A key size that would push the two
     * pads off the sides is not honoured as asked — the keypad would be
     * unusable and there would be no way to see that it was the size setting
     * that did it — so it is held to what fits.</p>
     */
    private int keyWidth(Framebuffer frame) {
        int standard = (int) (Math.min(frame.width(), frame.height()) / KEY_DIVISOR_UPRIGHT);
        int fits = (frame.width() - MARGIN * 3 - GAP * 4) / 6;
        int asked = arrangement().sizeOf(standard);
        return asked > fits ? fits : asked;
    }

    public Framebuffer render() throws Exception {
        if (session == null) {
            boot();
        }
        Framebuffer frame = Preview.newScreen();
        Ui ui = new Ui(frame);
        ui.background(Theme.BG);

        int barHeight = ui.medium().height() + 22;
        int key = keyWidth(frame);
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

        if (arrangeMode) {
            // The game is dimmed rather than hidden: the keys are being put
            // where the game needs them, so the game has to stay in sight.
            frame.setColor(0x99000000);
            frame.fillRect(0, barHeight, frame.width(), top - barHeight);
        }
        topBar(ui, barHeight, frame.width());
        controls(ui, top, controlHeight, key);
        if (arrangeMode) {
            arrangeChrome(ui, barHeight, top, key);
        }
        if (menuOpen) {
            gameMenu(ui, barHeight);
        }
        return frame;
    }

    /**
     * The in-game menu, which J2ME Loader puts behind the toolbar's overflow
     * and which this app had nowhere at all.
     *
     * <p>Its items are the ones a player reaches for mid-game and cannot
     * reach from a settings page they would have to quit to: a screenshot,
     * how much of the keypad is in the way, which way the phone is held, and
     * the way out that saves first.</p>
     */
    private void gameMenu(Ui ui, int barHeight) {
        Framebuffer frame = ui.frame();
        // The screen behind is dimmed rather than covered: the game is still
        // there, and this is a menu over it, not another screen.
        frame.setColor(0xB0000000);
        frame.fillRect(0, 0, frame.width(), frame.height());

        String[][] items = {
                {Icons.CAMERA, "Chụp màn hình", ""},
                {Icons.RECORD, "Quay màn hình", "tối đa 10 giây"},
                {Icons.TUNE, "Bàn phím", "Đầy đủ"},
                {Icons.ROTATE, "Màn hình", "Dọc"},
                {Icons.UNDO, "Tua lại 1 giây", "12s"},
                {Icons.SPEED, "Tốc độ", "1×"},
                {Icons.SAVE, "Lưu vào ô…", "2 ô đã dùng"},
                {Icons.EXIT, "Thoát", ""},
        };
        int width = 320;
        int rowHeight = ui.medium().height() + 26;
        int height = rowHeight * items.length + 16;
        int x = frame.width() - width - Ui.PAD;
        int y = barHeight + 6;
        ui.panel(x, y, width, height, Theme.SURFACE, Theme.BORDER);

        int glyph = ui.medium().height() + 4;
        for (int i = 0; i < items.length; i++) {
            int rowY = y + 8 + i * rowHeight;
            Icons.draw(frame, items[i][0], x + 16, rowY + (rowHeight - glyph) / 2, glyph,
                    Theme.ACCENT);
            ui.text(ui.medium(), items[i][1], x + 16 + glyph + 14,
                    rowY + (rowHeight - ui.medium().height()) / 2, Theme.TEXT);
            if (items[i][2].length() > 0) {
                ui.textRight(ui.small(), items[i][2], x + width - 16,
                        rowY + (rowHeight - ui.small().height()) / 2, Theme.TEXT_DIM);
            }
        }
    }

    /**
     * What the arranging screen adds over the keypad: what to do, how big the
     * keys are, and the way to put them all back.
     *
     * <p>The keys themselves are the control. There is no list of coordinates
     * to type, because nobody knows where a key should be until their thumb
     * is on it.</p>
     */
    private void arrangeChrome(Ui ui, int barHeight, int top, int key) {
        Framebuffer frame = ui.frame();
        int panelHeight = ui.medium().height() + ui.small().height() + 34;
        // Above the game rather than over the keypad: the keypad is what is
        // being worked on, and a panel across it would cover the keys the
        // thumb is reaching for.
        int y = barHeight + 12;
        ui.panel(Ui.PAD, y, frame.width() - Ui.PAD * 2, panelHeight,
                Theme.SURFACE, Theme.BORDER);
        ui.text(ui.mediumBold(), "Kéo phím tới chỗ vừa tay", Ui.PAD + 14, y + 12, Theme.TEXT);
        ui.text(ui.small(), "Cỡ phím " + arrangement().scale() + "%  ·  "
                        + (arrangement().isCustom() ? "đã sửa" : "như mặc định"),
                Ui.PAD + 14, y + 12 + ui.medium().height() + 6, Theme.TEXT_DIM);
        String reset = "Đặt lại";
        int resetWidth = ui.small().stringWidth(reset) + 28;
        ui.chip(reset, frame.width() - Ui.PAD - 14 - resetWidth,
                y + (panelHeight - ui.chipHeight()) / 2, Theme.ACCENT, Theme.ACCENT_DIM);
    }

    /**
     * A ring around a key that has been moved.
     *
     * <p>Only while arranging: mid-game it would be an outline round half the
     * keypad for no reason. While arranging it answers the one question the
     * screen raises — which of these have I already moved?</p>
     */
    private void markMoved(Ui ui, String button, int x, int y, int w, int h) {
        if (!arrangeMode || !arrangement().isMoved(button)) {
            return;
        }
        Framebuffer frame = ui.frame();
        frame.setColor(Theme.ACCENT);
        frame.drawRoundRect(x - 4, y - 4, w + 7, h + 7, 22, 22);
    }

    /** Softkey bar, then whichever pads the layout shows. */
    private int controlHeight(Ui ui, int key) {
        int soft = session.showsSoftKeyBar() ? 0 : (int) (key * SOFT_SCALE_Y) + 12;
        int pads = 0;
        if (showsNumbers()) {
            pads = key * 4 + GAP * 3;
        } else if (showsArrows()) {
            pads = padKeyHeight(key) * 3 + GAP * 2;
        }
        return 12 + soft + pads + 12;
    }

    private boolean showsArrows() {
        return keypadLayout == com.mobicore.core.model.GameProfile.KEYPAD_FULL
                || keypadLayout == com.mobicore.core.model.GameProfile.KEYPAD_ARROWS;
    }

    private boolean showsNumbers() {
        return keypadLayout == com.mobicore.core.model.GameProfile.KEYPAD_FULL
                || keypadLayout == com.mobicore.core.model.GameProfile.KEYPAD_NUMBERS;
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
        int key = arrangement().sizeOf(
                Math.max(frame.width(), frame.height()) / KEY_DIVISOR_TURNED);
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

        // Sideways the keys go onto their own sheet too, so the fade works
        // the same way whichever way the phone is held.
        Ui pen = keyLayer(ui);
        int softHeight = session.showsSoftKeyBar() ? 0 : (int) (key * SOFT_SCALE_Y);
        int dpadX = (side - padWidth) / 2;
        int numX = frame.width() - side + (side - padWidth) / 2;
        // With the command bar on screen there is no softkey under the pads,
        // so they sit in the middle of their column instead of hanging from
        // the top of it.
        int stack = key * 4 + GAP * 3 + (softHeight == 0 ? 0 : softHeight + 14);
        int padY = barHeight + Math.max(12, (frame.height() - barHeight - stack) / 2);
        directionalPad(pen, dpadX, padY, key, padKeyHeight(key));
        numericPad(pen, numX, padY, key);

        // The softkeys stay at the bottom outside corners: they are the two
        // keys the game labels, and both thumbs rest there when the phone is
        // held sideways.
        if (!session.showsSoftKeyBar()) {
            int softWidth = (int) (key * SOFT_SCALE_X);
            int softY = frame.height() - softHeight - 14;
            softKey(pen, key, (side - softWidth) / 2, softY, softWidth, softHeight,
                    session.leftSoftKeyLabel(), "L");
            softKey(pen, key, frame.width() - side + (side - softWidth) / 2, softY, softWidth,
                    softHeight, session.rightSoftKeyLabel(), "R");
        }
        blitKeyLayer(ui, pen);
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
        // Giữa thanh này để trống có chủ ý. Cỡ màn hình và số hình mỗi giây là
        // con số của người viết máy ảo, không phải của người đang chơi — và
        // một con số nhảy liên tục ngay trên đầu màn game thì kéo mắt đi khỏi
        // đúng thứ người ta đang nhìn.
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

        // The keys go onto a sheet of their own so the whole keypad can be
        // faded in one move. Fading each colour as it is drawn does not work:
        // a rounded outline is drawn as hundreds of overlapping points, and
        // hundreds of translucent points on one pixel come out opaque.
        Ui pen = keyLayer(ui);
        controlKeys(pen, top, key);
        blitKeyLayer(ui, pen);
    }

    /** The keys themselves, drawn wherever {@code pen} points. */
    private void controlKeys(Ui ui, int top, int key) {
        Framebuffer frame = ui.frame();
        int y = top + 12;
        boolean both = showsArrows() && showsNumbers();
        int padWidth = key * 3 + GAP * 2;
        int margin = both ? (frame.width() - padWidth * 2) / 3 : (frame.width() - padWidth) / 2;
        int softWidth = (int) (key * SOFT_SCALE_X);
        int softHeight = (int) (key * SOFT_SCALE_Y);
        // While the screen carries the command bar, that bar is the softkeys:
        // it is drawn with the game's own labels and a tap on it runs the
        // command. Repeating the same two words on two more keys underneath
        // is two ways to do one thing. A game that goes full screen takes the
        // bar away, and then these keys are the only way to reach a command.
        if (!session.showsSoftKeyBar()) {
            softKey(ui, key, margin, y, softWidth, softHeight,
                    session.leftSoftKeyLabel(), "L");
            softKey(ui, key, frame.width() - margin - softWidth, y, softWidth, softHeight,
                    session.rightSoftKeyLabel(), "R");
            y += softHeight + 12;
        }

        if (session.isTextInputActive()) {
            // The phone's own keyboard covers this half of the screen while a
            // game is asking for text, so the pads are not drawn at all: what
            // goes here is the band the keyboard leaves behind.
            keyboardNotice(ui, Ui.PAD, y, frame.width() - Ui.PAD * 2);
            return;
        }

        int padTop = y;
        if (showsNumbers()) {
            numericPad(ui, margin, padTop, key);
        }
        if (showsArrows()) {
            // Alone, a pad takes the middle: there is no reason to leave a
            // hole where the other half of the keypad used to be.
            int x = both ? frame.width() - margin - padWidth : margin;
            directionalPad(ui, x, padTop, key, padKeyHeight(key));
        }
    }

    /**
     * Where the keys should be drawn: straight onto the screen at full
     * strength, or onto a clear sheet when they are to be faded.
     */
    private Ui keyLayer(Ui ui) {
        if (keypadOpacity >= 100) {
            return ui;
        }
        Framebuffer layer = new Framebuffer(ui.frame().width(), ui.frame().height());
        layer.fill(0x00000000);
        return new Ui(layer);
    }

    /** Puts the sheet of keys down over the screen at the chosen strength. */
    private void blitKeyLayer(Ui ui, Ui pen) {
        if (pen == ui) {
            return;
        }
        int[] pixels = pen.frame().pixels();
        for (int i = 0; i < pixels.length; i++) {
            int alpha = (pixels[i] >>> 24) * keypadOpacity / 100;
            pixels[i] = (alpha << 24) | (pixels[i] & 0x00FFFFFF);
        }
        ui.frame().drawFramebuffer(pen.frame(), 0, 0);
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
    private void softKey(Ui ui, int key, int x, int y, int width, int height,
                         String label, String mark) {
        // Offsets are in numeric keys everywhere, including for these two:
        // one unit has to mean the same distance whichever key was dragged.
        String button = "L".equals(mark) ? "softLeft" : "softRight";
        x += shiftX(button, key);
        y += shiftY(button, key);
        boolean bound = label != null && label.length() > 0;
        keyPanel(ui, x, y, width, height, bound ? Theme.SURFACE_ALT : Theme.BG, Theme.BORDER);
        markMoved(ui, button, x, y, width, height);
        int textY = y + (height - ui.mediumBold().height()) / 2;
        if (!bound) {
            // Nothing to label it with, so the key is called what it is:
            // "L" in the middle rather than an "L" in the corner beside a
            // dash standing in for a command that is not there.
            ui.textCenter(ui.mediumBold(), mark, x + width / 2, textY, Theme.ACCENT);
            return;
        }
        ui.textCenter(ui.mediumBold(), ui.ellipsize(ui.mediumBold(), label, width - 26),
                x + width / 2, textY, Theme.TEXT);
        ui.text(ui.small(), mark, x + 10, y + (height - ui.small().height()) / 2,
                Theme.ACCENT);
    }

    // ------------------------------------------------------- how a key looks

    /**
     * One key's body, in the shape and at the strength the profile asks for.
     *
     * <p>Every key on the keypad goes through here — numbers, arrows, fire
     * and the two softkeys — so a change of shape cannot reach some of them
     * and miss the rest.</p>
     */
    /**
     * Where a key ends up once the player has dragged it.
     *
     * <p>Offsets are in keys, so the same arrangement holds upright, sideways
     * and on a screen of any size.</p>
     */
    private int shiftX(String button, int key) {
        return Math.round(arrangement().offsetX(button) * key);
    }

    private int shiftY(String button, int key) {
        return Math.round(arrangement().offsetY(button) * key);
    }

    private com.mobicore.core.model.KeypadArrangement arrangement() {
        return session.profile().keypadArrangement();
    }

    private void keyPanel(Ui ui, int x, int y, int w, int h, int fill, int border) {
        Framebuffer frame = ui.frame();
        int shape = keypadShape;
        if (shape == com.mobicore.core.model.GameProfile.KEY_SHAPE_RECT) {
            frame.setColor(fill);
            frame.fillRect(x, y, w, h);
            frame.setColor(border);
            frame.drawRect(x, y, w - 1, h - 1);
            return;
        }
        // Round keys are the same call with the corner radius run all the way
        // out: on a square key that is a circle, on the wide softkeys a pill.
        int arc = shape == com.mobicore.core.model.GameProfile.KEY_SHAPE_ROUND
                ? Math.min(w, h) : 18;
        frame.setColor(fill);
        frame.fillRoundRect(x, y, w, h, arc, arc);
        frame.setColor(border);
        frame.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
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
        String[] buttons = {"num1", "num2", "num3", "num4", "num5", "num6",
                "num7", "num8", "num9", "star", "num0", "hash"};
        for (int i = 0; i < labels.length; i++) {
            int keyX = x + (i % 3) * (key + GAP) + shiftX(buttons[i], key);
            int keyY = y + (i / 3) * (key + GAP) + shiftY(buttons[i], key);
            keyPanel(ui, keyX, keyY, key, key, Theme.SURFACE_ALT, Theme.BORDER);
            markMoved(ui, buttons[i], keyX, keyY, key, key);
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
    private void directionalPad(Ui ui, int x, int y, int key, int tall) {
        int centreX = x + key + GAP;
        int rightX = centreX + key + GAP;
        int middleY = y + tall + GAP;
        int bottomY = middleY + tall + GAP;

        arrowKey(ui, centreX, y, key, tall, 0);
        arrowKey(ui, x, middleY, key, tall, 2);
        arrowKey(ui, rightX, middleY, key, tall, 3);
        arrowKey(ui, centreX, bottomY, key, tall, 1);

        arrowKey(ui, x, y, key, tall, 4);
        arrowKey(ui, rightX, y, key, tall, 5);
        arrowKey(ui, x, bottomY, key, tall, 6);
        arrowKey(ui, rightX, bottomY, key, tall, 7);
        int fireX = centreX + shiftX("fire", key);
        int fireY = middleY + shiftY("fire", key);

        // "F" is what J2ME Loader writes on the fire key, and fire is what
        // MIDP calls it; the pad's middle key has never been an "OK" button.
        keyPanel(ui, fireX, fireY, key, tall, Theme.ACCENT_DIM, Theme.ACCENT);
        markMoved(ui, "fire", fireX, fireY, key, tall);
        ui.textCenter(ui.largeBold(), "F", fireX + key / 2,
                fireY + (tall - ui.largeBold().height()) / 2, Theme.ACCENT);
    }

    /** The button each arrow sends, in the order the pad draws them. */
    private static final String[] ARROW_BUTTONS = {
            "up", "down", "left", "right",
            "upLeft", "upRight", "downLeft", "downRight",
    };

    /** Arrows in the order the pad draws them, corners last. */
    private static final String[] ARROWS = {
            Icons.UP, Icons.DOWN, Icons.LEFT, Icons.RIGHT,
            Icons.UP_LEFT, Icons.UP_RIGHT, Icons.DOWN_LEFT, Icons.DOWN_RIGHT,
    };

    /**
     * Directional key. The arrow is the Material chevron the Android keypad
     * puts on the same key, so the two keypads cannot drift apart.
     */
    private void arrowKey(Ui ui, int x, int y, int key, int tall, int direction) {
        x += shiftX(ARROW_BUTTONS[direction], key);
        y += shiftY(ARROW_BUTTONS[direction], key);
        keyPanel(ui, x, y, key, tall, Theme.KEY, Theme.ACCENT);
        markMoved(ui, ARROW_BUTTONS[direction], x, y, key, tall);
        // The corners are quieter than the four main directions: they are
        // there when a game needs them, not competing for the thumb.
        boolean corner = direction >= 4;
        Icons.drawCentred(ui.frame(), ARROWS[direction], x + key / 2, y + tall / 2,
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
