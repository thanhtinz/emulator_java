package com.mobicore.core.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Real sockets, over the platform's own stack.
 *
 * <p>{@code java.net.Socket} exists on Android and is mapped onto BSD sockets
 * by J2ObjC, so one implementation serves both mobile targets — the same
 * bargain {@link HttpTransport} makes.</p>
 */
public final class RealSockets implements SocketTransport {

    public Stream connect(String host, int port, int timeoutMs) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), Math.max(0, timeoutMs));
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
        return new SocketStream(socket);
    }

    public Server listen(int port) throws IOException {
        return new SocketServer(new ServerSocket(port));
    }

    public Datagrams bind(int port) throws IOException {
        return new SocketDatagrams(port <= 0 ? new DatagramSocket() : new DatagramSocket(port));
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already failing; the original cause is the one worth reporting.
        }
    }

    private static String address(InetAddress address) {
        return address == null ? "" : address.getHostAddress();
    }

    private static final class SocketStream implements Stream {

        private final Socket socket;

        SocketStream(Socket socket) {
            this.socket = socket;
        }

        public InputStream input() throws IOException {
            return socket.getInputStream();
        }

        public OutputStream output() throws IOException {
            return socket.getOutputStream();
        }

        public String remoteAddress() {
            return address(socket.getInetAddress());
        }

        public int remotePort() {
            return socket.getPort();
        }

        public String localAddress() {
            return address(socket.getLocalAddress());
        }

        public int localPort() {
            return socket.getLocalPort();
        }

        public void close() throws IOException {
            socket.close();
        }
    }

    private static final class SocketServer implements Server {

        private final ServerSocket socket;

        SocketServer(ServerSocket socket) {
            this.socket = socket;
        }

        public Stream accept() throws IOException {
            return new SocketStream(socket.accept());
        }

        public String localAddress() {
            return address(socket.getInetAddress());
        }

        public int localPort() {
            return socket.getLocalPort();
        }

        public void close() throws IOException {
            socket.close();
        }
    }

    private static final class SocketDatagrams implements Datagrams {

        private final DatagramSocket socket;

        SocketDatagrams(DatagramSocket socket) {
            this.socket = socket;
        }

        public void send(String host, int port, byte[] data, int offset, int length)
                throws IOException {
            DatagramPacket packet = new DatagramPacket(data, offset, length,
                    InetAddress.getByName(host), port);
            socket.send(packet);
        }

        public Packet receive(byte[] buffer, int offset, int length) throws IOException {
            DatagramPacket packet = new DatagramPacket(buffer, offset, length);
            socket.receive(packet);
            return new Packet(address(packet.getAddress()), packet.getPort(), packet.getLength());
        }

        public String localAddress() {
            return address(socket.getLocalAddress());
        }

        public int localPort() {
            return socket.getLocalPort();
        }

        public void close() throws IOException {
            socket.close();
        }
    }
}
