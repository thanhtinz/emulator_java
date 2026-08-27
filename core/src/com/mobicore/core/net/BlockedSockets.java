package com.mobicore.core.net;

import java.io.IOException;

/**
 * The socket transport in place when nobody installed one.
 *
 * <p>Refusing by default matters: a game that opens a socket the moment it
 * starts must not reach the network merely because the host application forgot
 * to make a choice. The message names the reason so that the failure reads as
 * a decision rather than a defect.</p>
 */
public final class BlockedSockets implements SocketTransport {

    private static final String REASON = "MobiCore is not carrying socket connections";

    public Stream connect(String host, int port, int timeoutMs) throws IOException {
        throw new IOException(REASON);
    }

    public Server listen(int port) throws IOException {
        throw new IOException(REASON);
    }

    public Datagrams bind(int port) throws IOException {
        throw new IOException(REASON);
    }
}
