package demo;

/**
 * A loop for the benchmark to run.
 *
 * <p>Deliberately ordinary: integer arithmetic, an array, a field, and a
 * method call per iteration — the mix a J2ME game's own logic is made of.
 * Nothing here is chosen to flatter the interpreter, and nothing calls into
 * the MIDP library, so the number measures the virtual machine alone.</p>
 */
public final class Bench {

    private int state = 12345;

    private final int[] table = new int[64];

    public static int work(int iterations) {
        Bench bench = new Bench();
        int total = 0;
        for (int i = 0; i < iterations; i++) {
            total += bench.step(i);
        }
        return total;
    }

    /**
     * The same amount of work reached through a virtual call.
     *
     * <p>Separate because it is the shape a MIDP game actually has: the
     * device calls the game's paint, the game calls Graphics, and both are
     * virtual calls the emulator must resolve. Two implementations exist so
     * nothing can quietly turn the call site into a direct one.</p>
     */
    public static int virtualWork(int iterations) {
        Step[] steps = {new Add(), new Xor()};
        int total = 0;
        for (int i = 0; i < iterations; i++) {
            total += steps[i & 1].apply(total, i);
        }
        return total;
    }

    interface Step {
        int apply(int total, int i);
    }

    static final class Add implements Step {
        public int apply(int total, int i) {
            return (total + i) & 0xFFFF;
        }
    }

    static final class Xor implements Step {
        public int apply(int total, int i) {
            return (total ^ i) & 0xFFFF;
        }
    }

    private int step(int i) {
        state = state * 1103515245 + 12345;
        int index = (state >>> 16) & 63;
        table[index] += i ^ state;
        return table[index] >>> 24;
    }
}
