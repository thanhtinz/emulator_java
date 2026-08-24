package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.gfx.PngReader;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.vm.VmObject;
import com.mobicore.tools.SampleSuite;

import java.io.File;

/**
 * Boots the demo MIDlet through the full pipeline — JAR, class loading,
 * interpreter, MIDP library, framebuffer — and checks the pixels that come out.
 */
public final class MidpTest extends Test {

    private final String fixtureDir;

    public MidpTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "MIDP runtime + MIDlet";
    }

    @Override
    public void run() throws Exception {
        if (!new File(fixtureDir, "demo/SkyRunner.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, 240, 320, null);
        eq(EmulatorSession.STATE_NEW, session.state(), "a fresh session is new");

        session.start();
        eq(EmulatorSession.STATE_ACTIVE, session.state(), "start activates the MIDlet");
        eq("demo.SkyRunner", session.midletClass(), "the default MIDlet is started");
        check(session.context().current() != null, "startApp set a current Displayable");

        VmObject scene = session.context().current();
        eq("Sky Runner", session.vm().stringOf(scene.get("title")), "setTitle reached the emulator");
        eq(2, session.context().commandsOf(scene).size(), "both commands were registered");

        check(session.renderFrame(), "the first frame is painted");
        Framebuffer screen = session.screen();
        eq(240, screen.width(), "the screen matches the device profile");

        // The HUD is a black strip across the top; the ground is green.
        eq(0xFF000000, screen.pixel(120, 4), "the HUD bar is painted");
        int ground = screen.pixel(120, 276);
        check((ground & 0x00FF00) > 0x005000, "the tiled ground is green, was "
                + Integer.toHexString(ground));
        int sky = screen.pixel(10, 30);
        check((sky & 0xFF) > 0x40, "the sky gradient is blue, was " + Integer.toHexString(sky));

        // Input has to reach the game and change what it draws.
        int startX = ((Integer) session.vm().callVirtual(scene, "playerX", "()I")).intValue();
        session.keyPressed(MidpContext.KEY_RIGHT);
        int movedX = ((Integer) session.vm().callVirtual(scene, "playerX", "()I")).intValue();
        eq(startX + 8, movedX, "a right key press moves the player");
        session.keyPressed(MidpContext.KEY_LEFT);
        eq(startX, ((Integer) session.vm().callVirtual(scene, "playerX", "()I")).intValue(),
                "a left key press moves the player back");

        check((session.context().keyStates() & (1 << MidpContext.ACTION_LEFT)) != 0,
                "GameCanvas key state latches the pressed action");
        session.keyReleased(MidpContext.KEY_LEFT);
        check((session.context().keyStates() & (1 << MidpContext.ACTION_LEFT)) == 0,
                "releasing clears the key state");

        int scoreBefore = ((Integer) session.vm().callVirtual(scene, "score", "()I")).intValue();
        session.keyPressed(MidpContext.KEY_FIRE);
        eq(scoreBefore + 10, ((Integer) session.vm().callVirtual(scene, "score", "()I")).intValue(),
                "fire scores points");

        // Frames must keep advancing and repainting.
        for (int i = 0; i < 12; i++) {
            session.vm().callVirtual(scene, "tick", "()V");
            session.renderFrame();
        }
        check(session.context().frames() >= 13, "every tick produced a frame");

        byte[] png = session.screenshotPng();
        check(PngReader.looksLikePng(png), "the screenshot is a PNG");
        PngReader.Image shot = PngReader.decode(png);
        eq(240, shot.width, "the screenshot is screen sized");
        eq(320, shot.height, "the screenshot is screen tall");

        // Commands fire through the listener the MIDlet installed.
        VmObject exitCommand = session.context().commandsOf(scene).get(1);
        check(session.invokeCommand(exitCommand), "the command listener was invoked");
        check(session.isFinished(), "the Exit command destroyed the MIDlet");

        session.destroy();
        eq(EmulatorSession.STATE_DESTROYED, session.state(), "destroy is terminal");

        check(session.log().size() >= 2, "the emulator logged the lifecycle");
    }
}
