package com.mobicore.core.net;

import java.io.IOException;

/**
 * Refuses everything.
 *
 * <p>The default on a fresh install: a game must not reach the network until
 * the user has said so for that game.</p>
 */
public final class BlockedTransport implements NetworkTransport {

    @Override
    public Response execute(Request request) throws IOException {
        throw new IOException("Network access is blocked for this game");
    }
}
