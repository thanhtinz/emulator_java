package demo;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

import java.util.Timer;
import java.util.TimerTask;

/**
 * A MIDlet with no loop of its own: a {@code TimerTask} drives everything.
 *
 * <p>This is how a large share of J2ME games were written — schedule a task
 * every few tens of milliseconds and let it move the world and repaint. The
 * test suite runs this as bytecode to prove the emulator's timers actually
 * fire, repeat, and stop when cancelled.</p>
 */
public final class TimerDemo extends MIDlet {

    private final Scene scene = new Scene();
    private Timer timer;

    protected void startApp() {
        Display.getDisplay(this).setCurrent(scene);
        timer = new Timer();
        // 50ms is what a game of the era asked for when it wanted "as often
        // as the handset can manage".
        timer.schedule(scene.tick, 0, 50);
        timer.schedule(scene.once, 200);
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
        if (timer != null) {
            timer.cancel();
        }
    }

    /** Counts what ran, so a test can read the numbers back out. */
    public int ticks() {
        return scene.ticks;
    }

    public int oneShots() {
        return scene.oneShots;
    }

    public void stopTicking() {
        scene.tick.cancel();
    }

    static final class Scene extends Canvas {

        int ticks;
        int oneShots;
        int x;

        final TimerTask tick = new TimerTask() {
            public void run() {
                ticks++;
                x = (x + 4) % 240;
                repaint();
            }
        };

        final TimerTask once = new TimerTask() {
            public void run() {
                oneShots++;
            }
        };

        protected void paint(Graphics g) {
            g.setColor(0x101820);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(0x6FC3FF);
            g.fillRect(x, getHeight() / 2 - 6, 12, 12);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            g.setColor(0xE6EDF5);
            g.drawString("Nhịp: " + ticks, 6, 6, Graphics.TOP | Graphics.LEFT);
        }
    }
}
