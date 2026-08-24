package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.emu.SaveState;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.midp.MidpContext;
import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.rt.JavaRandom;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.vm.VmHost;
import com.mobicore.tools.SampleSuite;

import java.io.File;
import java.util.Map;
import java.util.Random;

/**
 * Save states: a game put back exactly where it was.
 *
 * <p>The test that matters is the last one — save mid-game, restore into a
 * session that started from scratch, and check the two produce the same
 * frames from then on. Anything less proves only that bytes were written.</p>
 */
public final class SaveStateTest extends Test {

    private final String fixtureDir;

    public SaveStateTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Save states";
    }

    @Override
    public void run() throws Exception {
        random();
        if (!new File(fixtureDir, "demo/SkyRunner.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        roundTrip();
        refusals();
        throughTheBridge();
    }

    /**
     * The generator has to match the one a handset had, or a saved game with
     * generated levels would play out differently after being restored.
     */
    private void random() {
        Random host = new Random(42L);
        JavaRandom ours = new JavaRandom(42L);
        boolean same = true;
        for (int i = 0; i < 200; i++) {
            same &= host.nextInt() == ours.nextInt();
            same &= host.nextInt(97) == ours.nextInt(97);
            same &= host.nextLong() == ours.nextLong();
            same &= host.nextBoolean() == ours.nextBoolean();
            same &= host.nextDouble() == ours.nextDouble();
        }
        check(same, "the random generator matches the one the specification fixes");

        JavaRandom captured = new JavaRandom(7L);
        for (int i = 0; i < 10; i++) {
            captured.nextInt();
        }
        JavaRandom restored = new JavaRandom(0L);
        restored.restoreRawSeed(captured.rawSeed());
        eq(captured.nextInt(), restored.nextInt(), "a captured seed continues the sequence");
    }

    // ---------------------------------------------------------- round trip

    private void roundTrip() throws Exception {
        EmulatorSession played = boot();
        // Play a while, so the saved state is somewhere a fresh start is not:
        // the runner has moved, the world has scrolled, the score has risen.
        for (int i = 0; i < 40; i++) {
            step(played);
        }
        int score = scoreOf(played);
        check(score > 0, "the game got somewhere before saving, score " + score);

        byte[] blob = SaveState.capture(played);
        check(blob.length > 1000, "a save state has the heap in it, " + blob.length + " bytes");
        eq(played.info().suiteId(), SaveState.suiteIdOf(blob),
                "a save names the game it belongs to without being restored");

        // Restore into a session that started from nothing, which is what
        // reopening the app actually does.
        EmulatorSession resumed = boot();
        eq(0, scoreOf(resumed), "a fresh session starts at zero");
        SaveState.restore(resumed, blob);
        eq(score, scoreOf(resumed), "restoring puts the score back");

        resumed.renderFrame();
        played.renderFrame();
        check(sameScreen(played.screen(), resumed.screen()),
                "and the restored game draws the same frame");

        // From here the two must stay in step: same input, same frames.
        for (int i = 0; i < 20; i++) {
            step(played);
            step(resumed);
        }
        eq(scoreOf(played), scoreOf(resumed), "the two run on identically");
        check(sameScreen(played.screen(), resumed.screen()),
                "frame for frame, twenty frames later");

        played.destroy();
        resumed.destroy();
    }

    // ------------------------------------------------------------ refusals

    private void refusals() throws Exception {
        EmulatorSession session = boot();
        byte[] blob = SaveState.capture(session);

        EmulatorSession other = boot();
        byte[] corrupted = new byte[blob.length];
        System.arraycopy(blob, 0, corrupted, 0, blob.length);
        corrupted[0] = 0;
        expectRefusal(other, corrupted, "a file that is not a save state is refused");

        byte[] truncated = new byte[40];
        System.arraycopy(blob, 0, truncated, 0, truncated.length);
        expectRefusal(other, truncated, "a truncated save is refused rather than half-applied");

        eq(null, SaveState.suiteIdOf(new byte[]{1, 2, 3}),
                "and nothing claims to know which game a broken file belongs to");

        session.destroy();
        other.destroy();
    }

    /**
     * What the app actually calls: leave a game and it is saved; open it
     * again and it is where it was.
     */
    private void throughTheBridge() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        check(Json.bool(Json.readObject(facade.startGame(suiteId)), "ok", false),
                "the game starts");
        check(!facade.hasSaveState(suiteId), "and has nothing saved yet");
        for (int i = 0; i < 25; i++) {
            facade.renderFrame();
        }

        check(Json.bool(Json.readObject(facade.stopGameSaving()), "ok", false),
                "leaving the game saves it");
        check(facade.hasSaveState(suiteId), "the saved state is on disk");
        check(facade.saveStateThumbnail(suiteId).length > 0,
                "with a picture of the screen the player left");

        Map<String, Object> resumed = Json.readObject(facade.resumeGame(suiteId));
        check(Json.bool(resumed, "ok", false), "opening it again starts the game");
        check(Json.bool(resumed, "resumed", false), "and puts it back where it was");

        check(Json.bool(Json.readObject(facade.deleteSaveState(suiteId)), "ok", false),
                "a saved state can be thrown away");
        check(!facade.hasSaveState(suiteId), "and then it is gone");
        Map<String, Object> fresh = Json.readObject(facade.resumeGame(suiteId));
        check(Json.bool(fresh, "ok", false), "a game with nothing saved still starts");
        check(!Json.bool(fresh, "resumed", true), "and says it started from the beginning");
        facade.stopGame();
    }

    private void expectRefusal(EmulatorSession session, byte[] blob, String message) {
        try {
            SaveState.restore(session, blob);
            fail(message);
        } catch (SaveState.NotSavable expected) {
            check(true, message);
        }
    }

    // --------------------------------------------------------------- tools

    private EmulatorSession boot() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        final long[] now = {1_700_000_000_000L};
        EmulatorSession session = EmulatorSession.create(suite, 240, 320, new VmHost() {
            public long currentTimeMillis() {
                now[0] += 33;
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
        session.start();
        return session;
    }

    /** One frame as the app runs it: the game ticks, then the screen paints. */
    private void step(EmulatorSession session) {
        session.vm().callVirtual(session.context().current(), "tick", "()V");
        session.keyPressed(MidpContext.KEY_RIGHT);
        session.renderFrame();
    }

    /** The scene keeps the score, and the scene is the screen being shown. */
    private int scoreOf(EmulatorSession session) {
        return ((Integer) session.vm().callVirtual(session.context().current(),
                "score", "()I")).intValue();
    }

    private boolean sameScreen(Framebuffer left, Framebuffer right) {
        if (left.width() != right.width() || left.height() != right.height()) {
            return false;
        }
        int[] a = left.pixels();
        int[] b = right.pixels();
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }
}
