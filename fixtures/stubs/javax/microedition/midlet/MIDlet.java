package javax.microedition.midlet;

/**
 * Compile-time stub. The emulator implements this class natively; these
 * sources exist only so the fixture MIDlets can be compiled with a plain JDK.
 */
public abstract class MIDlet {

    protected MIDlet() {
    }

    protected abstract void startApp() throws MIDletStateChangeException;

    protected abstract void pauseApp();

    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    public final String getAppProperty(String key) {
        return null;
    }

    public final void notifyDestroyed() {
    }

    public final void notifyPaused() {
    }

    public final void resumeRequest() {
    }

    public final boolean platformRequest(String url) {
        return false;
    }
}
