package javax.microedition.rms;

/** Compile-time stub; the emulator implements this natively. */
public interface RecordEnumeration {

    int numRecords();

    boolean hasNextElement();

    int nextRecordId() throws InvalidRecordIDException;

    byte[] nextRecord() throws InvalidRecordIDException, RecordStoreException;

    boolean hasPreviousElement();

    int previousRecordId() throws InvalidRecordIDException;

    byte[] previousRecord() throws InvalidRecordIDException, RecordStoreException;

    int getRecordId(int index) throws IllegalArgumentException;

    void rebuild();

    void keepUpdated(boolean keepUpdated);

    boolean isKeptUpdated();

    void reset();

    void destroy();
}
