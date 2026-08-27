package javax.microedition.io;

import java.io.IOException;

/** Compile-time stub; the emulator implements this natively. */
public interface UDPDatagramConnection extends DatagramConnection {

    String getLocalAddress() throws IOException;

    int getLocalPort() throws IOException;
}
