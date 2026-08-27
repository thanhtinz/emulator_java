package javax.microedition.io;

import java.io.IOException;

/** Compile-time stub; the emulator implements this natively. */
public interface ServerSocketConnection extends StreamConnectionNotifier {

    String getLocalAddress() throws IOException;

    int getLocalPort() throws IOException;
}
