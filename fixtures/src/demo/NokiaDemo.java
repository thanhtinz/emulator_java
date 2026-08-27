package demo;

import com.nokia.mid.ui.DeviceControl;
import com.nokia.mid.ui.DirectGraphics;
import com.nokia.mid.ui.DirectUtils;
import com.nokia.mid.ui.FullCanvas;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;

/**
 * A MIDlet written the way a Nokia-targeted game was written.
 *
 * <p>It extends {@code FullCanvas} rather than {@code Canvas}, draws through
 * {@code DirectGraphics}, turns an image with Nokia's own manipulation
 * constants, and asks the handset to vibrate. Compiled to real bytecode and
 * run by the interpreter in the test suite, because the failure this guards
 * against is the class loader giving up on the superclass before a single
 * frame is painted.</p>
 */
public final class NokiaDemo extends MIDlet {

    private final Scene scene = new Scene();

    protected void startApp() {
        Display.getDisplay(this).setCurrent(scene);
        // A game of this kind buzzes on start. Both ways of asking are here
        // because games use both: Nokia's own and MIDP's.
        DeviceControl.startVibra(50, 200);
        DeviceControl.stopVibra();
        // MIDP's own way of asking, which most games use.
        Display.getDisplay(this).vibrate(120);
        scene.repaint();
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    /** What the screen holds, drawn entirely through Nokia's own calls. */
    private static final class Scene extends FullCanvas {

        /** Set by paint, so a test can tell the drawing really ran. */
        int painted;

        protected void paint(Graphics graphics) {
            DirectGraphics direct = DirectUtils.getDirectGraphics(graphics);
            direct.setARGBColor(0xFF102030);
            direct.fillTriangle(0, 0, getWidth(), 0, 0, getHeight());

            // A filled polygon, which MIDP itself has no call for.
            int[] xs = {10, 60, 60, 10};
            int[] ys = {10, 10, 40, 40};
            direct.fillPolygon(xs, 0, ys, 0, 4, 0xFFCC4422);

            // Pixels straight in, then straight back out again.
            int[] block = new int[16];
            for (int i = 0; i < block.length; i++) {
                block[i] = 0xFF00FF00;
            }
            direct.drawPixels(block, false, 0, 4, 70, 12, 4, 4, 0,
                    DirectGraphics.TYPE_INT_8888_ARGB);
            direct.getPixels(block, 0, 4, 70, 12, 4, 4, DirectGraphics.TYPE_INT_8888_ARGB);

            // An image made by Nokia's own factory, drawn turned a quarter
            // turn: this is how one sprite sheet serves both directions.
            Image tile = DirectUtils.createImage(12, 6, 0xFFFFCC00);
            direct.drawImage(tile, 90, 30, 0, DirectGraphics.ROTATE_90);
            painted++;
        }

        public void keyPressed(int keyCode) {
            if (keyCode == KEY_SOFTKEY1) {
                painted = 0;
            }
        }
    }
}
