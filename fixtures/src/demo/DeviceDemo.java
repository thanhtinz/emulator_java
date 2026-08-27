package demo;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

/**
 * A game that asks the handset who it is, the way real ones did.
 *
 * <p>Half the library branched on {@code microedition.platform}: Nokia got the
 * full-screen canvas and the vendor drawing calls, everyone else got the safe
 * path, and a name nobody recognised got whichever branch was written last.
 * This draws the answers it is given, so a screenshot shows what the game
 * sees rather than what the settings screen claims.</p>
 */
public final class DeviceDemo extends MIDlet {

    protected void startApp() {
        Display.getDisplay(this).setCurrent(new Scene());
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    static final class Scene extends Canvas {

        private static final String[] ASKED = {
            "microedition.platform",
            "microedition.configuration",
            "microedition.profiles",
            "microedition.encoding",
            "microedition.locale",
        };

        protected void paint(Graphics g) {
            int width = getWidth();
            int height = getHeight();
            g.setColor(0x101820);
            g.fillRect(0, 0, width, height);

            String platform = System.getProperty("microedition.platform");
            // Đúng câu hỏi mà game đời ấy hỏi, và đúng cách nó hỏi.
            boolean nokia = platform != null && platform.startsWith("Nokia");

            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
            g.setColor(nokia ? 0x7FD962 : 0xE0E0E0);
            g.drawString(nokia ? "Bản cho máy Nokia" : "Bản chung", 6, 4, Graphics.LEFT | Graphics.TOP);

            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            int line = 4 + Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,
                    Font.SIZE_MEDIUM).getHeight() + 6;
            int step = g.getFont().getHeight() + 2;
            for (int i = 0; i < ASKED.length; i++) {
                String value = System.getProperty(ASKED[i]);
                g.setColor(0x8B98A8);
                g.drawString(shortName(ASKED[i]), 6, line, Graphics.LEFT | Graphics.TOP);
                g.setColor(value == null ? 0xC0342B : 0xFFFFFF);
                g.drawString(value == null ? "không có" : value, 6, line + step,
                        Graphics.LEFT | Graphics.TOP);
                line += step * 2 + 4;
            }
        }

        /** Chỉ phần sau dấu chấm cuối, để một dòng còn vừa màn hình. */
        private String shortName(String property) {
            int cut = property.lastIndexOf('.');
            return cut < 0 ? property : property.substring(cut + 1);
        }
    }
}
