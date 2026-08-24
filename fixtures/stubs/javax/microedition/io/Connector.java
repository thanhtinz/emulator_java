package javax.microedition.io;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Compile-time stub; the emulator implements this natively. */
public class Connector {

    public static final int READ = 1;
    public static final int WRITE = 2;
    public static final int READ_WRITE = 3;

    public static Connection open(String url) throws IOException {
        return null;
    }

    public static Connection open(String url, int mode) throws IOException {
        return null;
    }

    public static Connection open(String url, int mode, boolean timeouts) throws IOException {
        return null;
    }

    public static InputStream openInputStream(String url) throws IOException {
        return null;
    }

    public static DataInputStream openDataInputStream(String url) throws IOException {
        return null;
    }
}
