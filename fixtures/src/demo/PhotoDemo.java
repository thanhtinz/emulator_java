package demo;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;

import java.io.IOException;

/**
 * A game showing the picture it shipped with — a JPEG, as they did.
 *
 * <p>MIDP only ever required PNG, so plenty of emulators read only PNG. Real
 * handsets read JPEG too, and games knew it: title art, backgrounds and
 * character portraits — the big, many-coloured things — were packed as JPEG
 * because it is a fraction of the size.</p>
 */
public final class PhotoDemo extends MIDlet {

    protected void startApp() {
        Display.getDisplay(this).setCurrent(new Scene());
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    static final class Scene extends Canvas {

        private Image photo;
        private String failure;

        Scene() {
            try {
                photo = Image.createImage("/res/photo.jpg");
            } catch (IOException e) {
                failure = e.getMessage();
            }
        }

        protected void paint(Graphics g) {
            int width = getWidth();
            int height = getHeight();
            g.setColor(0x101820);
            g.fillRect(0, 0, width, height);

            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
            g.setColor(0xFFFFFF);
            g.drawString("Ảnh JPEG trong game", width / 2, 4,
                    Graphics.HCENTER | Graphics.TOP);

            if (photo == null) {
                g.setColor(0xFF6060);
                g.drawString(failure == null ? "Không đọc được ảnh" : failure,
                        width / 2, height / 2, Graphics.HCENTER | Graphics.TOP);
                return;
            }
            int top = 4 + g.getFont().getHeight() + 8;
            g.drawImage(photo, (width - photo.getWidth()) / 2, top,
                    Graphics.LEFT | Graphics.TOP);

            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            g.setColor(0x8B98A8);
            g.drawString(photo.getWidth() + "x" + photo.getHeight() + " điểm ảnh",
                    width / 2, top + photo.getHeight() + 6, Graphics.HCENTER | Graphics.TOP);
        }
    }
}
