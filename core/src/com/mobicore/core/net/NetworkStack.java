package com.mobicore.core.net;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Policy, monitoring and transport in front of every connection a game opens.
 *
 * <p>Nothing reaches the network without passing through here, which is what
 * makes the "warn before a game connects" rule enforceable rather than
 * advisory.</p>
 */
public final class NetworkStack {

    /** Asked when the policy cannot decide on its own. */
    public interface PermissionPrompt {

        /**
         * @return true to allow this host; the answer is remembered
         */
        boolean allowHost(String host, String url);
    }

    private final NetworkPolicy policy;
    private final NetworkMonitor monitor = new NetworkMonitor();
    private NetworkTransport transport = new BlockedTransport();
    private SocketTransport sockets = new BlockedSockets();
    private final List<Closeable> open = new ArrayList<Closeable>();
    private PermissionPrompt prompt;
    private Clock clock = new Clock() {
        public long now() {
            return System.currentTimeMillis();
        }
    };

    /** Injectable so tests and screenshots are reproducible. */
    public interface Clock {
        long now();
    }

    public NetworkStack(NetworkPolicy policy) {
        this.policy = policy == null ? new NetworkPolicy() : policy;
    }

    public NetworkPolicy policy() {
        return policy;
    }

    public NetworkMonitor monitor() {
        return monitor;
    }

    public NetworkTransport transport() {
        return transport;
    }

    public void setTransport(NetworkTransport transport) {
        this.transport = transport == null ? new BlockedTransport() : transport;
    }

    public SocketTransport sockets() {
        return sockets;
    }

    public void setSocketTransport(SocketTransport sockets) {
        this.sockets = sockets == null ? new BlockedSockets() : sockets;
    }

    public void setPrompt(PermissionPrompt prompt) {
        this.prompt = prompt;
    }

    public void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    /**
     * Runs a request through the policy, records it, and returns the response.
     *
     * @throws IOException when the host is refused or the transport fails
     */
    public NetworkTransport.Response perform(NetworkTransport.Request request) throws IOException {
        NetworkMonitor.Exchange exchange = monitor.begin(request.url, request.method, clock.now());
        for (Map.Entry<String, String> header : request.headers.entrySet()) {
            exchange.addRequestHeader(header.getKey(), header.getValue());
        }
        exchange.recordRequestBody(request.body, policy.logBodies(), policy.maxBodyBytes());

        String host = NetworkPolicy.hostOf(request.url);
        int decision = authorise(host, request.url);
        exchange.setDecision(decision);

        if (decision != NetworkPolicy.ALLOW) {
            exchange.complete(0, "blocked", clock.now());
            throw new IOException("MobiCore blocked a connection to " + host);
        }

        try {
            NetworkTransport.Response response = transport.execute(request);
            for (Map.Entry<String, String> header : response.headers.entrySet()) {
                exchange.addResponseHeader(header.getKey(), header.getValue());
            }
            exchange.recordResponseBody(response.body, policy.logBodies(), policy.maxBodyBytes());
            exchange.complete(response.status, "ok", clock.now());
            return response;
        } catch (IOException e) {
            exchange.complete(0, "failed: " + e.getMessage(), clock.now());
            throw e;
        }
    }

    /**
     * Runs one host past the policy, asking the player when it cannot decide.
     */
    private int authorise(String host, String url) {
        int decision = policy.decideHost(host);
        if (decision != NetworkPolicy.ASK) {
            return decision;
        }
        if (prompt == null) {
            // Nobody can answer right now. Refuse this attempt, but do not
            // record a decision: the user has not said no, and a game must
            // not be permanently cut off because a prompt was unavailable.
            return NetworkPolicy.DENY;
        }
        if (prompt.allowHost(host, url)) {
            policy.allowHost(host);
            return NetworkPolicy.ALLOW;
        }
        policy.denyHost(host);
        return NetworkPolicy.DENY;
    }

    /**
     * Opens a raw connection to another machine.
     *
     * <p>The same door as {@link #perform}: the policy decides, the monitor
     * records. What differs is that the recording stays open — a socket has no
     * final byte count until somebody hangs up — so the returned connection
     * counts what passes through it and closes the record on the way out.</p>
     */
    /**
     * Cổng phải là một con số cổng thật.
     *
     * <p>Game đọc số cổng từ đâu đó — người chơi gõ vào, máy chủ gửi về, một
     * dòng trong tệp cấu hình — nên một con số vô lý là chuyện thường. Không
     * kiểm ở đây thì {@code java.net} ném ra một lỗi không ai bắt được, và
     * game chết hẳn thay vì nhận về một {@code IOException} mà nó đã viết sẵn
     * chỗ để bắt.</p>
     *
     * @param allowAny cho phép cổng 0, nghĩa là "máy tự chọn cổng nào cũng được"
     */
    private static void checkPort(int port, boolean allowAny) throws IOException {
        int lowest = allowAny ? 0 : 1;
        if (port < lowest || port > 65535) {
            throw new IOException("Số cổng không hợp lệ: " + port);
        }
    }

