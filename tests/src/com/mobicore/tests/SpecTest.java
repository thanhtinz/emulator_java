package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.model.DeviceProfile;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.vm.Vm;
import com.mobicore.tools.EmulatorScreen;
import com.mobicore.tools.SampleSuite;

import java.io.File;

/**
 * Những câu đặc tả trả lời khác với trực giác.
 *
 * <p>Sáu câu này lấy từ việc đọc mã nguồn các emulator thật — MicroEmulator,
 * FreeJ2ME, FreeJ2ME-Plus, KEmulator, SquirrelJME — và từ chính danh sách lỗi
 * mà cộng đồng của họ tự lập. Mỗi câu là một chỗ một cách cài đặt hợp lý lại
 * sai, và chỗ nào cũng có một báo cáo lỗi thật đứng sau.</p>
 */
public final class SpecTest extends Test {

    private final String fixtureDir;

    public SpecTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Đặc tả trả lời khác trực giác";
    }

    @Override
    public void run() throws Exception {
        if (!new File(fixtureDir, "demo/SpecProbe.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        theCanvasThatAskedForSilence();
        theCanvasThatDidNot();
        twoKeysAtOnce();
        theMemoryAGameCanSee();
        theScreensGamesWereBuiltFor();
        theSoundSaysWhenItEnds();
    }

    private EmulatorSession boot(DeviceProfile device) throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        GameProfile profile = GameProfile.defaultsFor(suite.info());
        profile.setDevice(device);
        EmulatorSession session = EmulatorSession.create(suite, profile, null, null,
                new EmulatorScreen.FixedClock());
        session.start("demo.SpecProbe");
        return session;
    }

    private EmulatorSession boot() throws Exception {
        return boot(DeviceProfile.custom(240, 320));
    }

    // ------------------------------------------------------ suppressKeyEvents

    /**
     * {@code GameCanvas(true)} means "do not tell me; I will ask".
     *
     * <p>A game that polls {@code getKeyStates} in its loop and is also sent
     * {@code keyPressed} reads every press twice, and the character moves at
     * double speed. The specification lets the game say so, and the machine
     * has to listen — while still delivering the keys the player works the
     * menus with, which are not game keys.</p>
     *
     * <p>This fixture also has no {@code paint} of its own, which is how
     * MIDlets other people wrote are written: a GameCanvas draws through its
     * own buffer. If the machine insists on paint, their source does not even
     * compile against it.</p>
     */
    private void theCanvasThatAskedForSilence() throws Exception {
        EmulatorSession session = boot();
        Vm vm = session.vm();
        vm.callVirtual(session.context().midlet(), "showQuiet", "()V");

        session.keyPressed(MidpContext.KEY_FIRE);
        session.keyReleased(MidpContext.KEY_FIRE);
        String[] heard = split(call(session, "heardQuiet"));
        eq("", heard[0], "a canvas that asked for silence is told nothing about the fire key");
        eq(String.valueOf(1 << MidpContext.ACTION_FIRE), heard[1],
                "but the press it did not hear about is still there to be polled");

        // Silence covers the game keys and nothing else. '1' is a digit and
        // not an action — unlike '5', which is fire on every keypad ever laid
        // out, and is therefore rightly held back with the rest of the pad.
        session.keyPressed('1');
        eq("P49 ", split(call(session, "heardQuiet"))[0],
                "a key that is not a game key is still delivered");
        session.destroy();
    }

    /** And a canvas that did not ask is told everything, as before. */
    private void theCanvasThatDidNot() throws Exception {
        EmulatorSession session = boot();
        Vm vm = session.vm();
        vm.callVirtual(session.context().midlet(), "showNoisy", "()V");

        session.keyPressed(MidpContext.KEY_FIRE);
        session.keyReleased(MidpContext.KEY_FIRE);
        eq("P" + MidpContext.KEY_FIRE + " R" + MidpContext.KEY_FIRE + " ",
                split(call(session, "heardNoisy"))[0],
                "the ordinary canvas hears the press and the release");
        session.destroy();
    }

    // ---------------------------------------------------------- several keys

    /**
     * Two keys held at once are two bits, not one answer.
     *
     * <p>FreeJ2ME's report on Rayman: pressing jump un-presses the left you
     * were holding. That is a whole bitfield being overwritten where it should
     * have one bit set. A platform game is unplayable and the cause is one
     * assignment.</p>
     */
    private void twoKeysAtOnce() throws Exception {
        EmulatorSession session = boot();
        Vm vm = session.vm();
        vm.callVirtual(session.context().midlet(), "showQuiet", "()V");
        int left = 1 << MidpContext.ACTION_LEFT;
        int fire = 1 << MidpContext.ACTION_FIRE;

        session.keyPressed(MidpContext.KEY_LEFT);
        session.keyPressed(MidpContext.KEY_FIRE);
        int both = states(session);
        eq(left, both & left, "left is still held after fire went down");
        eq(fire, both & fire, "and fire is down too");

        // Letting go of one leaves the other exactly where it was.
        session.keyReleased(MidpContext.KEY_FIRE);
        int after = states(session);
        eq(left, after & left, "letting go of fire does not let go of left");
        eq(0, after & fire, "and fire really did come up");
        session.destroy();
    }

    // ---------------------------------------------------------------- memory

    /**
     * A handset had a megabyte or two, and the game decides things by that.
     *
     * <p>Handing over the host's own figure tells a game it has gigabytes: it
     * loads every level at once, or skips the low-detail path it was written
     * to fall back on. Two questions here, because a plausible constant would
     * pass the first one on its own.</p>
     */
    private void theMemoryAGameCanSee() throws Exception {
        EmulatorSession session = boot();
        String[] parts = split(call(session, "memory"));
        long total = Long.parseLong(parts[0]);
        check(total > 0 && total <= 8L * 1024 * 1024,
                "the machine reports a handset's heap, not the host's: " + total);
        eq("giảm", parts[1], "and free memory falls when the game takes a bite out of it");
        long fell = Long.parseLong(parts[2]);
        check(fell >= 256 * 1024,
                "by at least what was asked for — " + fell + " bytes for a 256 KB array");

        eq("trả lại", call(session, "afterCollect"),
                "and a collection gives it back, which is the bargain the game knows");
        session.destroy();
    }

    // --------------------------------------------------------------- screens

    /**
     * A game built for 176x208 has to be told it is on 176x208.
     *
     * <p>It does not merely look small on the wrong screen: it cuts its sprite
     * sheet by the size it expects and reads past the edge of its own artwork.
     * The emulators that offer one screen size report exactly this, as blank
     * screens and as crashes.</p>
     */
    private void theScreensGamesWereBuiltFor() throws Exception {
        check(DeviceProfile.catalog().size() >= 4,
                "more than one screen is on offer");
        eq(176, DeviceProfile.byId("s60-176x208").width(), "the Series 60 screen is there");
        eq(128, DeviceProfile.byId("s40-128x128").height(), "and the Series 40 one");

        // And a session really runs at that size, framebuffer included.
        EmulatorSession session = boot(DeviceProfile.S60_176x208);
        eq(176, session.screen().width(), "the machine is built at the size asked for");
        eq(208, session.screen().height(), "in both directions");
        session.destroy();
    }

    // ---------------------------------------------------------------- sound

    /**
     * A sound that ran out says so, without being asked.
     *
     * <p>A game registers a {@code PlayerListener} precisely so it does not
     * have to poll. Firing {@code END_OF_MEDIA} only from {@code getState}
     * means the listener of a game that does not poll never fires at all: the
     * music never advances to the next track, the cutscene never ends.</p>
     */
    private void theSoundSaysWhenItEnds() throws Exception {
        EmulatorSession session = boot();
        Vm vm = session.vm();
        vm.callVirtual(session.context().midlet(), "showNoisy", "()V");
        vm.callVirtual(session.context().midlet(), "playShort", "()V");
        eq(0, ended(session), "nothing has ended while it is still playing");

        // A millisecond of sound, and a handful of frames — the clock moves on
        // its own with each ask. The machine has to notice by itself, because
        // nothing here ever calls getState.
        for (int i = 0; i < 20 && ended(session) == 0; i++) {
            session.renderFrame();
        }
        eq(1, ended(session), "the listener was told the sound ended, and told once");

        // Once only: firing again on the next frame is how a game ends up
        // playing its music twice over the top of itself.
        session.renderFrame();
        session.renderFrame();
        eq(1, ended(session), "and not told again on the frames after");
        session.destroy();
    }

    // ------------------------------------------------------------- plumbing

    private String call(EmulatorSession session, String method) {
        Vm vm = session.vm();
        return vm.stringOf(vm.callVirtual(session.context().midlet(), method,
                "()Ljava/lang/String;"));
    }

    private int ended(EmulatorSession session) {
        Vm vm = session.vm();
        return ((Integer) vm.callVirtual(session.context().midlet(), "ended", "()I")).intValue();
    }

    private int states(EmulatorSession session) {
        return Integer.parseInt(split(call(session, "heardQuiet"))[1]);
    }

    /** The fixtures answer as one string with bars in it. */
    private static String[] split(String text) {
        java.util.List<String> parts = new java.util.ArrayList<String>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '|' || text.charAt(i) == ',') {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        return parts.toArray(new String[parts.size()]);
    }
}
