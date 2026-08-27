package demo;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

/**
 * A game with a loop that never comes back.
 *
 * <p>Written the way the accident happens: a wait-for-something loop with no
 * way out. On a handset it was a game that stopped responding and a battery
 * pull; in an emulator it is a thread stuck inside the interpreter, a screen
 * that never changes, and no button that does anything.</p>
 *
 * <p>The test suite runs this to prove the emulator cuts the frame short and
 * says why, rather than sitting there.</p>
 */
public final class HangDemo extends MIDlet {

    protected void startApp() {
        Display.getDisplay(this).setCurrent(new Scene());
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    static final class Scene extends Canvas {

        private int spin;

        protected void paint(Graphics g) {
            g.setColor(0x000000);
            g.fillRect(0, 0, getWidth(), getHeight());
            // Chờ một thứ không bao giờ tới.
            while (spin >= 0) {
                spin++;
                if (spin == Integer.MAX_VALUE) {
                    spin = 0;
                }
            }
        }
    }
}
