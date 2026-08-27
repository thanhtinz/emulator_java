package javax.microedition.io;

import java.io.IOException;

/** Compile-time stub; the emulator implements this natively. */
public interface StreamConnectionNotifier extends Connection {

    StreamConnection acceptAndOpen() throws IOException;
}
