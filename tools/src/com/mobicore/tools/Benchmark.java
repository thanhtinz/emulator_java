package com.mobicore.tools;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmHost;

/**
 * Measures the emulator, so a change to it can be judged rather than believed.
 *
 * <pre>./build.sh run com.mobicore.tools.Benchmark</pre>
 *
 * <p>Three numbers, because they fail differently: raw interpreter throughput
 * (a game's own logic), whole frames of a real MIDlet (logic plus the MIDP
 * library plus drawing), and the scaler that runs once per frame on the way to
 * the screen.</p>
 *
 * <p>Everything runs on a frozen clock. A benchmark that depended on the wall
 * clock would measure the machine it happened to run on as much as the code,
 * and the frame loop would throttle itself to the frame limit.</p>
 */
public final class Benchmark {

    /** Silent host with a clock the benchmark drives itself. */
    private static final class Clock implements VmHost {

        long now = 1_700_000_000_000L;

        public long currentTimeMillis() {
            now += 16;
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
            now += millis;
        }
    }

    public static void main(String[] args) throws Exception {
        String fixtures = args.length > 0 ? args[0] : "build/classes/fixtures";
        System.out.println("MobiCore benchmark");
        System.out.println("==================");
        interpreter(fixtures);
        frames(fixtures);
        scaling();
    }

    /** Pure bytecode: arithmetic, field access, calls, in a tight loop. */
    private static void interpreter(String fixtures) throws Exception {
        Vm vm = new Vm();
        vm.setHost(new Clock());
        com.mobicore.core.rt.Cldc.install(vm);
        vm.addSource(new DirectoryClassSource(fixtures));

        // Warm up first: the first run pays for class loading and for the
        // constant pool resolution that every later run reuses.
        run(vm, "work", 200_000);
        run(vm, "virtualWork", 200_000);

        // Best of three, not the mean: a slow run means something else on the
        // machine got in the way, and averaging that in measures the machine.
        report("interpreter", "M loops/s", best(vm, "work", 3_000_000L));
        report("virtual calls", "M calls/s", best(vm, "virtualWork", 3_000_000L));
    }

    private static double best(Vm vm, String method, long iterations) {
        double best = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            long start = System.nanoTime();
            run(vm, method, iterations);
            long elapsed = System.nanoTime() - start;
            double rate = iterations / (elapsed / 1_000_000_000.0) / 1_000_000.0;
            best = Math.max(best, rate);
        }
        return best;
    }

    private static void report(String label, String unit, double value) {
        StringBuilder padded = new StringBuilder(label);
        while (padded.length() < 14) {
            padded.append(' ');
        }
        System.out.println(String.format("%s%,.2f %s", padded, Double.valueOf(value), unit));
    }

    private static int run(Vm vm, String method, long iterations) {
        Object result = vm.callStatic("demo/Bench", method, "(I)I",
                Integer.valueOf((int) iterations));
        return ((Integer) result).intValue();
    }

    /** A real MIDlet: game logic, the MIDP library and drawing, per frame. */
    private static void frames(String fixtures) throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtures), SampleSuite.jad());
        Clock clock = new Clock();
        EmulatorSession session = EmulatorSession.create(suite, 240, 320, clock);
        session.start();

        for (int i = 0; i < 60; i++) {
            step(session);
        }
        int frames = 600;
        long start = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            step(session);
        }
        long elapsed = System.nanoTime() - start;

        double perSecond = frames / (elapsed / 1_000_000_000.0);
        System.out.println(String.format("game frames   %,.0f frames/s  (%.2f ms each)",
                Double.valueOf(perSecond), Double.valueOf(elapsed / 1_000_000.0 / frames)));
        session.destroy();
    }

    private static void step(EmulatorSession session) {
        session.keyPressed(com.mobicore.core.midp.MidpContext.KEY_RIGHT);
        session.renderFrame();
    }

    /** The scale to the phone's screen, which happens once per frame. */
    private static void scaling() {
        Framebuffer source = new Framebuffer(240, 320);
        for (int y = 0; y < 320; y++) {
            for (int x = 0; x < 240; x++) {
                source.setColor(0xFF000000 | (x << 16) | (y << 8) | (x ^ y));
                source.fillRect(x, y, 1, 1);
            }
        }
        for (int i = 0; i < 20; i++) {
            source.scaleSmooth(480, 640);
        }
        int rounds = 200;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            source.scaleSmooth(480, 640);
        }
        long elapsed = System.nanoTime() - start;
        System.out.println(String.format("scale to 2x   %.2f ms per frame",
                Double.valueOf(elapsed / 1_000_000.0 / rounds)));
    }

    private Benchmark() {
    }
}
