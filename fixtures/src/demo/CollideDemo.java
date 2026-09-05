package demo;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.game.TiledLayer;
import javax.microedition.midlet.MIDlet;

/**
 * The one collision case that tells a real implementation from a fake one.
 *
 * <p>Two sprites shaped as opposite corner wedges. Slide them together and
 * their boxes overlap long before anything drawn does: the overlap is the
 * transparent corner of one against the transparent corner of the other. A
 * box test says they have collided; a pixel test says they have not — and at
 * one particular pair of positions the two answers differ, which is the only
 * way to prove the flag is being read at all.</p>
 *
 * <p>Everything is built out of {@code createRGBImage} with real transparent
 * pixels rather than drawn, so the shapes are exact and the test can say
 * where the edges are.</p>
 */
public final class CollideDemo extends MIDlet {

    /** Both wedges are this many pixels square. */
    public static final int SIZE = 8;

    protected void startApp() {
        Display.getDisplay(this).setCurrent(new Board());
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    /**
     * A wedge filling one triangular half of the square.
     *
     * @param lower true for the bottom-left half, false for the top-right
     */
    public static Image wedge(boolean lower) {
        int[] pixels = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean drawn = lower ? x <= y : x >= y;
                pixels[y * SIZE + x] = drawn ? 0xFFE2574C : 0x00000000;
            }
        }
        return Image.createRGBImage(pixels, SIZE, SIZE, true);
    }

    /** A tile sheet: tile 1 solid, tile 2 blank. */
    public static Image tiles() {
        int[] pixels = new int[SIZE * 2 * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE * 2; x++) {
                pixels[y * SIZE * 2 + x] = x < SIZE ? 0xFF4C9BE2 : 0x00000000;
            }
        }
        return Image.createRGBImage(pixels, SIZE * 2, SIZE, true);
    }

    public static Sprite lowerWedge() {
        return new Sprite(wedge(true), SIZE, SIZE);
    }

    public static Sprite upperWedge() {
        return new Sprite(wedge(false), SIZE, SIZE);
    }

    /** A two-cell layer: a solid tile, then a hole beside it. */
    public static TiledLayer ground() {
        TiledLayer layer = new TiledLayer(2, 1, tiles(), SIZE, SIZE);
        layer.setCell(0, 0, 1);
        layer.setCell(1, 0, 0);
        return layer;
    }

    // --------------------------------------------------- what the test asks

    /**
     * Two wedges that meet only where both are transparent.
     *
     * <p>The lower wedge is drawn where {@code x <= y}, the upper where
     * {@code x >= y}. Slide the upper one right by anything at all and there
     * is no pixel where both are drawn — while their boxes go on overlapping
     * for another seven columns. That gap between the two answers is the
     * whole of what {@code pixelLevel} means.</p>
     */
    public String wedges(int shift, boolean pixelLevel) {
        Sprite lower = lowerWedge();
        Sprite upper = upperWedge();
        lower.setPosition(0, 0);
        upper.setPosition(shift, 0);
        return String.valueOf(lower.collidesWith(upper, pixelLevel));
    }

    /** The same wedge twice: overlapping boxes, and overlapping pixels too. */
    public String twins(int shift, boolean pixelLevel) {
        Sprite one = lowerWedge();
        Sprite two = lowerWedge();
        one.setPosition(0, 0);
        two.setPosition(shift, 0);
        return String.valueOf(one.collidesWith(two, pixelLevel));
    }

    /** A wedge against a bare image, which carries no transform of its own. */
    public String onImage(int shift, boolean pixelLevel) {
        Sprite lower = lowerWedge();
        lower.setPosition(0, 0);
        return String.valueOf(lower.collidesWith(wedge(false), shift, 0, pixelLevel));
    }

    /**
     * A wedge against the layer: one solid cell, then a hole.
     *
     * <p>The hole is the point. A layer is mostly holes, and an emulator that
     * treats the layer's whole rectangle as solid turns the empty half of
     * every level into a wall.</p>
     */
    public String onTiles(int x, boolean pixelLevel) {
        Sprite lower = lowerWedge();
        lower.setPosition(x, 0);
        TiledLayer layer = ground();
        layer.setPosition(0, 0);
        return String.valueOf(lower.collidesWith(layer, pixelLevel));
    }

    /** The same overlap, after the sprite says a smaller part of it counts. */
    public String narrowed(int shift, boolean pixelLevel) {
        Sprite lower = lowerWedge();
        Sprite upper = upperWedge();
        lower.setPosition(0, 0);
        upper.setPosition(shift, 0);
        // Only the left two columns of the lower wedge can be hit.
        lower.defineCollisionRectangle(0, 0, 2, SIZE);
        return String.valueOf(lower.collidesWith(upper, pixelLevel));
    }

    private static final class Board extends Canvas {

        /** How many screen pixels one sprite pixel is blown up to. */
        private static final int ZOOM = 12;

        protected void paint(Graphics g) {
            g.setColor(0x101820);
            g.fillRect(0, 0, getWidth(), getHeight());

            // The sprites at their real size, where the emulator draws them.
            Sprite lower = lowerWedge();
            Sprite upper = upperWedge();
            lower.setPosition(12, 16);
            upper.setPosition(15, 16);
            lower.paint(g);
            upper.paint(g);

            g.setColor(0xC8D2DC);
            g.drawString("Hộp bao chồng nhau", 12, 34, Graphics.TOP | Graphics.LEFT);
            g.drawString("Điểm ảnh thì không", 12, 50, Graphics.TOP | Graphics.LEFT);

            // And blown up, because eight pixels is not something anyone can
            // judge a collision by.
            int left = 12;
            int top = 76;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    boolean inLower = x <= y;
                    boolean inUpper = x >= y + 3;
                    if (!inLower && !inUpper) {
                        continue;
                    }
                    g.setColor(inLower ? 0xE2574C : 0x4C9BE2);
                    g.fillRect(left + (inUpper ? (x + 3) : x) * ZOOM, top + y * ZOOM,
                            ZOOM - 1, ZOOM - 1);
                }
            }
            // The columns where the two boxes overlap, marked out underneath.
            g.setColor(0xF5C542);
            g.drawRect(left + 3 * ZOOM, top - 3, 5 * ZOOM, SIZE * ZOOM + 6);
            g.drawString("hai hộp chồng nhau ở đây",
                    12, top + SIZE * ZOOM + 12, Graphics.TOP | Graphics.LEFT);
        }
    }
}