    public SocketTransport.Stream openSocket(String host, int port, int timeoutMs)
            throws IOException {
        checkPort(port, false);
        String url = "socket://" + host + ":" + port;
        NetworkMonitor.Exchange exchange = monitor.begin(url, "SOCKET", clock.now());
        int decision = authorise(NetworkPolicy.hostOf(url), url);
        exchange.setDecision(decision);
        if (decision != NetworkPolicy.ALLOW) {
            exchange.complete(0, "blocked", clock.now());
            throw new IOException("MobiCore blocked a connection to " + host);
        }
        try {
            return new CountedStream(sockets.connect(host, port, timeoutMs), exchange);
        } catch (IOException e) {
            exchange.complete(0, "failed: " + e.getMessage(), clock.now());
            throw e;
        }
    }

    /**
     * Takes a port on this device so other machines can connect to the game.
     */
    public SocketTransport.Server openServer(int port) throws IOException {
        // Cổng 0 hợp lệ ở đây: đó là cách nói "cổng nào trống cũng được".
        checkPort(port, true);
        String url = "socket://:" + port;
        NetworkMonitor.Exchange exchange = monitor.begin(url, "LISTEN", clock.now());
        int decision = authorise(NetworkPolicy.THIS_DEVICE, url);
        exchange.setDecision(decision);
        if (decision != NetworkPolicy.ALLOW) {
            exchange.complete(0, "blocked", clock.now());
            throw new IOException("MobiCore blocked the game from listening on port " + port);
        }
        try {
            SocketTransport.Server server = sockets.listen(port);
            exchange.complete(0, "listening", clock.now());
            return new CountedServer(server, this);
        } catch (IOException e) {
            exchange.complete(0, "failed: " + e.getMessage(), clock.now());
            throw e;
        }
    }

    /** Binds a UDP port for a game that speaks in packets rather than streams. */
    public SocketTransport.Datagrams openDatagrams(int port) throws IOException {
        checkPort(port, true);
        String url = "datagram://:" + port;
        NetworkMonitor.Exchange exchange = monitor.begin(url, "DATAGRAM", clock.now());
        int decision = authorise(NetworkPolicy.THIS_DEVICE, url);
        exchange.setDecision(decision);
        if (decision != NetworkPolicy.ALLOW) {
            exchange.complete(0, "blocked", clock.now());
            throw new IOException("MobiCore blocked the game from opening port " + port);
        }
        try {
            return new CountedDatagrams(sockets.bind(port), exchange, this);
        } catch (IOException e) {
            exchange.complete(0, "failed: " + e.getMessage(), clock.now());
            throw e;
        }
    }

    /** Records an accepted connection so an incoming peer shows up too. */
    NetworkMonitor.Exchange beginAccepted(String host, int port) {
        NetworkMonitor.Exchange exchange =
                monitor.begin("socket://" + host + ":" + port, "ACCEPT", clock.now());
        exchange.setDecision(NetworkPolicy.ALLOW);
        return exchange;
    }

    long now() {
        return clock.now();
    }

    synchronized void trackOpen(Closeable resource) {
        open.add(resource);
    }

    synchronized void untrack(Closeable resource) {
        open.remove(resource);
    }

    /**
     * Hangs up on everything the game left open.
     *
     * <p>A blocking read on a socket is the one place the emulator cannot
     * interrupt a game by counting its instructions: it is not executing any.
     * Closing the connection underneath it is what wakes it, so stopping a
     * game has to come through here or the stop button would wait on a server
     * that may never answer.</p>
     */
    public void closeAll() {
        List<Closeable> victims;
        synchronized (this) {
            victims = new ArrayList<Closeable>(open);
            open.clear();
        }
        for (Closeable resource : victims) {
            try {
                resource.close();
            } catch (IOException ignored) {
                // Shutting down; a connection that will not close politely is
                // still going away.
            }
        }
    }

    /** A connection that keeps the monitor's record up to date as it is used. */
    private final class CountedStream implements SocketTransport.Stream {

        private final SocketTransport.Stream delegate;
        private final NetworkMonitor.Exchange exchange;
        private InputStream in;
        private OutputStream out;
        private boolean closed;

        CountedStream(SocketTransport.Stream delegate, NetworkMonitor.Exchange exchange) {
            this.delegate = delegate;
            this.exchange = exchange;
            exchange.complete(0, "open", clock.now());
            trackOpen(this);
        }

