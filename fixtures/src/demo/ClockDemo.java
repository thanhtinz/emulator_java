package demo;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

/**
 * A game that looks at the clock, the way daily-reward games do.
 *
 * <p>The shape is always the same: read today's date, compare it with the one
 * in the save, and hand out the reward if the day has turned over.</p>
 */
public final class ClockDemo extends MIDlet {

    protected void startApp() {
        Display.getDisplay(this).setCurrent(new Face());
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    /** Every field of one moment, so a wrong one shows up as a wrong word. */
    public String readAt(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTime(new Date(millis));
        return c.get(Calendar.YEAR) + "|" + c.get(Calendar.MONTH) + "|"
                + c.get(Calendar.DAY_OF_MONTH) + "|" + c.get(Calendar.DAY_OF_WEEK) + "|"
                + c.get(Calendar.DAY_OF_YEAR) + "|" + c.get(Calendar.HOUR_OF_DAY) + "|"
                + c.get(Calendar.HOUR) + "|" + c.get(Calendar.AM_PM) + "|"
                + c.get(Calendar.MINUTE) + "|" + c.get(Calendar.SECOND) + "|"
                + c.get(Calendar.MILLISECOND);
    }

    /** Sets one field and reads the moment back, overflow and all. */
    public long setAt(long millis, int field, int value) {
        Calendar c = Calendar.getInstance();
        c.setTime(new Date(millis));
        c.set(field, value);
        return c.getTime().getTime();
    }

    /** The zone the phone says it is in. */
    public String zone() {
        TimeZone z = TimeZone.getDefault();
        return z.getID() + "|" + z.getRawOffset() + "|" + z.useDaylightTime();
    }

    /** Counting days forward by adding to DATE, which is how games do it. */
    public String tomorrow(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTime(new Date(millis));
        c.set(Calendar.DAY_OF_MONTH, c.get(Calendar.DAY_OF_MONTH) + 1);
        return c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH) + 1)
                + "-" + c.get(Calendar.DAY_OF_MONTH);
    }

    /** Reading a text file out of the game's own jar, letter by letter. */
    public String readText(String path) throws Exception {
        java.io.InputStream in = getClass().getResourceAsStream(path);
        if (in == null) {
            return "<không có tệp>";
        }
        java.io.InputStreamReader reader = new java.io.InputStreamReader(in, "UTF-8");
        StringBuffer out = new StringBuffer();
        int ch;
        while ((ch = reader.read()) >= 0) {
            out.append((char) ch);
        }
        reader.close();
        return out.toString();
    }

    private final class Face extends Canvas {
        protected void paint(Graphics g) {
            g.setColor(0x101820);
            g.fillRect(0, 0, getWidth(), getHeight());
            Calendar c = Calendar.getInstance();
            String[] names = {"CN", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy"};
            String day = names[c.get(Calendar.DAY_OF_WEEK) - 1];
            String clock = two(c.get(Calendar.HOUR_OF_DAY)) + ":" + two(c.get(Calendar.MINUTE));
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
            g.setColor(0xF5C542);
            g.drawString(clock, getWidth() / 2, 60, Graphics.TOP | Graphics.HCENTER);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM));
            g.setColor(0xD8E0EA);
            g.drawString("Thứ " + day + ", " + c.get(Calendar.DAY_OF_MONTH) + "/"
                            + (c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.YEAR),
                    getWidth() / 2, 100, Graphics.TOP | Graphics.HCENTER);
            try {
                g.setColor(0x7FB2E5);
                g.drawString(readText("/message.txt"), getWidth() / 2, 140,
                        Graphics.TOP | Graphics.HCENTER);
            } catch (Exception ignored) {
            }
            g.setColor(0x5A6472);
            g.drawString("phần thưởng mỗi ngày", getWidth() / 2, 190,
                    Graphics.TOP | Graphics.HCENTER);
        }

        private String two(int value) {
            return value < 10 ? "0" + value : String.valueOf(value);
        }
    }
}
