package com.mobicore.core.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A whole conversation without a network card.
 *
 * <p>The second half of the local server bridge {@link LoopbackTransport}
 * started. A game whose multiplayer server shut down years ago can be answered
 * from the device instead, and a test can play a full exchange — connect,
 * write, read, hang up — deterministically.</p>
 *
 * <p>The pipes are real streams with real blocking reads, so the code under
 * test is the same code that runs against a real socket. Every blocking wait
 * has a deadline: an in-memory network that can deadlock would be worse than
 * none, because a hung test says nothing at all.</p>
 */
public final class LoopbackSockets implements SocketTransport {

    /** Serves one connection, from the far end. Runs on its own thread. */
    public interface Peer {
        void talk(Stream connection) throws IOException;
    }

    /** How long any blocking read or accept waits before giving up. */
    private static final long DEADLINE_MS = 10000;

    private final Map<Integer, Peer> peers = new HashMap<Integer, Peer>();
    private final Map<Integer, LoopServer> servers = new HashMap<Integer, LoopServer>();
    private final Map<Integer, LoopDatagrams> bound = new HashMap<Integer, LoopDatagrams>();
    private final List<String> dialled = new ArrayList<String>();
    private int nextEphemeral = 40000;

    /** Answers connections to a port with a scripted far end. */
    public synchronized LoopbackSockets serve(int port, Peer peer) {
        peers.put(Integer.valueOf(port), peer);
        return this;
    }

    /** A far end that sends back whatever it is given, until it is hung up on. */
    public static Peer echo() {
        return new Peer() {
            public void talk(Stream connection) throws IOException {
                InputStream in = connection.input();
                OutputStream out = connection.output();
                byte[] buffer = new byte[256];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
                connection.close();
            }
        };
    }

    /** Every address a game dialled, in order. */
    public synchronized List<String> dialled() {
        return new ArrayList<String>(dialled);
    }

    public Stream connect(String host, int port, int timeoutMs) throws IOException {
        Peer peer;
        LoopServer server;
        int localPort;
        synchronized (this) {
            dialled.add(host + ":" + port);
            peer = peers.get(Integer.valueOf(port));
            server = servers.get(Integer.valueOf(port));
            localPort = nextEphemeral++;
        }
        if (peer == null && server == null) {
            // Same failure a real stack gives for a port nobody is on. Saying
            // "refused" rather than hanging is what the game expects to hear.
            throw new IOException("Connection refused to " + host + ":" + port);
        }
        Pipe toServer = new Pipe();
        Pipe toClient = new Pipe();
        LoopStream client = new LoopStream(toClient, toServer, host, port, "127.0.0.1", localPort);
        LoopStream far = new LoopStream(toServer, toClient, "127.0.0.1", localPort, host, port);
        if (server != null) {
            server.offer(far);
        } else {
            runPeer(peer, far);
        }
        return client;
    }

    public synchronized Server listen(int port) throws IOException {
        int actual = port > 0 ? port : nextEphemeral++;
        Integer key = Integer.valueOf(actual);
        if (servers.containsKey(key)) {
            throw new IOException("Port " + actual + " is already taken");
        }
        LoopServer server = new LoopServer(actual);
        servers.put(key, server);
        return server;
    }

    public synchronized Datagrams bind(int port) throws IOException {
        int actual = port > 0 ? port : nextEphemeral++;
        Integer key = Integer.valueOf(actual);
        if (bound.containsKey(key)) {
            throw new IOException("Port " + actual + " is already taken");
        }
        LoopDatagrams datagrams = new LoopDatagrams(actual);
        bound.put(key, datagrams);
        return datagrams;
    }

