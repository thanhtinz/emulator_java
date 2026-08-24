package com.mobicore.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tiny zero-dependency test harness.
 *
 * <p>The emulator core deliberately has no third-party dependencies so it can
 * be translated for iOS; the test suite follows the same rule and runs with a
 * plain {@code javac} + {@code java} pair.</p>
 */
public abstract class Test {

    private final List<String> failures = new ArrayList<String>();
    private int checks;

    public abstract String name();

    public abstract void run() throws Exception;

    protected void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            failures.add(message);
        }
    }

    protected void eq(Object expected, Object actual, String message) {
        checks++;
        boolean same = expected == null ? actual == null : expected.equals(actual);
        if (!same) {
            failures.add(message + " — expected <" + expected + "> but was <" + actual + ">");
        }
    }

    protected void eq(int expected, int actual, String message) {
        eq(Integer.valueOf(expected), Integer.valueOf(actual), message);
    }

    protected void eqBytes(byte[] expected, byte[] actual, String message) {
        checks++;
        if (!Arrays.equals(expected, actual)) {
            failures.add(message + " — byte arrays differ");
        }
    }

    protected void fail(String message) {
        checks++;
        failures.add(message);
    }

    public List<String> failures() {
        return failures;
    }

    public int checks() {
        return checks;
    }
}
