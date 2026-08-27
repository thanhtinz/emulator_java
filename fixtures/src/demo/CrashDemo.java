package demo;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

/**
 * A game that dies the way real ones die.
 *
 * <p>Half the J2ME library was written against one handset and shipped; on
 * anything else it walks off the end of an array or dereferences something it
 * only set up in a screen size it never saw. The test suite runs this as real
 * bytecode so the explanation shown to the player is produced from a genuine
 * crash rather than from a fabricated exception.</p>
 *
 * <p>It crashes on the first paint rather than in {@code startApp}: a game
 * that cannot even start is the easy case, and the one that matters is the
 * one that opens, draws, and then dies.</p>
 */
public final class CrashDemo extends MIDlet {

    protected void startApp() {
        Display.getDisplay(this).setCurrent(new Scene());
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    static final class Scene extends Canvas {

        /** Never filled in — which is exactly the bug being reproduced. */
        private int[] tiles;

        protected void paint(Graphics g) {
            g.setColor(0x000000);
            g.fillRect(0, 0, getWidth(), getHeight());
            // The line the game shipped with.
            g.setColor(tiles[0]);
            g.fillRect(10, 10, 20, 20);
        }
    }
}
