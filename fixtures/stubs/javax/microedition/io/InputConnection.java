package javax.microedition.io;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Compile-time stub; the emulator implements this natively. */
public interface InputConnection extends Connection {

    InputStream openInputStream() throws IOException;

    DataInputStream openDataInputStream() throws IOException;
}
