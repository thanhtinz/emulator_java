package demo;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.midlet.MIDlet;

import java.io.ByteArrayInputStream;

/**
 * The parts of the specification that answer differently from the guess.
 *
 * <p>Each of these is a place a reasonable implementation is wrong, and where
 * every real emulator that got it wrong has a bug report to show for it. None
 * of them stop a game; they make it behave like a different game.</p>
 */
public final class SpecProbe extends MIDlet {

    private Quiet quiet;
    private Noisy noisy;

    protected void startApp() {
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    // ------------------------------------------------------- suppressKeyEvents

    /** Shows the canvas that asked not to be told about game keys. */
    public void showQuiet() {
        quiet = new Quiet();
        Display.getDisplay(this).setCurrent(quiet);
    }

    /** Shows the one that did not ask. */
    public void showNoisy() {
        noisy = new Noisy();
        Display.getDisplay(this).setCurrent(noisy);
    }

    /** What each canvas was told, and what it can still poll. */
    public String heardQuiet() {
        return quiet.told + "|" + quiet.getKeyStates();
    }

    public String heardNoisy() {
        return noisy.told + "|" + noisy.getKeyStates();
    }

    /**
     * A GameCanvas that polls, and does not implement {@code paint}.
     *
     * <p>Both halves matter. It passes {@code true}, so the machine owes it
     * silence on the game keys; and it has no {@code paint}, because a
     * GameCanvas draws through its own buffer — which is exactly how the
     * MIDlets other people wrote are written.</p>
     */
    private static final class Quiet extends GameCanvas {

        String told = "";

        Quiet() {
            super(true);
        }

        protected void keyPressed(int keyCode) {
            told = told + "P" + keyCode + " ";
        }

        protected void keyReleased(int keyCode) {
            told = told + "R" + keyCode + " ";
        }
    }

    /** The same, but it asked to be told. */
    private static final class Noisy extends GameCanvas {

        String told = "";

        Noisy() {
            super(false);
        }

        protected void keyPressed(int keyCode) {
            told = told + "P" + keyCode + " ";
        }

        protected void keyReleased(int keyCode) {
            told = told + "R" + keyCode + " ";
        }

        protected void paint(Graphics g) {
        }
    }

    // ------------------------------------------------------------- freeMemory

    /**
     * Free memory before and after taking a bite out of it.
     *
     * <p>A game asks this to decide how much it can afford. The two numbers
     * have to differ, or the answer is a decoration.</p>
     */
    public String memory() {
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.freeMemory();
        // A quarter of a megabyte: big enough to be felt, small enough that
        // the machine has no cause to collect in the middle.
        byte[] bite = new byte[256 * 1024];
        bite[0] = 1;
        long after = runtime.freeMemory();
        long total = runtime.totalMemory();
        return total + "," + (before > after ? "giảm" : "không") + "," + (before - after);
    }

    /** What the machine says it has in total, and what a collection returns. */
    public String afterCollect() {
        Runtime runtime = Runtime.getRuntime();
        byte[] bite = new byte[256 * 1024];
        bite[0] = 1;
        long used = runtime.freeMemory();
        runtime.gc();
        return runtime.freeMemory() > used ? "trả lại" : "không";
    }

    // ---------------------------------------------------------------- Player

    private int ended;

    /**
     * Starts a very short sound and waits for the machine to say it ended.
     *
     * <p>The listener is registered and nothing is polled: a game that
     * registers a listener has said it is not going to ask.</p>
     */
    public void playShort() throws Exception {
        Player player = Manager.createPlayer(
                new ByteArrayInputStream(wav()), "audio/x-wav");
        player.addPlayerListener(new PlayerListener() {
            public void playerUpdate(Player which, String event, Object data) {
                if (PlayerListener.END_OF_MEDIA.equals(event)) {
                    ended++;
                }
            }
        });
        player.realize();
        player.start();
    }

    public int ended() {
        return ended;
    }

    /** Eight samples at 8 kHz: a millisecond of silence, and a real header. */
    private static byte[] wav() {
        int samples = 8;
        int size = 44 + samples;
        byte[] out = new byte[size];
        put(out, 0, "RIFF");
        le32(out, 4, size - 8);
        put(out, 8, "WAVE");
        put(out, 12, "fmt ");
        le32(out, 16, 16);
        le16(out, 20, 1);
        le16(out, 22, 1);
        le32(out, 24, 8000);
        le32(out, 28, 8000);
        le16(out, 32, 1);
        le16(out, 34, 8);
        put(out, 36, "data");
        le32(out, 40, samples);
        for (int i = 0; i < samples; i++) {
            out[44 + i] = (byte) 128;
        }
        return out;
    }

    private static void put(byte[] out, int at, String text) {
        for (int i = 0; i < text.length(); i++) {
            out[at + i] = (byte) text.charAt(i);
        }
    }

    private static void le16(byte[] out, int at, int value) {
        out[at] = (byte) value;
        out[at + 1] = (byte) (value >> 8);
    }

    private static void le32(byte[] out, int at, int value) {
        out[at] = (byte) value;
        out[at + 1] = (byte) (value >> 8);
        out[at + 2] = (byte) (value >> 16);
        out[at + 3] = (byte) (value >> 24);
    }
}
