package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.emu.SpeedClock;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.vm.VmHost;
import com.mobicore.tools.SampleSuite;

import java.io.File;
import java.util.Map;

/**
 * Running a game faster or slower than a handset did.
 *
 * <p>The thing being tested is not a frame counter: a J2ME game paces itself
 * off {@code System.currentTimeMillis} and its own sleeps, so the test is
 * that the clock the game sees moves at the speed asked for, that the sleeps
 * shorten to match, and that changing speed mid-game never moves that clock
 * backwards — a game that sees time go backwards computes a negative frame
 * delta, and that is how a sprite ends up somewhere it cannot be.</p>
 */
public final class SpeedTest extends Test {

    private final String fixtureDir;

    public SpeedTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Game speed";
    }

    @Override
    public void run() throws Exception {
        clock();
        sleeps();
        if (!new File(fixtureDir, "demo/SkyRunner.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        throughTheBridge();
    }

    // ---------------------------------------------------------------- clock

    private void clock() {
        Fake real = new Fake();
        SpeedClock clock = new SpeedClock(real);
        eq(SpeedClock.NORMAL, clock.speed(), "a game starts at a handset's pace");

        long start = clock.currentTimeMillis();
        real.now += 1000;
        eq(1000L, clock.currentTimeMillis() - start, "at normal speed the two clocks agree");

        clock.setSpeed(200);
        long doubled = clock.currentTimeMillis();
        real.now += 1000;
        eq(2000L, clock.currentTimeMillis() - doubled,
                "at double speed a real second is two game seconds");

        clock.setSpeed(50);
        long halved = clock.currentTimeMillis();
        real.now += 1000;
        eq(500L, clock.currentTimeMillis() - halved,
                "and at half speed it is half a game second");

        // The rebase is what keeps a change from rewriting history.
        check(halved >= doubled, "changing speed never moves the game's clock backwards");
        long before = clock.currentTimeMillis();
        clock.setSpeed(300);
        check(clock.currentTimeMillis() >= before, "nor does speeding up an hour-old game");

        eq(200, new SpeedClock(new Fake()).nextSpeed(), "the control steps up from normal");
    }

    private void sleeps() {
        Fake real = new Fake();
        SpeedClock clock = new SpeedClock(real);
        try {
            clock.sleep(40);
            eq(40L, real.slept, "a normal-speed sleep is what the game asked for");
            clock.setSpeed(200);
            real.slept = 0;
            clock.sleep(40);
            eq(20L, real.slept,
                    "at double speed the sleep halves too — otherwise the sleep alone would "
                            + "hold the game to its old pace");
        } catch (InterruptedException e) {
            fail("sleeping threw " + e);
        }
    }

    // --------------------------------------------------------------- bridge

    private void throughTheBridge() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> imported = Json.readObject(
                facade.importSuite(SampleSuite.jar(fixtureDir), SampleSuite.jad()));
        String suiteId = Json.string(Json.child(imported, "game"), "suiteId", "");

        check(!Json.bool(Json.readObject(facade.cycleSpeed()), "ok", true),
                "there is nothing to speed up before a game starts");
        facade.startGame(suiteId);
        eq(100, Json.integer(Json.readObject(facade.speedJson()), "speed", 0),
                "a game starts at a handset's pace");

        Map<String, Object> faster = Json.readObject(facade.cycleSpeed());
        eq(200, Json.integer(faster, "speed", 0), "the control steps up");
        eq("2×", Json.string(faster, "label", ""), "with something to show the player");
        eq(300, Json.integer(Json.readObject(facade.cycleSpeed()), "speed", 0), "and up again");
        eq(50, Json.integer(Json.readObject(facade.cycleSpeed()), "speed", 0),
                "then round to half speed, for a game tuned to a slower handset");
        eq(100, Json.integer(Json.readObject(facade.setSpeed(100)), "speed", 0),
                "and normal can be asked for directly");

        // The running game keeps working at speed: this is the whole point.
        facade.setSpeed(300);
        for (int i = 0; i < 20; i++) {
            facade.renderFrame();
        }
        check(facade.screenshotPng().length > 100, "and the game is still drawing frames");
        facade.stopGame();
    }

    /** A clock that only moves when the test says so. */
    private static final class Fake implements VmHost {

        long now = 1_700_000_000_000L;
        long slept;

        public long currentTimeMillis() {
            return now;
        }

        public void print(boolean error, String text) {
        }

        public void exit(int code) {
        }

        public String property(String name) {
            return null;
        }

        public void sleep(long millis) {
            slept += millis;
            now += millis;
        }
    }
}
