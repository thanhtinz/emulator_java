package com.mobicore.core.vm;

/**
 * Platform services the emulated program can reach.
 *
 * <p>Routing clocks, console output and exit through one interface keeps the VM
 * testable — a test can freeze time and capture output — and keeps the game
 * from touching the host process directly.</p>
 */
public interface VmHost {

    VmHost DEFAULT = new VmHost() {

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public void print(boolean error, String text) {
            if (error) {
                System.err.print(text);
            } else {
                System.out.print(text);
            }
        }

        @Override
        public void exit(int code) {
            throw new VmError("The MIDlet called System.exit(" + code + ")");
        }

        @Override
        public String property(String name) {
            return null;
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    };

    long currentTimeMillis();

    /** Console output; {@code error} selects {@code System.err}. */
    void print(boolean error, String text);

    void exit(int code);

    /** System property lookup, e.g. {@code microedition.platform}. */
    String property(String name);

    void sleep(long millis) throws InterruptedException;
}
