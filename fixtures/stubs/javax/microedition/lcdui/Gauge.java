package javax.microedition.lcdui;

/** Compile-time stub; the emulator implements this natively. */
public class Gauge extends Item {

    public static final int INDEFINITE = -1;
    public static final int CONTINUOUS_IDLE = 0;
    public static final int INCREMENTAL_IDLE = 1;
    public static final int CONTINUOUS_RUNNING = 2;
    public static final int INCREMENTAL_UPDATING = 3;

    public Gauge(String label, boolean interactive, int maxValue, int initialValue) {
    }

    public int getValue() {
        return 0;
    }

    public void setValue(int value) {
    }

    public int getMaxValue() {
        return 0;
    }

    public void setMaxValue(int maxValue) {
    }

    public boolean isInteractive() {
        return false;
    }
}
