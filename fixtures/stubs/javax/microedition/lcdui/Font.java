package javax.microedition.lcdui;

/** Compile-time stub; the emulator implements this natively. */
public class Font {

    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;
    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;

    public int getHeight() {
        return 0;
    }

    public int getBaselinePosition() {
        return 0;
    }

    public int stringWidth(String text) {
        return 0;
    }

    public int substringWidth(String text, int offset, int length) {
        return 0;
    }

    public int charWidth(char value) {
        return 0;
    }

    public int charsWidth(char[] data, int offset, int length) {
        return 0;
    }

    public int getFace() {
        return 0;
    }

    public int getStyle() {
        return 0;
    }

    public int getSize() {
        return 0;
    }

    public boolean isBold() {
        return false;
    }

    public boolean isItalic() {
        return false;
    }

    public boolean isUnderlined() {
        return false;
    }

    public static Font getDefaultFont() {
        return null;
    }

    public static Font getFont(int face, int style, int size) {
        return null;
    }

    public static Font getFont(int specifier) {
        return null;
    }
}
