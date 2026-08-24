package javax.microedition.lcdui;

/** Compile-time stub; the emulator implements this natively. */
public abstract class Item {

    public static final int LAYOUT_DEFAULT = 0;
    public static final int LAYOUT_LEFT = 1;
    public static final int LAYOUT_RIGHT = 2;
    public static final int LAYOUT_CENTER = 3;
    public static final int LAYOUT_NEWLINE_BEFORE = 0x100;
    public static final int LAYOUT_NEWLINE_AFTER = 0x200;
    public static final int PLAIN = 0;
    public static final int HYPERLINK = 1;
    public static final int BUTTON = 2;

    protected Item() {
    }

    public String getLabel() {
        return null;
    }

    public void setLabel(String label) {
    }

    public void setLayout(int layout) {
    }

    public int getLayout() {
        return 0;
    }
}
