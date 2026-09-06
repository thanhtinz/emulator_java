package demo;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.midlet.MIDlet;

/**
 * The four things a game does that an emulator can get wrong quietly.
 *
 * <p>None of these stop a game. They make it behave slightly wrong in a way
 * the player feels and cannot describe: a fire button that misses, a character
 * that steps sideways when it turns, a band along the bottom that stops
 * repainting, and the menu's music still playing under the level.</p>
 */
public final class InputProbe extends MIDlet {

    /** Frame size for the sprite: not square, so turning it is visible. */
    public static final int WIDE = 6;
    public static final int TALL = 4;

    private Board board;
    private Away away;

    protected void startApp() {
        board = new Board();
        Display.getDisplay(this).setCurrent(board);
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    public Board board() {
        return board;
    }

    // ------------------------------------------------------------ key states

    /** What {@code getKeyStates} says right now, and latches. */
    public int keys() {
        return board.getKeyStates();
    }

    // -------------------------------------------------------- reference pixel

    private Sprite figure;

    /** A sprite with its reference pixel off centre, placed by that pixel. */
    public void place(int refX, int refY, int screenX, int screenY) {
        Image frame = Image.createImage(WIDE, TALL);
        Graphics g = frame.getGraphics();
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, WIDE, TALL);
        figure = new Sprite(frame, WIDE, TALL);
        figure.defineReferencePixel(refX, refY);
        figure.setRefPixelPosition(screenX, screenY);
    }

    /** Turns it, then says where its reference pixel ended up. */
    public String turn(int transform) {
        figure.setTransform(transform);
        return figure.getRefPixelX() + "," + figure.getRefPixelY();
    }

    /** Where the top-left corner is, which is what moves instead. */
    public String corner() {
        return figure.getX() + "," + figure.getY();
    }

    // ------------------------------------------------------------ back buffer

    /**
     * Fills the buffer, goes full screen, then paints one colour over the lot.
     *
     * <p>If the buffer is still the old size, the strip the command bar used
     * to occupy keeps the first colour for ever.</p>
     */
    /** Fills the buffer, goes full screen, then paints over the lot. */
    public void repaintFullScreen() {
        board.repaintFullScreen();
    }

    private Later later;

    /**
     * The way a game really meets this: it builds its play screen while the
     * menu is still up.
     *
     * <p>The menu has a title and a command, so the system is keeping a strip
     * of the screen and the canvas is short. The buffer is made at that size.
     * Then the play screen goes up, the strip comes back to the game, and a
     * buffer built for the old size no longer covers the bottom.</p>
     */
    public void buildWhileMenuIsUp() {
        later = new Later();
    }

    /** Shows it and paints one colour over the whole thing. */
    public void playOn() {
        Display.getDisplay(this).setCurrent(later);
        later.paintAll(0x00FF00);
    }

    public String canvasSize() {
        return board.getWidth() + "x" + board.getHeight();
    }

    public String laterSize() {
        return later.getWidth() + "x" + later.getHeight();
    }

    // ------------------------------------------------------------ hideNotify

    /** How many times the first screen has been told it is going away. */
    public int hidden() {
        return board.hidden;
    }

    public int shown() {
        return board.shown;
    }

    /** Moves to a second screen, so the first one has to be told. */
    public void goAway() {
        if (away == null) {
            away = new Away();
        }
        Display.getDisplay(this).setCurrent(away);
    }

    public void comeBack() {
        Display.getDisplay(this).setCurrent(board);
    }

    /** The screen being watched. */
    public static final class Board extends GameCanvas {

        int hidden;
        int shown;

        Board() {
            super(false);
            // A title and a command, so the system really does keep a strip of
            // the screen for itself — without them there is no chrome for
            // full screen mode to hand back and nothing to get wrong.
            setTitle("Thử");
            addCommand(new Command("Thoát", Command.EXIT, 1));
        }

        protected void showNotify() {
            shown++;
        }

        protected void hideNotify() {
            hidden++;
        }

        /** Fills the buffer, goes full screen, then paints over the lot. */
        void repaintFullScreen() {
            Graphics g = getGraphics();
            g.setColor(0xFF0000);
            g.fillRect(0, 0, getWidth(), getHeight());
            flushGraphics();

            setFullScreenMode(true);
            g = getGraphics();
            g.setColor(0x00FF00);
            g.fillRect(0, 0, getWidth(), getHeight());
            flushGraphics();
        }

        protected void paint(Graphics g) {
        }
    }

    /** Somewhere else to be. */
    private static final class Away extends GameCanvas {

        Away() {
            super(false);
        }

        protected void paint(Graphics g) {
        }
    }

    /** The play screen, built while the menu was still on show. */
    private static final class Later extends GameCanvas {

        Later() {
            super(false);
        }

        void paintAll(int colour) {
            Graphics g = getGraphics();
            g.setColor(colour);
            g.fillRect(0, 0, getWidth(), getHeight());
            flushGraphics();
        }

        protected void paint(Graphics g) {
        }
    }
}
