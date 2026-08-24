package javax.microedition.media;

public interface PlayerListener {
    String STARTED = "started";
    String STOPPED = "stopped";
    String END_OF_MEDIA = "endOfMedia";
    String CLOSED = "closed";

    void playerUpdate(Player player, String event, Object eventData);
}
