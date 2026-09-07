package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;

/** Compile-time stub; the emulator implements this natively. */
public abstract class GameCanvas extends Canvas {

    public static final int UP_PRESSED = 1 << Canvas.UP;
    public static final int LEFT_PRESSED = 1 << Canvas.LEFT;
    public static final int RIGHT_PRESSED = 1 << Canvas.RIGHT;
    public static final int DOWN_PRESSED = 1 << Canvas.DOWN;
    public static final int FIRE_PRESSED = 1 << Canvas.FIRE;
    public static final int GAME_A_PRESSED = 1 << Canvas.GAME_A;
    public static final int GAME_B_PRESSED = 1 << Canvas.GAME_B;
    public static final int GAME_C_PRESSED = 1 << Canvas.GAME_C;
    public static final int GAME_D_PRESSED = 1 << Canvas.GAME_D;

    protected GameCanvas(boolean suppressKeyEvents) {
    }

    /**
     * A GameCanvas draws into its own buffer, so it does not need paint.
     *
     * <p>Real MIDlets subclass GameCanvas without implementing paint, and
     * every working implementation gives them this. Leaving it abstract here
     * means their source does not compile against the emulator at all.</p>
     */
    protected void paint(Graphics g) {
    }

    protected Graphics getGraphics() {
        return null;
    }

    public void flushGraphics() {
    }

    public void flushGraphics(int x, int y, int width, int height) {
    }

    public int getKeyStates() {
        return 0;
    }
}
