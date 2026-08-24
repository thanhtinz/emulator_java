package javax.microedition.rms;

/** Compile-time stub; the emulator implements this natively. */
public interface RecordComparator {

    int PRECEDES = -1;
    int EQUIVALENT = 0;
    int FOLLOWS = 1;

    int compare(byte[] left, byte[] right);
}
