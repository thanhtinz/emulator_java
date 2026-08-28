package com.mobicore.core.midp;

import com.mobicore.core.net.NetworkStack;
import com.mobicore.core.net.SocketTransport;
import com.mobicore.core.rt.Rt;
import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmObject;

import java.io.IOException;

/**
 * {@code socket://} and {@code datagram://}: connections that stay open.
 *
 * <p>An {@code http} connection is a question and an answer. Multiplayer games
 * of the J2ME era are not shaped that way: two handsets keep a line open and
 * take turns talking on it, a lobby holds a socket to a server for as long as
 * the player is in the room, and a few games trade single packets over UDP
 * because losing one matters less than waiting for it. None of that fits
 * behind {@code HttpConnection}, so it gets its own classes here.</p>
 *
 * <p>Every one of them goes through the same policy and the same monitor as
 * {@code http}, for the same reason: a twenty-year-old game opening a line to
 * an address the player has never heard of is exactly the moment to ask.</p>
 */
public final class MidpSockets {

    public static final String INPUT_CONNECTION = "javax/microedition/io/InputConnection";
    public static final String OUTPUT_CONNECTION = "javax/microedition/io/OutputConnection";
    public static final String STREAM_CONNECTION = "javax/microedition/io/StreamConnection";
    public static final String SOCKET_CONNECTION = "javax/microedition/io/SocketConnection";
    public static final String NOTIFIER = "javax/microedition/io/StreamConnectionNotifier";
    public static final String SERVER_SOCKET = "javax/microedition/io/ServerSocketConnection";
    public static final String DATAGRAM = "javax/microedition/io/Datagram";
    public static final String DATAGRAM_CONNECTION = "javax/microedition/io/DatagramConnection";
    public static final String UDP_CONNECTION = "javax/microedition/io/UDPDatagramConnection";

    /** The largest packet the emulator will hand a game that did not say. */
    private static final int NOMINAL_DATAGRAM = 1024;

    private MidpSockets() {
    }

    /** One open socket, host-side. */
    static final class StreamState {

        final SocketTransport.Stream stream;
        VmObject in;
        VmObject out;

        StreamState(SocketTransport.Stream stream) {
            this.stream = stream;
        }
    }

    /** A datagram's bytes, plus where they came from or are going. */
    static final class DatagramState {

        VmArray array;
        int offset;
        int length;
        int readAt;
        int writeAt;
        String address = "";

        DatagramState(VmArray array, int offset, int length) {
            this.array = array;
            this.offset = offset;
            this.length = length;
        }

        byte[] bytes() {
            return array.bytes();
        }

        int capacity() {
            return array.length() - offset;
        }
    }

    public static void install(final Vm vm, final NetworkStack network) {
        vm.builtin(INPUT_CONNECTION, Vm.OBJECT, new String[]{MidpNet.CONNECTION}, true)
                .abstractMethod("openInputStream", "()Ljava/io/InputStream;")
                .abstractMethod("openDataInputStream", "()Ljava/io/DataInputStream;")
                .define();
        vm.builtin(OUTPUT_CONNECTION, Vm.OBJECT, new String[]{MidpNet.CONNECTION}, true)
                .abstractMethod("openOutputStream", "()Ljava/io/OutputStream;")
                .abstractMethod("openDataOutputStream", "()Ljava/io/DataOutputStream;")
                .define();
        vm.builtin(STREAM_CONNECTION, Vm.OBJECT,
                        new String[]{INPUT_CONNECTION, OUTPUT_CONNECTION}, true)
                .define();
        vm.builtin(NOTIFIER, Vm.OBJECT, new String[]{MidpNet.CONNECTION}, true)
                .abstractMethod("acceptAndOpen", "()Ljavax/microedition/io/StreamConnection;")
                .define();

        socketConnection(vm);
        serverSocketConnection(vm, network);
        datagram(vm);
        datagramConnection(vm, network);
    }

    // ---------------------------------------------------------------- socket

