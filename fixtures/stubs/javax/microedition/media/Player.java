package javax.microedition.media;

public class Player implements Controllable {

    public static final int UNREALIZED = 100;
    public static final int REALIZED = 200;
    public static final int PREFETCHED = 300;
    public static final int STARTED = 400;
    public static final int CLOSED = 0;

    public void realize() throws MediaException {
    }

    public void prefetch() throws MediaException {
    }

    public void start() throws MediaException {
    }

    public void stop() throws MediaException {
    }

    public void deallocate() {
    }

    public void close() {
    }

    public int getState() {
        return UNREALIZED;
    }

    public void setLoopCount(int count) {
    }

    public long getDuration() {
        return -1L;
    }

    public long getMediaTime() {
        return 0L;
    }

    public long setMediaTime(long now) throws MediaException {
        return 0L;
    }

    public String getContentType() {
        return null;
    }

    public void addPlayerListener(PlayerListener listener) {
    }

    public void removePlayerListener(PlayerListener listener) {
    }

    public Control getControl(String type) {
        return null;
    }

    public Control[] getControls() {
        return null;
    }
}
