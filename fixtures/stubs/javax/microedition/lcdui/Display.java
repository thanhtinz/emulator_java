package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

/** Compile-time stub; the emulator implements this natively. */
public class Display {

    public static Display getDisplay(MIDlet midlet) {
        return null;
    }

    public void setCurrent(Displayable next) {
    }

    public void setCurrent(Alert alert, Displayable next) {
    }

    public void setCurrentItem(Item item) {
    }

    public Displayable getCurrent() {
        return null;
    }

    public void callSerially(Runnable runnable) {
    }

    public boolean isColor() {
        return true;
    }

    public int numColors() {
        return 0;
    }

    public int numAlphaLevels() {
        return 0;
    }

    public boolean flashBacklight(int millis) {
        return false;
    }

    public boolean vibrate(int millis) {
        return false;
    }
}
