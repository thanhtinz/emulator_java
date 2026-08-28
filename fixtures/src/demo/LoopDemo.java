package demo;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

/**
 * A game that drives its own frames, the way most of them did.
 *
 * <p>The shape is always the same: move the world, ask for a repaint, then
 * <em>wait for it to happen</em> before moving anything again:</p>
 *
 * <pre>while (playing) { tick(); repaint(); serviceRepaints(); }</pre>
 *
 * <p>That middle step is a promise MIDP makes — {@code serviceRepaints} blocks
 * until the paint is done — and a game written this way keeps its own timing
 * only if the promise is kept. With it broken the loop runs free and the
 * screen shows whatever frame it happened to catch.</p>
 */
public final class LoopDemo extends MIDlet {

    /** How many times the game asked for a frame. */
    public int asked;
    /** How many times paint actually ran. */
    public int painted;
    /** True when a paint happened inside serviceRepaints, not after it. */
    public boolean paintedInTime;

    private Scene scene;

    protected void startApp() {
        scene = new Scene();
        Display.getDisplay(this).setCurrent(scene);
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    /** One turn of the game's own loop, as the test drives it. */
    public void step() {
        scene.x++;
        asked++;
        int before = painted;
        scene.repaint();
        scene.serviceRepaints();
        // Đúng lời hứa của MIDP thì lúc này khung hình đã vẽ xong.
        if (painted > before) {
            paintedInTime = true;
        }
    }

    /** Paints from inside paint, which must not go round in circles. */
    public void stepReentrant() {
        scene.reentrant = true;
        step();
        scene.reentrant = false;
    }

    final class Scene extends Canvas {

        int x;
        boolean reentrant;

        protected void paint(Graphics g) {
            painted++;
            g.setColor(0x203040);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(0xFFCC33);
            g.fillRect(x % Math.max(1, getWidth()), 20, 12, 12);
            if (reentrant) {
                // Game gọi serviceRepaints từ trong chính paint của nó: có
                // thật, và nếu máy ảo vẽ tiếp ở đây thì nó gọi đệ quy không
                // đáy.
                serviceRepaints();
            }
        }
    }
}
