package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.midp.SystemChrome;
import com.mobicore.core.midp.MidpForms;
import com.mobicore.core.midp.ScreenRenderer;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmObject;
import com.mobicore.tools.SampleSuite;

import java.io.File;

/**
 * Drives the high level screens the way a player does: through the keypad.
 *
 * <p>These are the screens a MIDlet builds its menus from, and the emulator
 * draws them itself, so nothing here checks a game's pixels — it checks that
 * moving, selecting and typing reach the MIDlet as MIDP says they should.</p>
 */
public final class FormsTest extends Test {

    private final String fixtureDir;

    public FormsTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "High level LCDUI screens";
    }

    @Override
    public void run() throws Exception {
        if (!new File(fixtureDir, "demo/MenuDemo.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        EmulatorSession session = boot();
        Vm vm = session.vm();
        MidpContext context = session.context();

        VmObject menu = context.current();
        check(menu != null, "startApp put a screen up");
        check(ScreenRenderer.isHighLevel(vm, menu), "a List is a screen the emulator draws");
        eq("Sky Runner", context.title(), "switching to a screen brings its title with it");
        eq(5, MidpForms.choicesOf(menu).size(), "every row was appended");

        // The device draws it: a Canvas would have painted nothing here.
        check(session.renderFrame(), "the list is painted");
        Framebuffer screen = session.screen();
        check(!isBlank(screen), "the painted list is not an empty screen");

        listNavigation(session, context, menu);
        implicitSelect(session, context, vm);
        typing(session, context, vm);
        optionsMenu(session, context, vm);
        alerts(session, context, vm);
        diagonals(session);
        softKeysAreLandR(session);
        tappingTheCommandBar();
        turbo();
        systemKeyboard(session, context, vm);
    }

    private EmulatorSession boot() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, 240, 320, null);
        session.start("demo.MenuDemo");
        return session;
    }

    /** Up and down walk the rows, and the ends wrap as a handset's list does. */
    private void listNavigation(EmulatorSession session, MidpContext context, VmObject menu) {
        eq(0, MidpForms.intField(menu, "focus"), "the first row starts selected");
        session.keyPressed(MidpContext.KEY_DOWN);
        eq(1, MidpForms.intField(menu, "focus"), "down moves to the next row");
        session.keyPressed(MidpContext.KEY_UP);
        session.keyPressed(MidpContext.KEY_UP);
        eq(4, MidpForms.intField(menu, "focus"), "up from the first row wraps to the last");
        check(session.renderFrame(), "moving the selection asks for a repaint");
        // The keypad's 2 and 8 are up and down, so they walk the list too
        // rather than being read as row numbers.
        session.keyPressed('8');
        eq(0, MidpForms.intField(menu, "focus"), "8 is down, and down from the last row wraps");
        session.keyPressed('8');
        eq(1, MidpForms.intField(menu, "focus"), "the selection is on the options row");
    }

    /** Pressing the key on an implicit list runs the MIDlet's select command. */
    private void implicitSelect(EmulatorSession session, MidpContext context, Vm vm) {
        VmObject menu = context.current();
        session.keyPressed(MidpContext.KEY_FIRE);
        VmObject now = context.current();
        check(now != menu, "selecting row 2 moved to the options form");
        eq("Tuỳ chọn", context.title(), "the form's title is showing");
        check(now.type().isAssignableTo(vm.loadClass(MidpForms.FORM)), "the new screen is a Form");
        check(session.renderFrame(), "the form is painted");

        // Walking the form reaches the gauge, and left/right move it — which is
        // the only way an interactive Gauge can be driven from a keypad.
        session.keyPressed(MidpContext.KEY_DOWN);
        session.keyPressed(MidpContext.KEY_DOWN);
        eq(2, MidpForms.intField(now, "focus"), "the focus reached the gauge");
        VmObject gauge = MidpForms.itemsOf(now).get(2);
        eq(7, MidpForms.intField(gauge, "value"), "the gauge starts where the MIDlet set it");
        session.keyPressed(MidpContext.KEY_RIGHT);
        eq(8, MidpForms.intField(gauge, "value"), "right raises an interactive gauge");
        session.keyPressed(MidpContext.KEY_LEFT);
        session.keyPressed(MidpContext.KEY_LEFT);
        eq(6, MidpForms.intField(gauge, "value"), "left lowers it");

        // itemStateChanged is how a Form reports anything at all.
        VmObject status = MidpForms.itemsOf(now).get(0);
        eq("Âm lượng 6", vm.stringOf(status.get("text")),
                "the MIDlet was told the item changed");

        // The exclusive choice group below it selects as the focus moves.
        session.keyPressed(MidpContext.KEY_DOWN);
        eq(3, MidpForms.intField(now, "focus"), "the focus reached the choice group");
        VmObject choice = MidpForms.itemsOf(now).get(3);
        check(MidpForms.choicesOf(choice).selected(1), "the MIDlet's own selection stands");
        session.keyPressed(MidpContext.KEY_DOWN);
        session.keyPressed(MidpContext.KEY_FIRE);
        check(MidpForms.choicesOf(choice).selected(2), "pressing the key picks the row");
        eq("Khó", vm.stringOf(status.get("text")), "the choice was reported too");
    }

    /** Multi-tap: the only way a numeric keypad ever entered a name. */
    private void typing(EmulatorSession session, MidpContext context, Vm vm) {
        VmObject form = context.current();
        VmObject field = MidpForms.itemsOf(form).get(1);
        form.set("focus", Integer.valueOf(1));
        eq("Nam", MidpForms.textOf(field).toString(), "the field starts with the MIDlet's text");

        session.keyPressed('2');
        eq("Nama", MidpForms.textOf(field).toString(), "the first tap types the key's first letter");
        session.keyPressed('2');
        eq("Namb", MidpForms.textOf(field).toString(), "tapping again cycles rather than repeats");
        session.keyPressed(MidpContext.KEY_CLEAR);
        eq("Nam", MidpForms.textOf(field).toString(), "clear deletes the last character");

        // A numeric field takes the digit itself, not the letters printed on it.
        field.set("constraints", Integer.valueOf(MidpForms.NUMERIC));
        session.keyPressed('7');
        eq("Nam7", MidpForms.textOf(field).toString(), "a numeric field types the digit");
        field.set("constraints", Integer.valueOf(MidpForms.ANY));
    }

    /**
     * Four commands want the left key, so the last three go behind the menu.
     * Before it existed they were parsed, counted, and unreachable.
     */
    private void optionsMenu(EmulatorSession session, MidpContext context, Vm vm) {
        VmObject form = context.current();
        eq(5, context.commandsOf(form).size(), "every command the MIDlet added is registered");
        eq(3, context.menuCommands().size(), "three commands do not fit on the softkeys");
        check(!context.isMenuOpen(), "the menu starts closed");

        session.pressButton("softLeft");
        check(context.isMenuOpen(), "the left key opens the menu instead of running a command");
        check(session.renderFrame(), "the open menu is painted");

        session.keyPressed(MidpContext.KEY_DOWN);
        eq(1, context.menuIndex(), "the menu selection moves");
        session.keyPressed(MidpContext.KEY_UP);
        eq(0, context.menuIndex(), "and moves back");

        // The left key already carries "Lưu", so the menu starts at "Đặt lại",
        // which is the command that resets the gauge.
        VmObject gauge = MidpForms.itemsOf(form).get(2);
        session.keyPressed(MidpContext.KEY_FIRE);
        check(!context.isMenuOpen(), "picking a row closes the menu");
        eq(5, MidpForms.intField(gauge, "value"), "the command behind the menu really ran");

        // The right key backs out without running anything.
        session.pressButton("softLeft");
        check(context.isMenuOpen(), "the menu opens again");
        session.pressButton("softRight");
        check(!context.isMenuOpen(), "the right key closes the menu");
    }

    private void alerts(EmulatorSession session, MidpContext context, Vm vm) {
        // "Giới thiệu" is the last menu row and puts up an Alert.
        session.pressButton("softLeft");
        session.keyPressed(MidpContext.KEY_DOWN);
        session.keyPressed(MidpContext.KEY_DOWN);
        session.keyPressed(MidpContext.KEY_FIRE);
        VmObject alert = context.current();
        check(alert.type().isAssignableTo(vm.loadClass(MidpForms.ALERT)),
                "the help command put up an Alert");
        eq(MidpForms.FOREVER, MidpForms.intField(alert, "timeout"),
                "an alert with no timeout waits for the player");
        check(session.renderFrame(), "the alert is painted");
        check(!isBlank(session.screen()), "the painted alert is not an empty screen");

        // Its own Back command is on the right key, where a handset put it.
        eq("Quay lại", session.rightSoftKeyLabel(), "the alert's back command labels the right key");
        session.pressButton("softRight");
        check(context.current() != alert, "backing out of the alert leaves it");
    }

    /** True when every pixel is the same, which means nothing was drawn. */
    private static boolean isBlank(Framebuffer frame) {
        int[] pixels = frame.pixels();
        for (int i = 1; i < pixels.length; i++) {
            if (pixels[i] != pixels[0]) {
                return false;
            }
        }
        return true;
    }

    /**
     * The phone's own keyboard: the emulator has to say when a game wants
     * text, and take a whole string back.
     */
    private void systemKeyboard(EmulatorSession session, MidpContext context, Vm vm) {
        check(!session.isTextInputActive(), "a list is not asking for text");

        // "Nhập tên" is the third row, and opens a TextBox.
        session.keyPressed(MidpContext.KEY_DOWN);
        session.keyPressed(MidpContext.KEY_DOWN);
        session.keyPressed(MidpContext.KEY_FIRE);
        check(session.isTextInputActive(), "a TextBox is asking for text");
        eq("", session.textInput(), "and starts empty");

        check(session.setTextInput("Nguyễn"), "the keyboard's text reaches the game");
        eq("Nguyễn", session.textInput(), "marks and all — this is not multi-tap");
        eq("Nguyễn", MidpForms.textOf(context.current()).toString(),
                "and it is the game's own field that holds it");

        // The field's limit is the game's promise, not the keyboard's.
        session.setTextInput("012345678901234567890123456789");
        int max = MidpForms.intField(context.current(), "maxSize");
        eq(max, session.textInput().length(), "a longer string is cut to what the game allows");

        session.pressButton("softRight");
        check(!session.isTextInputActive(), "leaving the screen ends the text entry");
    }

    /**
     * The corners of the pad. MIDP has no diagonal key, so a corner is two
     * directions at once — which is what a game reading key states expects.
     */
    private void diagonals(EmulatorSession session) {
        int up = 1 << MidpContext.ACTION_UP;
        int right = 1 << MidpContext.ACTION_RIGHT;
        session.pressButton("upRight");
        int states = session.context().keyStates();
        check((states & up) != 0, "a corner presses up");
        check((states & right) != 0, "and right, at the same time");
        session.releaseButton("upRight");
        eq(0, session.context().keyStates() & (up | right),
                "and releasing it lets go of both");
        eq(null, com.mobicore.core.model.InputProfile.diagonalOf("up"),
                "a plain direction is not a corner");
    }

    /**
     * L and R are the two softkeys.
     *
     * <p>That is what a J2ME emulator's on-screen keypad has always called
     * them — {@code new VirtualKey(Canvas.KEY_SOFT_LEFT, "L")} — so the keys
     * marked L and R must run the game's own commands, not send some other
     * key code.</p>
     */
    /**
     * On a touchscreen the command bar is the button.
     *
     * <p>That is how these games were played on one: the labels are drawn
     * along the bottom of the screen and tapping a label runs its command.
     * The emulator has to deliver that tap to the command rather than pass it
     * down to the game as a stray touch.</p>
     */
    private void tappingTheCommandBar() throws Exception {
        EmulatorSession session = boot();
        MidpContext context = session.context();
        check(session.showsSoftKeyBar(), "a list screen carries the command bar");

        // Into "Nhập tên", whose right key is "Quay lại".
        session.keyPressed(MidpContext.KEY_DOWN);
        session.keyPressed(MidpContext.KEY_DOWN);
        session.keyPressed(MidpContext.KEY_FIRE);
        check(session.isTextInputActive(), "the TextBox is up");
        eq("Quay lại", session.rightSoftKeyLabel(), "with its own back command on the right key");

        int barTop = context.screen().height() - SystemChrome.softKeyBarHeight() + 2;
        session.pointerPressed(context.screen().width() - 6, barTop);
        check(!session.isTextInputActive(), "tapping that label ran the command behind it");

        // A tap on the game is still the game's.
        eq(SystemChrome.HIT_NONE, SystemChrome.softKeyHit(context,
                        context.screen().width() / 2, context.screen().height() / 2),
                "a tap in the middle of the screen is not a softkey");
        eq(SystemChrome.HIT_LEFT, SystemChrome.softKeyHit(context, 4, barTop),
                "the left half of the bar is the left key");
    }

    /**
     * Turbo: the emulator hammering a key on the player's behalf.
     *
     * <p>What has to be true is that the key genuinely goes up and down
     * again. A game reading {@code keyPressed} counts presses, so a key held
     * down forever is one press however long it is held, and turbo that only
     * kept it pressed would fire once and stop.</p>
     */
    private void turbo() throws Exception {
        EmulatorSession session = boot();
        session.profile().input().setTurbo("fire", 30);

        session.pressButton("fire");
        check(session.isTurboHeld("fire"), "a turbo button repeats while it is held");
        eq(0, session.pumpTurbo(), "and does not fire again before its interval is up");

        // Held past the interval, it presses again on its own.
        Thread.sleep(45);
        eq(1, session.pumpTurbo(), "held long enough, it presses again by itself");
        eq(0, session.pumpTurbo(), "then waits out the interval again");

        session.releaseButton("fire");
        check(!session.isTurboHeld("fire"), "letting go stops it");
        eq(0, session.pumpTurbo(), "and nothing repeats after that");

        session.profile().input().setTurbo("fire", 0);
        session.pressButton("fire");
        check(!session.isTurboHeld("fire"), "a button with turbo off is a plain key again");
        session.releaseButton("fire");
    }

    private void softKeysAreLandR(EmulatorSession session) {
        eq(MidpContext.KEY_SOFT_LEFT,
                com.mobicore.core.model.InputProfile.nokia().keyCodeFor("softLeft"),
                "L is the left softkey");
        eq(MidpContext.KEY_SOFT_RIGHT,
                com.mobicore.core.model.InputProfile.nokia().keyCodeFor("softRight"),
                "R is the right softkey");
        check(session.leftSoftKeyLabel() != null,
                "and L carries whatever label the running screen gave it");
    }
}
