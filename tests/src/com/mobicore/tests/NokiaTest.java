package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.haptics.VibrationLog;
import com.mobicore.core.midp.NokiaUi;
import com.mobicore.core.model.GameProfile;
import com.mobicore.tools.SampleSuite;

import java.io.File;

/**
 * Nokia's own API, which most of these games were built against.
 *
 * <p>The fixture extends {@code FullCanvas} and draws entirely through
 * {@code DirectGraphics}, so this runs as real bytecode. The failure it
 * guards against is not a wrong pixel: it is the class loader giving up on
 * the superclass, which stops the game before a single frame and explains
 * nothing to the person holding the phone.</p>
 */
public final class NokiaTest extends Test {

    private final String fixtureDir;

    public NokiaTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Vendor APIs (Nokia, Siemens, Samsung, Motorola)";
    }

    @Override
    public void run() throws Exception {
        manipulations();
        if (!new File(fixtureDir, "demo/NokiaDemo.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        runsAsBytecode();
    }

    // ----------------------------------------------------------- bytecode

    private void runsAsBytecode() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, 240, 320, null);
        VibrationLog buzzes = new VibrationLog();
        session.setVibration(buzzes);
        session.start("demo.NokiaDemo");
        MidpContext context = session.context();

        check(context.current() != null, "a FullCanvas game starts at all");
        check(context.isFullScreen(),
                "and owns the whole screen, which is what FullCanvas was for");

        check(session.renderFrame(), "it paints");
        Framebuffer screen = context.screen();
        check(!blank(screen), "and the frame is not empty");

        // Each Nokia call the fixture makes leaves something specific behind.
        check(screen.pixel(4, 4) == 0xFF102030,
                "fillTriangle painted the background triangle");
        check(screen.pixel(30, 20) == 0xFFCC4422,
                "fillPolygon painted the block MIDP has no call for");
        check(screen.pixel(72, 14) == 0xFF00FF00,
                "drawPixels put an array of pixels on the screen");
        check(screen.pixel(92, 32) == 0xFFFFCC00,
                "and an image made by DirectUtils was drawn, turned");

        // Nokia's key codes are the handset's own, and the fixture reads them.
        session.keyPressed(-6);
        check(true, "a softkey code from FullCanvas reaches the game");

        // FullCanvas refuses commands, and a game may be relying on that.
        boolean refused = false;
        try {
            session.vm().callVirtual(context.current(), "setCommandListener",
                    "(Ljavax/microedition/lcdui/CommandListener;)V", new Object[]{null});
        } catch (RuntimeException expected) {
            refused = true;
        }
        check(refused, "FullCanvas still refuses commands, as the real one did");
        vibration(buzzes, suite);
    }

    /**
     * The buzz: a J2ME game's only physical feedback, and until now every
     * request for one was answered with "no".
     */
    private void vibration(VibrationLog buzzes, SuiteLoader suite) throws Exception {
        eq(5, buzzes.buzzes().size(),
                "every maker's way of asking reaches the device: Nokia's, MIDP's, "
                        + "Siemens', Samsung's and Motorola's");
        // 200 + 120 Nokia and MIDP, 200 Siemens (two tenths), 80 Samsung, 60 Motorola.
        eq(660, buzzes.totalMs(), "for as long as the game asked, in each one's own units");
        check(buzzes.cancels() >= 1, "and stopVibra calls it off");

        // The player's switch is the last word.
        GameProfile quiet = GameProfile.defaultsFor(suite.info());
        quiet.setVibration(false);
        EmulatorSession silent = EmulatorSession.create(suite, quiet, null, null, null);
        VibrationLog none = new VibrationLog();
        silent.setVibration(none);
        silent.start("demo.NokiaDemo");
        eq(0, none.buzzes().size(), "a game told not to buzz does not buzz, whoever asked");

        // Siemens states tones in hertz where MIDP states note numbers.
        eq(69, com.mobicore.core.midp.VendorApis.noteForFrequency(440),
                "440 hertz is concert A, not note 440");
        eq(81, com.mobicore.core.midp.VendorApis.noteForFrequency(880),
                "and an octave up is twelve notes up");

        // And a game told no is told no, so it can draw something instead.
        check(!silent.context().vibrate(100),
                "the honest answer is what MIDP promises to report");
    }

    // ------------------------------------------------------ manipulations

    /**
     * Turning and mirroring: why a game can ship one sprite sheet and draw a
     * character facing both ways.
     */
    private void manipulations() {
        Framebuffer source = new Framebuffer(2, 1);
        source.blendPixel(0, 0, 0xFFFF0000);
        source.blendPixel(1, 0, 0xFF00FF00);

        Framebuffer turned = NokiaUi.manipulate(source, NokiaUi.ROTATE_90);
        eq(1, turned.width(), "a quarter turn swaps the sides");
        eq(2, turned.height(), "in both directions");
        eq(0xFFFF0000, turned.pixel(0, 0), "and carries the pixels round with it");
        eq(0xFF00FF00, turned.pixel(0, 1), "in the right order");

        Framebuffer mirrored = NokiaUi.manipulate(source, NokiaUi.FLIP_HORIZONTAL);
        eq(0xFF00FF00, mirrored.pixel(0, 0), "a mirror swaps left for right");
        eq(0xFFFF0000, mirrored.pixel(1, 0), "and right for left");

        Framebuffer same = NokiaUi.manipulate(source, 0);
        check(same == source, "asking for nothing copies nothing");
    }

    private static boolean blank(Framebuffer frame) {
        int[] pixels = frame.pixels();
        for (int i = 1; i < pixels.length; i++) {
            if (pixels[i] != pixels[0]) {
                return false;
            }
        }
        return true;
    }
}
