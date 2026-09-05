package javax.microedition.rms;

/** Compile-time stub; the emulator implements this natively. */
public interface RecordListener {

    void recordAdded(RecordStore store, int recordId);

    void recordChanged(RecordStore store, int recordId);

    void recordDeleted(RecordStore store, int recordId);
}
