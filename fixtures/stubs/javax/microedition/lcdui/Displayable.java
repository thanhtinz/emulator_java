package javax.microedition.lcdui;

/** Compile-time stub; the emulator implements this natively. */
public abstract class Displayable {

    protected Displayable() {
    }

    public int getWidth() {
        return 0;
    }

    public int getHeight() {
        return 0;
    }

    public void setTitle(String title) {
    }

    public String getTitle() {
        return null;
    }

    public void addCommand(Command command) {
    }

    public void removeCommand(Command command) {
    }

    public void setCommandListener(CommandListener listener) {
    }

    public boolean isShown() {
        return false;
    }
}
