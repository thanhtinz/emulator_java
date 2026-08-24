package javax.microedition.rms;

/** Compile-time stub; the emulator implements this natively. */
public class RecordStore {

    public static RecordStore openRecordStore(String name, boolean createIfNecessary)
            throws RecordStoreException {
        return null;
    }

    public static void deleteRecordStore(String name) throws RecordStoreException {
    }

    public static String[] listRecordStores() {
        return null;
    }

    public void closeRecordStore() throws RecordStoreException {
    }

    public String getName() throws RecordStoreException {
        return null;
    }

    public int getVersion() throws RecordStoreException {
        return 0;
    }

    public long getLastModified() throws RecordStoreException {
        return 0;
    }

    public int getNumRecords() throws RecordStoreException {
        return 0;
    }

    public int getSize() throws RecordStoreException {
        return 0;
    }

    public int getSizeAvailable() throws RecordStoreException {
        return 0;
    }

    public int getNextRecordID() throws RecordStoreException {
        return 0;
    }

    public int addRecord(byte[] data, int offset, int length) throws RecordStoreException {
        return 0;
    }

    public void setRecord(int recordId, byte[] data, int offset, int length)
            throws RecordStoreException {
    }

    public byte[] getRecord(int recordId) throws RecordStoreException {
        return null;
    }

    public int getRecord(int recordId, byte[] buffer, int offset) throws RecordStoreException {
        return 0;
    }

    public int getRecordSize(int recordId) throws RecordStoreException {
        return 0;
    }

    public void deleteRecord(int recordId) throws RecordStoreException {
    }

    public RecordEnumeration enumerateRecords(RecordFilter filter, RecordComparator comparator,
                                              boolean keepUpdated) throws RecordStoreException {
        return null;
    }
}
