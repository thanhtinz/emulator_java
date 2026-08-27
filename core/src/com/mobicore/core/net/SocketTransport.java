package com.mobicore.core.net;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Carries a raw connection — one that stays open — out to the world.
 *
 * <p>{@link NetworkTransport} models a request and an answer, which is all
 * {@code http} ever needs. A socket is not that shape: it opens, both sides
 * talk for as long as they like, and either may close it. Multiplayer games,
 * chat rooms and high-score servers of that era all speak over such a socket,
 * so the framework needs a second shape rather than a stretched first one.</p>
 *
 * <p>Everything still goes through an interface so that the policy layer stays
 * in front of it, so that a test can run a whole conversation without a
 * network card, and so that Android and iOS can each plug in their own.</p>
 */
public interface SocketTransport {

    /** One open connection, from whichever end opened it. */
    interface Stream extends Closeable {

        InputStream input() throws IOException;

        OutputStream output() throws IOException;

        /** Address of the far end, as the game would print it. */
        String remoteAddress();

        int remotePort();

        String localAddress();

        int localPort();

        void close() throws IOException;
    }

    /** A port this device listens on; games use it for peer-to-peer play. */
    interface Server extends Closeable {

        /** Blocks until somebody connects. */
        Stream accept() throws IOException;

        String localAddress();

        int localPort();

        void close() throws IOException;
    }

    /** One packet as it arrived: the bytes, and who sent them. */
    final class Packet {

        public final String host;
        public final int port;
        public final int length;

        public Packet(String host, int port, int length) {
            this.host = host == null ? "" : host;
            this.port = port;
            this.length = length;
        }
    }

    /** A bound UDP port. Packets may arrive late, twice, or never. */
    interface Datagrams extends Closeable {

        void send(String host, int port, byte[] data, int offset, int length) throws IOException;

        /** Blocks until a packet arrives; fills {@code buffer} and says who sent it. */
        Packet receive(byte[] buffer, int offset, int length) throws IOException;

        String localAddress();

        int localPort();

        void close() throws IOException;
    }

    /**
     * Opens a connection to another machine.
     *
     * @param timeoutMs how long to wait for the far end to answer
     */
    Stream connect(String host, int port, int timeoutMs) throws IOException;

    /**
     * Listens on a port.
     *
     * @param port the port to take, or 0 to be given a free one
     */
    Server listen(int port) throws IOException;

    /**
     * Binds a UDP port.
     *
     * @param port the port to take, or 0 to be given a free one
     */
    Datagrams bind(int port) throws IOException;
}
