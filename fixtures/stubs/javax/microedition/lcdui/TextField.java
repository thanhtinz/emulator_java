package javax.microedition.lcdui;

/** Compile-time stub; the emulator implements this natively. */
public class TextField extends Item {

    public static final int ANY = 0;
    public static final int EMAILADDR = 1;
    public static final int NUMERIC = 2;
    public static final int PHONENUMBER = 3;
    public static final int URL = 4;
    public static final int DECIMAL = 5;
    public static final int PASSWORD = 0x10000;
    public static final int UNEDITABLE = 0x20000;
    public static final int CONSTRAINT_MASK = 0xFFFF;

    public TextField(String label, String text, int maxSize, int constraints) {
    }

    public String getString() {
        return null;
    }

    public void setString(String text) {
    }

    public void insert(String text, int position) {
    }

    public void delete(int offset, int length) {
    }

    public int size() {
        return 0;
    }

    public int getMaxSize() {
        return 0;
    }

    public int setMaxSize(int maxSize) {
        return 0;
    }

    public int getConstraints() {
        return 0;
    }

    public void setConstraints(int constraints) {
    }

    public int getCaretPosition() {
        return 0;
    }

    public int getChars(char[] data) {
        return 0;
    }
}
