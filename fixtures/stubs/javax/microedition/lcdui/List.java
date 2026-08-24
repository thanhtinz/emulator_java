package javax.microedition.lcdui;

/** Compile-time stub; the emulator implements this natively. */
public class List extends Screen implements Choice {

    public static final Command SELECT_COMMAND = null;

    public List(String title, int listType) {
    }

    public List(String title, int listType, String[] elements, Image[] images) {
    }

    public int append(String text, Image image) {
        return 0;
    }

    public void insert(int index, String text, Image image) {
    }

    public void set(int index, String text, Image image) {
    }

    public void delete(int index) {
    }

    public void deleteAll() {
    }

    public int size() {
        return 0;
    }

    public String getString(int index) {
        return null;
    }

    public Image getImage(int index) {
        return null;
    }

    public int getSelectedIndex() {
        return 0;
    }

    public void setSelectedIndex(int index, boolean selected) {
    }

    public boolean isSelected(int index) {
        return false;
    }

    public int getSelectedFlags(boolean[] flags) {
        return 0;
    }

    public void setSelectedFlags(boolean[] flags) {
    }

    public void setSelectCommand(Command command) {
    }

    public void setFitPolicy(int policy) {
    }

    public int getFitPolicy() {
        return 0;
    }
}