    private static void runPeer(final Peer peer, final LoopStream far) {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    peer.talk(far);
                } catch (IOException e) {
                    // The far end gave up. Closing is what a real server does
                    // on the way out, and it wakes the game's blocked read.
                } finally {
                    far.closeQuietly();
                }
            }
        }, "mobicore-loopback-peer");
        thread.setDaemon(true);
        thread.start();
    }

    private synchronized void release(int port) {
        servers.remove(Integer.valueOf(port));
        bound.remove(Integer.valueOf(port));
    }

    private synchronized LoopDatagrams datagramsOn(int port) {
        return bound.get(Integer.valueOf(port));
    }

    /** One direction of a connection: bytes in at one end, out at the other. */
    private static final class Pipe {

        private byte[] data = new byte[256];
        private int head;
        private int tail;
        private boolean closed;

        synchronized void write(byte[] source, int offset, int length) throws IOException {
            if (closed) {
                throw new IOException("The connection is closed");
            }
            if (tail + length > data.length) {
                compact(length);
            }
            System.arraycopy(source, offset, data, tail, length);
            tail += length;
            notifyAll();
        }

        private void compact(int wanted) {
            int size = tail - head;
            if (size + wanted > data.length) {
                byte[] grown = new byte[Math.max(data.length * 2, size + wanted)];
                System.arraycopy(data, head, grown, 0, size);
                data = grown;
            } else {
                System.arraycopy(data, head, data, 0, size);
            }
            head = 0;
            tail = size;
        }

        synchronized int read(byte[] into, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            long deadline = System.currentTimeMillis() + DEADLINE_MS;
            while (head == tail) {
                if (closed) {
                    return -1;
                }
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    throw new IOException("Timed out waiting for the other end");
                }
                try {
                    wait(Math.min(left, 50));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while reading");
                }
            }
            int count = Math.min(length, tail - head);
            System.arraycopy(data, head, into, offset, count);
            head += count;
            return count;
        }

        synchronized int available() {
            return tail - head;
        }

        synchronized void close() {
            closed = true;
            notifyAll();
        }
    }

    private static final class PipeInput extends InputStream {

        private final Pipe pipe;

        PipeInput(Pipe pipe) {
            this.pipe = pipe;
        }

        public int read() throws IOException {
            byte[] one = new byte[1];
            int count = pipe.read(one, 0, 1);
            return count < 0 ? -1 : one[0] & 0xFF;
        }

        public int read(byte[] into, int offset, int length) throws IOException {
            return pipe.read(into, offset, length);
        }

        public int available() {
            return pipe.available();
        }

        public void close() {
            pipe.close();
        }
    }

    private static final class PipeOutput extends OutputStream {

        private final Pipe pipe;

        PipeOutput(Pipe pipe) {
            this.pipe = pipe;
        }

        public void write(int value) throws IOException {
            pipe.write(new byte[]{(byte) value}, 0, 1);
        }

        public void write(byte[] source, int offset, int length) throws IOException {
            pipe.write(source, offset, length);
        }

        public void close() {
            pipe.close();
        }
    }

    private static final class LoopStream implements Stream {

        private final Pipe incoming;
        private final Pipe outgoing;
        private final String remoteAddress;
        private final int remotePort;
        private final String localAddress;
        private final int localPort;
        private final PipeInput in;
        private final PipeOutput out;

        LoopStream(Pipe incoming, Pipe outgoing, String remoteAddress, int remotePort,
                   String localAddress, int localPort) {
            this.incoming = incoming;
            this.outgoing = outgoing;
            this.remoteAddress = remoteAddress;
            this.remotePort = remotePort;
            this.localAddress = localAddress;
            this.localPort = localPort;
            this.in = new PipeInput(incoming);
            this.out = new PipeOutput(outgoing);
        }

        public InputStream input() {
            return in;
        }

        public OutputStream output() {
            return out;
        }

        public String remoteAddress() {
            return remoteAddress;
        }

        public int remotePort() {
            return remotePort;
        }

        public String localAddress() {
            return localAddress;
        }

        public int localPort() {
            return localPort;
        }

        public void close() {
            closeQuietly();
        }

        void closeQuietly() {
            incoming.close();
            outgoing.close();
        }
    }

    private final class LoopServer implements Server {

        private final int port;
        private final List<Stream> waiting = new ArrayList<Stream>();
        private boolean closed;

        LoopServer(int port) {
            this.port = port;
        }

        synchronized void offer(Stream connection) {
            waiting.add(connection);
            notifyAll();
        }

        public synchronized Stream accept() throws IOException {
            long deadline = System.currentTimeMillis() + DEADLINE_MS;
            while (waiting.isEmpty()) {
                if (closed) {
                    throw new IOException("The listening port is closed");
                }
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    throw new IOException("Nobody connected");
                }
                try {
                    wait(Math.min(left, 50));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for a connection");
                }
            }
            return waiting.remove(0);
        }

        public String localAddress() {
            return "127.0.0.1";
        }

        public int localPort() {
            return port;
        }

        public synchronized void close() {
            closed = true;
            notifyAll();
            release(port);
        }
    }

    private final class LoopDatagrams implements Datagrams {

        private final int port;
        private final List<Object[]> inbox = new ArrayList<Object[]>();
        private boolean closed;

        LoopDatagrams(int port) {
            this.port = port;
        }

        synchronized void deliver(String fromHost, int fromPort, byte[] payload) {
            inbox.add(new Object[]{fromHost, Integer.valueOf(fromPort), payload});
            notifyAll();
        }

        public void send(String host, int target, byte[] data, int offset, int length)
                throws IOException {
            if (closed) {
                throw new IOException("The port is closed");
            }
            byte[] payload = new byte[length];
            System.arraycopy(data, offset, payload, 0, length);
            LoopDatagrams receiver = datagramsOn(target);
            if (receiver != null) {
                receiver.deliver("127.0.0.1", port, payload);
            }
            // A packet nobody is listening for is dropped, exactly as UDP
            // drops it. Raising here would teach a game the wrong lesson.
        }

        public synchronized Packet receive(byte[] buffer, int offset, int length)
                throws IOException {
            long deadline = System.currentTimeMillis() + DEADLINE_MS;
            while (inbox.isEmpty()) {
                if (closed) {
                    throw new IOException("The port is closed");
                }
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    throw new IOException("No packet arrived");
                }
                try {
                    wait(Math.min(left, 50));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for a packet");
                }
            }
            Object[] packet = inbox.remove(0);
            byte[] payload = (byte[]) packet[2];
            int count = Math.min(length, payload.length);
            System.arraycopy(payload, 0, buffer, offset, count);
            return new Packet((String) packet[0], ((Integer) packet[1]).intValue(), count);
        }

        public String localAddress() {
            return "127.0.0.1";
        }

        public int localPort() {
            return port;
        }

        public synchronized void close() {
            closed = true;
            notifyAll();
            release(port);
        }
    }
}
