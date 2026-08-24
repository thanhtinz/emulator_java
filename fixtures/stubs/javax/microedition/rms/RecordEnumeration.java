package javax.microedition.rms;

/** Compile-time stub; the emulator implements this natively. */
public interface RecordEnumeration {

    int numRecords();

    boolean hasNextElement();

    int nextRecordId() throws InvalidRecordIDException;

    byte[] nextRecord() throws InvalidRecordIDException, RecordStoreException;

    void reset();

    void destroy();
}
