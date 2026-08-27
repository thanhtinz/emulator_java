package com.mobicore.core.haptics;

import java.util.ArrayList;
import java.util.List;

/**
 * A vibration sink that writes down what was asked for.
 *
 * <p>What the desktop preview and the tests use: there is no motor to shake,
 * and "the game asked for 200ms here" is the thing worth checking anyway.</p>
 */
public final class VibrationLog implements VibrationSink {

    /** One request, as the game made it. */
    public static final class Buzz {

        private final int durationMs;

        Buzz(int durationMs) {
            this.durationMs = durationMs;
        }

        public int durationMs() {
            return durationMs;
        }
    }

    private final List<Buzz> buzzes = new ArrayList<Buzz>();
    private int cancels;

    public List<Buzz> buzzes() {
        return buzzes;
    }

    public int cancels() {
        return cancels;
    }

    public int totalMs() {
        int total = 0;
        for (int i = 0; i < buzzes.size(); i++) {
            total += buzzes.get(i).durationMs();
        }
        return total;
    }

    public boolean vibrate(int durationMs) {
        if (durationMs <= 0) {
            // Zero is how MIDP says "stop", not a buzz of no length.
            cancels++;
            return true;
        }
        buzzes.add(new Buzz(durationMs));
        return true;
    }

    public void cancel() {
        cancels++;
    }
}