    private static void socketConnection(final Vm vm) {
        vm.builtin(SOCKET_CONNECTION, Vm.OBJECT, new String[]{STREAM_CONNECTION}, false)
                .staticField("DELAY", "B")
                .staticField("LINGER", "B")
                .staticField("KEEPALIVE", "B")
                .staticField("RCVBUF", "B")
                .staticField("SNDBUF", "B")
                .method("getAddress", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(stream(vm, self).stream.remoteAddress());
                    }
                })
                .method("getPort", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(stream(vm, self).stream.remotePort());
                    }
                })
                .method("getLocalAddress", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(stream(vm, self).stream.localAddress());
                    }
                })
                .method("getLocalPort", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(stream(vm, self).stream.localPort());
                    }
                })
                // Buffer sizes and Nagle are tuning knobs for a handset radio.
                // Accepting them and reporting a plausible value is honest
                // here: the platform underneath already chose, and a game that
                // asks is deciding how much to send, not what to do.
                .method("setSocketOption", "(BI)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        stream(vm, self);
                        return null;
                    }
                })
                .method("getSocketOption", "(B)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        stream(vm, self);
                        int option = Rt.i(args, 0);
                        return Integer.valueOf(option == 3 || option == 4 ? 8192 : 0);
                    }
                })
                .method("openInputStream", "()Ljava/io/InputStream;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return input(vm, self);
                    }
                })
                .method("openDataInputStream", "()Ljava/io/DataInputStream;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject data = vm.newInstance("java/io/DataInputStream");
                        data.host = new java.io.DataInputStream(
                                (java.io.InputStream) input(vm, self).host);
                        return data;
                    }
                })
                .method("openOutputStream", "()Ljava/io/OutputStream;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return output(vm, self);
                    }
                })
                .method("openDataOutputStream", "()Ljava/io/DataOutputStream;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                VmObject data = vm.newInstance("java/io/DataOutputStream");
                                data.host = new java.io.DataOutputStream(
                                        (java.io.OutputStream) output(vm, self).host);
                                return data;
                            }
                        })
                .method("close", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        if (self.host instanceof StreamState) {
                            StreamState state = (StreamState) self.host;
                            self.host = null;
                            try {
                                state.stream.close();
                            } catch (IOException e) {
                                throw failure(vm, e);
                            }
                        }
                        return null;
                    }
                })
                .define();

        VmClass socket = vm.loadClass(SOCKET_CONNECTION);
        vm.initialize(socket);
        MidpGfx.setStatic(vm, socket, "DELAY", 0);
        MidpGfx.setStatic(vm, socket, "LINGER", 1);
        MidpGfx.setStatic(vm, socket, "KEEPALIVE", 2);
        MidpGfx.setStatic(vm, socket, "RCVBUF", 3);
        MidpGfx.setStatic(vm, socket, "SNDBUF", 4);
    }

    private static void serverSocketConnection(final Vm vm, final NetworkStack network) {
        vm.builtin(SERVER_SOCKET, Vm.OBJECT, new String[]{NOTIFIER}, false)
                .method("acceptAndOpen", "()Ljavax/microedition/io/StreamConnection;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                SocketTransport.Server server = server(vm, self);
                                try {
                                    return wrap(vm, server.accept());
                                } catch (IOException e) {
                                    throw failure(vm, e);
                                }
                            }
                        })
                .method("getLocalAddress", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(server(vm, self).localAddress());
                    }
                })
                .method("getLocalPort", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(server(vm, self).localPort());
                    }
                })
                .method("close", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        if (self.host instanceof SocketTransport.Server) {
                            SocketTransport.Server server = (SocketTransport.Server) self.host;
                            self.host = null;
                            try {
                                server.close();
                            } catch (IOException e) {
                                throw failure(vm, e);
                            }
                        }
                        return null;
                    }
                })
                .define();
    }

    // -------------------------------------------------------------- datagram

    private static void datagram(final Vm vm) {
        vm.builtin(DATAGRAM, Vm.OBJECT)
                .method("getData", "()[B", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return packet(vm, self).array;
                    }
                })
                .method("getLength", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(packet(vm, self).length);
                    }
                })
                .method("getOffset", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(packet(vm, self).offset);
                    }
                })
                .method("setLength", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        packet(vm, self).length = Rt.i(args, 0);
                        return null;
                    }
                })
                .method("setData", "([BII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        DatagramState packet = packet(vm, self);
                        packet.array = Rt.array(args, 0);
                        packet.offset = Rt.i(args, 1);
                        packet.length = Rt.i(args, 2);
                        packet.readAt = 0;
                        packet.writeAt = 0;
                        return null;
                    }
                })
                .method("reset", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        DatagramState packet = packet(vm, self);
                        packet.length = 0;
                        packet.readAt = 0;
                        packet.writeAt = 0;
                        return null;
                    }
                })
                .method("getAddress", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String address = packet(vm, self).address;
                        return address.length() == 0 ? null : vm.newString(address);
                    }
                })
                .method("setAddress", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        packet(vm, self).address = Rt.s(vm, args, 0);
                        return null;
                    }
                })
                .method("setAddress", "(Ljavax/microedition/io/Datagram;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // How a game answers whoever just wrote to it: copy the
                        // sender off the packet that arrived.
                        packet(vm, self).address = packet(vm, Rt.obj(args, 0)).address;
                        return null;
                    }
                })
                .method("write", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        put(vm, packet(vm, self), Rt.i(args, 0));
                        return null;
                    }
                })
                .method("write", "([BII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        DatagramState packet = packet(vm, self);
                        byte[] source = Rt.array(args, 0).bytes();
                        int from = Rt.i(args, 1);
                        int count = Rt.i(args, 2);
                        for (int i = 0; i < count; i++) {
                            put(vm, packet, source[from + i]);
                        }
                        return null;
                    }
                })
                .method("writeByte", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        put(vm, packet(vm, self), Rt.i(args, 0));
                        return null;
                    }
                })
                .method("writeShort", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        DatagramState packet = packet(vm, self);
                        int value = Rt.i(args, 0);
                        put(vm, packet, value >> 8);
                        put(vm, packet, value);
                        return null;
                    }
                })
                .method("writeInt", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        DatagramState packet = packet(vm, self);
                        int value = Rt.i(args, 0);
                        put(vm, packet, value >> 24);
                        put(vm, packet, value >> 16);
                        put(vm, packet, value >> 8);
                        put(vm, packet, value);
                        return null;
                    }
                })
                .method("writeUTF", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        DatagramState packet = packet(vm, self);
                        byte[] utf = utf8(Rt.s(vm, args, 0));
                        put(vm, packet, utf.length >> 8);
                        put(vm, packet, utf.length);
                        for (int i = 0; i < utf.length; i++) {
                            put(vm, packet, utf[i]);
                        }
                        return null;
                    }
                })
                .method("readByte", "()B", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf((byte) take(vm, packet(vm, self)));
                    }
                })
                .method("readUnsignedByte", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(take(vm, packet(vm, self)));
                    }
                })
                .method("readShort", "()S", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        DatagramState packet = packet(vm, self);
                        return Integer.valueOf((short) ((take(vm, packet) << 8) | take(vm, packet)));
                    }
                })
                .method("readInt", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        DatagramState packet = packet(vm, self);
                        int value = 0;
                        for (int i = 0; i < 4; i++) {
                            value = (value << 8) | take(vm, packet);
                        }
                        return Integer.valueOf(value);
                    }
                })
                .method("readUTF", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        DatagramState packet = packet(vm, self);
                        int count = (take(vm, packet) << 8) | take(vm, packet);
                        byte[] utf = new byte[count];
                        for (int i = 0; i < count; i++) {
                            utf[i] = (byte) take(vm, packet);
                        }
                        return vm.newString(fromUtf8(utf));
                    }
                })
                .define();
    }

    private static void datagramConnection(final Vm vm, final NetworkStack network) {
        vm.builtin(DATAGRAM_CONNECTION, Vm.OBJECT, new String[]{MidpNet.CONNECTION}, false)
                .field("defaultHost", "Ljava/lang/String;")
                .field("defaultPort", "I")
                .method("getMaximumLength", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        datagrams(vm, self);
                        return Integer.valueOf(NOMINAL_DATAGRAM);
                    }
                })
                .method("getNominalLength", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        datagrams(vm, self);
                        return Integer.valueOf(NOMINAL_DATAGRAM);
                    }
                })
                .method("newDatagram", "(I)Ljavax/microedition/io/Datagram;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return newDatagram(vm, self, vm.newArray("B", Rt.i(args, 0)), 0,
                                Rt.i(args, 0), defaultAddress(vm, self));
                    }
                })
                .method("newDatagram", "(ILjava/lang/String;)Ljavax/microedition/io/Datagram;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                return newDatagram(vm, self, vm.newArray("B", Rt.i(args, 0)), 0,
                                        Rt.i(args, 0), Rt.s(vm, args, 1));
                            }
                        })
                .method("newDatagram", "([BI)Ljavax/microedition/io/Datagram;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return newDatagram(vm, self, Rt.array(args, 0), 0, Rt.i(args, 1),
                                defaultAddress(vm, self));
                    }
                })
                .method("newDatagram", "([BILjava/lang/String;)Ljavax/microedition/io/Datagram;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                return newDatagram(vm, self, Rt.array(args, 0), 0, Rt.i(args, 1),
                                        Rt.s(vm, args, 2));
                            }
                        })
                .method("send", "(Ljavax/microedition/io/Datagram;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        SocketTransport.Datagrams port = datagrams(vm, self);
                        DatagramState packet = packet(vm, Rt.obj(args, 0));
                        String address = packet.address;
                        if (address.length() == 0) {
                            address = defaultAddress(vm, self);
                        }
                        String host = hostPart(address);
                        int target = portPart(address);
                        if (host.length() == 0 || target <= 0) {
                            throw vm.raise("java/io/IOException",
                                    "The packet has nowhere to go: set an address first");
                        }
                        try {
                            // What the game wrote, not the whole buffer: a
                            // 1024-byte scratch array holding twelve bytes of
                            // move is twelve bytes on the wire.
                            int length = packet.writeAt > 0 ? packet.writeAt : packet.length;
                            port.send(host, target, packet.bytes(), packet.offset, length);
                        } catch (IOException e) {
                            throw failure(vm, e);
                        }
                        return null;
                    }
                })
                .method("receive", "(Ljavax/microedition/io/Datagram;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        SocketTransport.Datagrams port = datagrams(vm, self);
                        DatagramState packet = packet(vm, Rt.obj(args, 0));
                        try {
                            SocketTransport.Packet arrived = port.receive(packet.bytes(),
                                    packet.offset, packet.capacity());
                            packet.length = arrived.length;
                            packet.readAt = 0;
                            packet.writeAt = 0;
                            packet.address = "datagram://" + arrived.host + ":" + arrived.port;
                        } catch (IOException e) {
                            throw failure(vm, e);
                        }
                        return null;
                    }
                })
                .method("close", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        if (self.host instanceof SocketTransport.Datagrams) {
                            SocketTransport.Datagrams port =
                                    (SocketTransport.Datagrams) self.host;
                            self.host = null;
                            try {
                                port.close();
                            } catch (IOException e) {
                                throw failure(vm, e);
                            }
                        }
                        return null;
                    }
                })
                .define();

        vm.builtin(UDP_CONNECTION, DATAGRAM_CONNECTION)
                .method("getLocalAddress", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(datagrams(vm, self).localAddress());
                    }
                })
                .method("getLocalPort", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(datagrams(vm, self).localPort());
                    }
                })
                .define();
    }

    // ------------------------------------------------------------- connector

    /**
     * Opens whichever of these a {@code Connector} URL names.
     *
     * @return the connection, or null when the scheme is not one of these
     */
    static VmObject open(Vm vm, NetworkStack network, String scheme, String url) {
        String address = after(url);
        if ("socket".equals(scheme)) {
            String host = hostPart(address);
            int port = portPart(address);
            if (host.length() == 0) {
                // socket://:1234 — the game is not dialling out, it is waiting
                // for somebody to dial in.
                return listen(vm, network, port);
            }
            if (port <= 0) {
                throw vm.raise(MidpNet.CONNECTION_NOT_FOUND,
                        "A socket address needs a port: " + url);
            }
            try {
                return wrap(vm, network.openSocket(host, port, 15000));
            } catch (IOException e) {
                throw failure(vm, e);
            }
        }
        if ("serversocket".equals(scheme)) {
            return listen(vm, network, portPart(address));
        }
        if ("datagram".equals(scheme)) {
            String host = hostPart(address);
            int port = portPart(address);
            try {
                // Dialling out binds a port of the emulator's choosing;
                // waiting binds the one the game asked for.
                VmObject connection = vm.newInstance(UDP_CONNECTION);
                connection.host = network.openDatagrams(host.length() == 0 ? port : 0);
                connection.set("defaultHost", host.length() == 0 ? null : vm.newString(host));
                connection.set("defaultPort", Integer.valueOf(host.length() == 0 ? 0 : port));
                return connection;
            } catch (IOException e) {
                throw failure(vm, e);
            }
        }
        return null;
    }

    private static VmObject listen(Vm vm, NetworkStack network, int port) {
        try {
            VmObject connection = vm.newInstance(SERVER_SOCKET);
            connection.host = network.openServer(port);
            return connection;
        } catch (IOException e) {
            throw failure(vm, e);
        }
    }

    static VmObject wrap(Vm vm, SocketTransport.Stream stream) {
        VmObject connection = vm.newInstance(SOCKET_CONNECTION);
        connection.host = new StreamState(stream);
        return connection;
    }

    // ----------------------------------------------------------- plumbing

    private static VmObject input(Vm vm, VmObject self) {
        StreamState state = stream(vm, self);
        if (state.in == null) {
            try {
                VmObject wrapper = vm.newInstance("java/io/InputStream");
                wrapper.host = state.stream.input();
                state.in = wrapper;
            } catch (IOException e) {
                throw failure(vm, e);
            }
        }
        return state.in;
    }

    private static VmObject output(Vm vm, VmObject self) {
        StreamState state = stream(vm, self);
        if (state.out == null) {
            try {
                VmObject wrapper = vm.newInstance("java/io/OutputStream");
                wrapper.host = state.stream.output();
                state.out = wrapper;
            } catch (IOException e) {
                throw failure(vm, e);
            }
        }
        return state.out;
    }

    private static StreamState stream(Vm vm, VmObject self) {
        if (!(self.host instanceof StreamState)) {
            throw vm.raise("java/io/IOException", "The connection is closed");
        }
        return (StreamState) self.host;
    }

    private static SocketTransport.Server server(Vm vm, VmObject self) {
        if (!(self.host instanceof SocketTransport.Server)) {
            throw vm.raise("java/io/IOException", "The listening port is closed");
        }
        return (SocketTransport.Server) self.host;
    }

    private static SocketTransport.Datagrams datagrams(Vm vm, VmObject self) {
        if (!(self.host instanceof SocketTransport.Datagrams)) {
            throw vm.raise("java/io/IOException", "The port is closed");
        }
        return (SocketTransport.Datagrams) self.host;
    }

    static DatagramState packet(Vm vm, VmObject self) {
        if (self == null || !(self.host instanceof DatagramState)) {
            throw vm.raise("java/io/IOException", "This is not a datagram");
        }
        return (DatagramState) self.host;
    }

    private static VmObject newDatagram(Vm vm, VmObject connection, VmArray array, int offset,
                                        int length, String address) {
        VmObject packet = vm.newInstance(DATAGRAM);
        DatagramState state = new DatagramState(array, offset, length);
        state.address = address == null ? "" : address;
        packet.host = state;
        return packet;
    }

    private static String defaultAddress(Vm vm, VmObject self) {
        Object host = self.get("defaultHost");
        if (!(host instanceof VmObject)) {
            return "";
        }
        Object port = self.get("defaultPort");
        int number = port instanceof Integer ? ((Integer) port).intValue() : 0;
        return "datagram://" + vm.stringOf(host) + ":" + number;
    }

    private static void put(Vm vm, DatagramState packet, int value) {
        if (packet.offset + packet.writeAt >= packet.array.length()) {
            throw vm.raise("java/io/IOException", "The packet is full");
        }
        packet.bytes()[packet.offset + packet.writeAt] = (byte) value;
        packet.writeAt++;
        if (packet.writeAt > packet.length) {
            packet.length = packet.writeAt;
        }
    }

    private static int take(Vm vm, DatagramState packet) {
        if (packet.readAt >= packet.length) {
            throw vm.raise("java/io/EOFException", "The packet has no more bytes");
        }
        int value = packet.bytes()[packet.offset + packet.readAt] & 0xFF;
        packet.readAt++;
        return value;
    }

    /** Host part of {@code host:port}, empty when the URL named no host. */
    public static String hostPart(String address) {
        String rest = after(address);
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            rest = rest.substring(0, slash);
        }
        int colon = rest.lastIndexOf(':');
        return colon >= 0 ? rest.substring(0, colon) : rest;
    }

    /** Port part of {@code host:port}, or 0 when there is none. */
    public static int portPart(String address) {
        String rest = after(address);
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            rest = rest.substring(0, slash);
        }
        int colon = rest.lastIndexOf(':');
        if (colon < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(rest.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String after(String url) {
        if (url == null) {
            return "";
        }
        int mark = url.indexOf("://");
        return mark >= 0 ? url.substring(mark + 3) : url;
    }

    private static byte[] utf8(String value) {
        try {
            return value.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value.getBytes();
        }
    }

    private static String fromUtf8(byte[] value) {
        try {
            return new String(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return new String(value);
        }
    }

    private static RuntimeException failure(Vm vm, IOException e) {
        String message = e.getMessage() == null ? "The connection failed" : e.getMessage();
        return vm.raise("java/io/IOException", message);
    }
}
