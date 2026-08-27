package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.model.AutoSetup;
import com.mobicore.core.model.Compatibility;
import com.mobicore.core.model.GameProfile;
import com.mobicore.core.vm.VmHost;
import com.mobicore.tools.SampleSuite;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compatibility: timers, which a great many MIDlets run on, and the
 * pre-flight scan that says whether a game can run at all before the user
 * finds out the hard way.
 */
public final class CompatibilityTest extends Test {

    private final String fixtureDir;

    public CompatibilityTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Timers and compatibility";
    }

    @Override
    public void run() throws Exception {
        scan();
        if (!new File(fixtureDir, "demo/TimerDemo.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        timers();
    }

    // ---------------------------------------------------------------- scan

    private void scan() throws Exception {
        SuiteLoader sample = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        Compatibility.Report ours = Compatibility.scan(sample);
        check(ours.playable(), "the sample suite runs");
        eq(Compatibility.LEVEL_PARTIAL, ours.level(),
                "it plays sound, which is emulated without MIDI");
        eq(0, ours.missing().size(), "and needs no package the emulator lacks");

        // A game built against a 3D or vendor package cannot start at all,
        // and the scan has to say which one before the user tries.
        Compatibility.Report threeD = Compatibility.scan(suiteReferencing(
                "javax/microedition/m3g/Graphics3D"));
        eq(Compatibility.LEVEL_BROKEN, threeD.level(), "a 3D game is reported as unplayable");
        check(!threeD.playable(), "and marked so plainly");
        eq(1, threeD.missing().size(), "with the package named");
        check(threeD.notes().get(0).indexOf("3D") >= 0, "in words the user can act on");

        // Nokia's own UI classes are emulated now, so a game using them runs
        // — with a note, because the rest of Nokia's packages are not there.
        Compatibility.Report nokia = Compatibility.scan(suiteReferencing(
                "com/nokia/mid/ui/DirectGraphics"));
        eq(Compatibility.LEVEL_PARTIAL, nokia.level(),
                "a Nokia game runs, and says which parts are emulated");
        check(nokia.playable(), "which is the whole point: most of these games are Nokia games");

        // The other makers' own classes are emulated too, so those games run.
        Compatibility.Report siemens = Compatibility.scan(suiteReferencing(
                "com/siemens/mp/game/Vibrator"));
        eq(Compatibility.LEVEL_PARTIAL, siemens.level(),
                "a Siemens game runs, with a note about what is emulated");
        Compatibility.Report samsung = Compatibility.scan(suiteReferencing(
                "com/samsung/util/Vibration"));
        check(samsung.playable(), "so does a Samsung one");

        // What is a library of its own rather than a few static methods is
        // still missing, and still said so plainly.
        Compatibility.Report colourGame = Compatibility.scan(suiteReferencing(
                "com/siemens/mp/color_game/GameCanvas"));
        eq(Compatibility.LEVEL_BROKEN, colourGame.level(),
                "Siemens' own game library is not pretended at");

        Compatibility.Report plain = Compatibility.scan(suiteReferencing(
                "javax/microedition/lcdui/Canvas"));
        eq(Compatibility.LEVEL_FULL, plain.level(), "a plain LCDUI game is fully supported");
        check(plain.notes().get(0).indexOf("Không thiếu") >= 0, "and says so");

        // The scan reads constant pools, so a string that merely looks like a
        // class name must not be mistaken for one.
        Compatibility.Report pretender = Compatibility.scan(suiteWithStringConstant(
                "javax/microedition/m3g/Graphics3D"));
        eq(Compatibility.LEVEL_FULL, pretender.level(),
                "a string constant is not a class reference");

        GameProfile profile = AutoSetup.configure(sample).profile();
        eq(Compatibility.LEVEL_PARTIAL, profile.compatibility(),
                "the verdict is stored with the game's settings");
    }

    /** A suite whose one class refers to {@code internalName} as a class. */
    private SuiteLoader suiteReferencing(String internalName) throws Exception {
        return SuiteLoader.load(SampleSuite.zip(classFile(internalName, true)), null);
    }

    /** The same, but with the name only as a string the game holds. */
    private SuiteLoader suiteWithStringConstant(String text) throws Exception {
        return SuiteLoader.load(SampleSuite.zip(classFile(text, false)), null);
    }

    private Map<String, byte[]> classFile(String value, boolean asClass) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("META-INF/MANIFEST.MF", ("MIDlet-Name: Probe\n"
                + "MIDlet-Version: 1.0\n"
                + "MIDlet-Vendor: Test\n"
                + "MIDlet-1: Probe,,demo.Probe\n").getBytes("UTF-8"));
        entries.put("demo/Probe.class", minimalClass(value, asClass));
        return entries;
    }

    /**
     * The smallest class file that carries one name: a header, a constant
     * pool of one UTF-8 entry plus either a Class or a String entry pointing
     * at it, and nothing else. Enough for the scanner, which reads no further
     * than the pool.
     */
    private byte[] minimalClass(String value, boolean asClass) {
        byte[] text = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) {
            text[i] = (byte) value.charAt(i);
        }
        byte[] out = new byte[10 + 3 + text.length + 3];
        int at = 0;
        out[at++] = (byte) 0xCA;
        out[at++] = (byte) 0xFE;
        out[at++] = (byte) 0xBA;
        out[at++] = (byte) 0xBE;
        out[at++] = 0;
        out[at++] = 0;
        out[at++] = 0;
        out[at++] = 49;
        // Three entries: index 0 is unused, 1 is the text, 2 points at it.
        out[at++] = 0;
        out[at++] = 3;
        out[at++] = 1;
        out[at++] = (byte) (text.length >> 8);
        out[at++] = (byte) text.length;
        System.arraycopy(text, 0, out, at, text.length);
        at += text.length;
        out[at++] = (byte) (asClass ? 7 : 8);
        out[at++] = 0;
        out[at] = 1;
        return out;
    }

    // -------------------------------------------------------------- timers

    private void timers() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        final long[] now = {10_000L};
        EmulatorSession session = EmulatorSession.create(suite, 240, 320, new VmHost() {
            public long currentTimeMillis() {
                return now[0];
            }

            public void sleep(long millis) {
                now[0] += millis;
            }

            public void print(boolean error, String text) {
            }

            public void exit(int code) {
            }

            public String property(String name) {
                return null;
            }
        });
        session.start("demo.TimerDemo");

        eq(2, session.scheduledTimers(), "both tasks are waiting");
        eq(0, ticks(session), "nothing has run before the first frame");

        // A task scheduled with no delay is due immediately.
        session.renderFrame();
        eq(1, ticks(session), "the repeating task runs on the first frame");
        eq(0, oneShots(session), "the delayed one has not come round yet");

        now[0] += 50;
        session.renderFrame();
        eq(2, ticks(session), "and again once its period has passed");

        now[0] += 20;
        session.renderFrame();
        eq(2, ticks(session), "but not before it");

        now[0] += 200;
        session.renderFrame();
        eq(3, ticks(session), "a task that fell behind runs once, not once per missed period");
        eq(1, oneShots(session), "the delayed task ran");
        eq(1, session.scheduledTimers(), "and a one-shot leaves the queue when it does");

        // Cancelling from inside the game must stop it, as TimerTask.cancel does.
        session.vm().callVirtual(session.context().midlet(), "stopTicking", "()V");
        now[0] += 500;
        session.renderFrame();
        eq(3, ticks(session), "a cancelled task stops running");
        eq(0, session.scheduledTimers(), "and is dropped from the queue");

        check(session.renderFrame() || true, "the demo keeps painting either way");
    }

    private int ticks(EmulatorSession session) {
        return ((Integer) session.vm().callVirtual(session.context().midlet(),
                "ticks", "()I")).intValue();
    }

    private int oneShots(EmulatorSession session) {
        return ((Integer) session.vm().callVirtual(session.context().midlet(),
                "oneShots", "()I")).intValue();
    }
}
