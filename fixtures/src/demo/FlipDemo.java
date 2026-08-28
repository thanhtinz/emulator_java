package demo;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.midlet.MIDlet;

/**
 * The same sprite drawn under all eight of MIDP's transforms.
 *
 * <p>An asymmetric figure — an arrow with a notch — because a symmetric one
 * hides exactly the mistake worth catching: mirroring and turning do not
 * commute, so a sprite mirrored-then-turned is not the one turned-then-
 * mirrored, and only a shape with no symmetry says which one you got.</p>
 */
public final class FlipDemo extends MIDlet {

    protected void startApp() {
        Display.getDisplay(this).setCurrent(new Wall());
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    /** A 12x8 arrow: points right, notched at the bottom-left. */
    private static Image figure() {
        Image image = Image.createImage(12, 8);
        Graphics g = image.getGraphics();
        g.setColor(0x101820);
        g.fillRect(0, 0, 12, 8);
        g.setColor(0xF5C542);
        g.fillRect(0, 3, 8, 2);
        g.fillTriangle(7, 0, 7, 7, 11, 4);
        g.setColor(0xE2574C);
        g.fillRect(0, 6, 3, 2);
        return image;
    }

    private static final int[] ORDER = {
            Sprite.TRANS_NONE, Sprite.TRANS_MIRROR, Sprite.TRANS_ROT90, Sprite.TRANS_ROT180,
            Sprite.TRANS_ROT270, Sprite.TRANS_MIRROR_ROT90, Sprite.TRANS_MIRROR_ROT180,
            Sprite.TRANS_MIRROR_ROT270,
    };

    private static final String[] NAMES = {
            "NONE", "MIRROR", "ROT90", "ROT180",
            "ROT270", "MIRROR_ROT90", "MIRROR_ROT180", "MIRROR_ROT270",
    };

    private final class Wall extends Canvas {
        protected void paint(Graphics g) {
            Image art = figure();
            g.setColor(0x101820);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
            g.setColor(0x7FB2E5);
            g.drawString("Tám phép lật xoay", getWidth() / 2, 6,
                    Graphics.TOP | Graphics.HCENTER);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            int cellWidth = getWidth() / 2;
            int cellHeight = 60;
            for (int i = 0; i < ORDER.length; i++) {
                int left = (i % 2) * cellWidth;
                int top = 28 + (i / 2) * cellHeight;
                g.setColor(0x1B2530);
                g.fillRect(left + 4, top, cellWidth - 8, cellHeight - 6);
                // Vẽ to lên gấp ba để nhìn rõ chỗ khuyết, bằng chính drawRegion.
                Image big = zoom(art, 3);
                g.drawRegion(big, 0, 0, big.getWidth(), big.getHeight(), ORDER[i],
                        left + cellWidth / 2, top + 8, Graphics.TOP | Graphics.HCENTER);
                g.setColor(0x9AA6B4);
                g.drawString(NAMES[i], left + cellWidth / 2, top + cellHeight - 20,
                        Graphics.TOP | Graphics.HCENTER);
            }
        }

        private Image zoom(Image source, int by) {
            int width = source.getWidth(), height = source.getHeight();
            int[] pixels = new int[width * height];
            source.getRGB(pixels, 0, width, 0, 0, width, height);
            int[] grown = new int[width * by * height * by];
            for (int y = 0; y < height * by; y++) {
                for (int x = 0; x < width * by; x++) {
                    grown[y * width * by + x] = pixels[(y / by) * width + (x / by)];
                }
            }
            return Image.createRGBImage(grown, width * by, height * by, false);
        }
    }
}