        public InputStream input() throws IOException {
            if (in == null) {
                final InputStream source = delegate.input();
                in = new FilterInputStream(source) {
                    @Override
                    public int read() throws IOException {
                        int value = source.read();
                        if (value >= 0) {
                            byte[] one = new byte[]{(byte) value};
                            exchange.addResponseBytes(one, 0, 1,
                                    policy.logBodies(), policy.maxBodyBytes());
                        }
                        return value;
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        int count = source.read(buffer, offset, length);
                        if (count > 0) {
                            exchange.addResponseBytes(buffer, offset, count,
                                    policy.logBodies(), policy.maxBodyBytes());
                        }
                        return count;
                    }
                };
            }
            return in;
        }

        public OutputStream output() throws IOException {
            if (out == null) {
                final OutputStream sink = delegate.output();
                out = new FilterOutputStream(sink) {
                    @Override
                    public void write(int value) throws IOException {
                        sink.write(value);
                        byte[] one = new byte[]{(byte) value};
                        exchange.addRequestBytes(one, 0, 1,
                                policy.logBodies(), policy.maxBodyBytes());
                    }

                    @Override
                    public void write(byte[] buffer, int offset, int length) throws IOException {
                        // FilterOutputStream would otherwise write byte by
                        // byte, which on a socket means one packet each.
                        sink.write(buffer, offset, length);
                        exchange.addRequestBytes(buffer, offset, length,
                                policy.logBodies(), policy.maxBodyBytes());
                    }
                };
            }
            return out;
        }

        public String remoteAddress() {
            return delegate.remoteAddress();
        }

        public int remotePort() {
            return delegate.remotePort();
        }

        public String localAddress() {
            return delegate.localAddress();
        }

        public int localPort() {
            return delegate.localPort();
        }

        public void close() throws IOException {
            if (!closed) {
                closed = true;
                exchange.complete(0, "closed", clock.now());
                untrack(this);
            }
            delegate.close();
        }
    }

    /** A listening port whose accepted connections are recorded like any other. */
    private static final class CountedServer implements SocketTransport.Server {

        private final SocketTransport.Server delegate;
        private final NetworkStack stack;

        CountedServer(SocketTransport.Server delegate, NetworkStack stack) {
            this.delegate = delegate;
            this.stack = stack;
            stack.trackOpen(this);
        }

        public SocketTransport.Stream accept() throws IOException {
            SocketTransport.Stream accepted = delegate.accept();
            return stack.new CountedStream(accepted,
                    stack.beginAccepted(accepted.remoteAddress(), accepted.remotePort()));
        }

        public String localAddress() {
            return delegate.localAddress();
        }

        public int localPort() {
            return delegate.localPort();
        }

        public void close() throws IOException {
            stack.untrack(this);
            delegate.close();
        }
    }

    /** A bound UDP port that counts what it sends and receives. */
    private static final class CountedDatagrams implements SocketTransport.Datagrams {

        private final SocketTransport.Datagrams delegate;
        private final NetworkMonitor.Exchange exchange;
        private final NetworkStack stack;
        private boolean closed;

        CountedDatagrams(SocketTransport.Datagrams delegate, NetworkMonitor.Exchange exchange,
                         NetworkStack stack) {
            this.delegate = delegate;
            this.exchange = exchange;
            this.stack = stack;
            exchange.complete(0, "open", stack.now());
            stack.trackOpen(this);
        }

        public void send(String host, int port, byte[] data, int offset, int length)
                throws IOException {
            // Địa chỉ gói tin thường do game tự ghép từ thứ nó đọc được, nên
            // một số cổng vô lý là chuyện thường — và không kiểm thì
            // java.net ném ra một lỗi không ai bắt được.
            checkPort(port, false);
            delegate.send(host, port, data, offset, length);
            exchange.addRequestBytes(data, offset, length,
                    stack.policy.logBodies(), stack.policy.maxBodyBytes());
        }

        public SocketTransport.Packet receive(byte[] buffer, int offset, int length)
                throws IOException {
            SocketTransport.Packet packet = delegate.receive(buffer, offset, length);
            exchange.addResponseBytes(buffer, offset, packet.length,
                    stack.policy.logBodies(), stack.policy.maxBodyBytes());
            return packet;
        }

        public String localAddress() {
            return delegate.localAddress();
        }

        public int localPort() {
            return delegate.localPort();
        }

        public void close() throws IOException {
            if (!closed) {
                closed = true;
                exchange.complete(0, "closed", stack.now());
                stack.untrack(this);
            }
            delegate.close();
        }
    }
}
