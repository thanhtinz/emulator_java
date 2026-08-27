package com.mobicore.core.haptics;

/**
 * Where a request to vibrate goes.
 *
 * <p>The core decides when a game asked for it and for how long; it never
 * touches a motor, because there is no portable way to. Android hands this to
 * the system vibrator, iOS to its haptic engine, and the preview and the test
 * suite to {@link VibrationLog}, which records instead of buzzing.</p>
 *
 * <p>Worth having at all because a J2ME game's only physical feedback was the
 * handset shaking: the buzz on a crash or a hit is part of what the game was,
 * and until now every call to vibrate was answered with "no".</p>
 */
public interface VibrationSink {

    /**
     * Vibrates for {@code durationMs}.
     *
     * @return true when the device really will vibrate, which is what MIDP's
     *     own {@code Display.vibrate} promises to report — a game may draw a
     *     different effect when it is told no
     */
    boolean vibrate(int durationMs);

    /** Stops early; a game that starts a long buzz can call this off. */
    void cancel();
}
