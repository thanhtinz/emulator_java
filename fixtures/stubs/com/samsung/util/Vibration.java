package com.samsung.util;

/** Stub for javac only; the emulator supplies this class natively. */
public final class Vibration {

    private Vibration() {
    }

    public static boolean isSupported() {
        return false;
    }

    /** Duration in milliseconds, then a strength no phone today takes. */
    public static void start(int durationMs, int strength) {
    }

    public static void stop() {
    }
}
