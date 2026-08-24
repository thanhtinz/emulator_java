package com.mobicore.core.rt;

/**
 * The linear congruential generator {@code java.util.Random} is specified to
 * be, written out so its state can be read.
 *
 * <p>The algorithm is fixed by the Java library documentation, so a game gets
 * exactly the sequence it would get on a handset. Using the host's own
 * {@code Random} would give the same numbers but keep the seed private, and a
 * save state that cannot capture the seed would hand the player a saved game
 * that plays out differently from the one they saved — which, in a game whose
 * levels are generated, is the difference between a save and a lie.</p>
 */
public final class JavaRandom {

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    private long seed;
    private double nextGaussian;
    private boolean haveGaussian;

    public JavaRandom(long seed) {
        setSeed(seed);
    }

    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MASK;
        this.haveGaussian = false;
    }

    /** The scrambled seed, as it stands; for save states. */
    public long rawSeed() {
        return seed;
    }

    /** Puts back a seed captured by {@link #rawSeed()}. */
    public void restoreRawSeed(long raw) {
        this.seed = raw & MASK;
        this.haveGaussian = false;
    }

    private int next(int bits) {
        seed = (seed * MULTIPLIER + ADDEND) & MASK;
        return (int) (seed >>> (48 - bits));
    }

    public int nextInt() {
        return next(32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        if ((bound & -bound) == bound) {
            // A power of two takes the high bits, as the specification says.
            return (int) ((bound * (long) next(31)) >> 31);
        }
        int bits;
        int value;
        do {
            bits = next(31);
            value = bits % bound;
            // Reject the tail that would make low values fractionally likelier.
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    public long nextLong() {
        return ((long) next(32) << 32) + next(32);
    }

    public boolean nextBoolean() {
        return next(1) != 0;
    }

    public float nextFloat() {
        return next(24) / ((float) (1 << 24));
    }

    public double nextDouble() {
        return (((long) next(26) << 27) + next(27)) / (double) (1L << 53);
    }

    /** Marsaglia polar method, as the library specifies. */
    public double nextGaussian() {
        if (haveGaussian) {
            haveGaussian = false;
            return nextGaussian;
        }
        double v1;
        double v2;
        double s;
        do {
            v1 = 2 * nextDouble() - 1;
            v2 = 2 * nextDouble() - 1;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1 || s == 0);
        double multiplier = Math.sqrt(-2 * Math.log(s) / s);
        nextGaussian = v2 * multiplier;
        haveGaussian = true;
        return v1 * multiplier;
    }
}
