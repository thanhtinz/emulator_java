package com.mobicore.core.midp;

import com.mobicore.core.net.NetworkPolicy;
import com.mobicore.core.net.NetworkStack;
import com.mobicore.core.net.NetworkTransport;
import com.mobicore.core.rt.IoClasses;
import com.mobicore.core.rt.Rt;
import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * {@code javax.microedition.io}: the Generic Connection Framework, limited to
 * what J2ME games actually use.
 *
 * <p>An {@code HttpConnection} buffers what the game writes and sends the
 * request lazily, on the first call that needs a response. That is exactly
 * what the MIDP specification prescribes, and it is also what lets the policy
 * layer see a complete request before anything leaves the device.</p>
 */
public final class MidpNet {

    public static final String CONNECTOR = "javax/microedition/io/Connector";
    public static final String CONNECTION = "javax/microedition/io/Connection";
    public static final String CONTENT_CONNECTION = "javax/microedition/io/ContentConnection";
    public static final String HTTP_CONNECTION = "javax/microedition/io/HttpConnection";
    public static final String CONNECTION_NOT_FOUND = "javax/microedition/io/ConnectionNotFoundException";

    private MidpNet() {
    }

    /** Per-connection state kept host-side. */
    static final class HttpState {

        final NetworkTransport.Request request;
        NetworkTransport.Response response;
        ByteArrayOutputStream pending = new ByteArrayOutputStream();
        boolean sent;

        HttpState(String url) {
            request = new NetworkTransport.Request(url);
        }
    }

    public static void install(final Vm vm, final NetworkStack network) {
        vm.builtin(CONNECTION, Vm.OBJECT, new String[0], true)
                .abstractMethod("close", "()V")
                .define();
        vm.builtin(CONTENT_CONNECTION, Vm.OBJECT, new String[]{CONNECTION}, true)
                .abstractMethod("getType", "()Ljava/lang/String;")
                .abstractMethod("getLength", "()J")
                .define();

        vm.builtin(CONNECTION_NOT_FOUND, "java/io/IOException")
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("<init>", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("message", args[0]);
                        return null;
                    }
                })
                .define();

        httpConnection(vm, network);
        // Sockets before the connector, for the same reason files are: the
        // connector only hands out what has been installed.
        MidpSockets.install(vm, network);
        connector(vm, network);
    }

    private static void connector(final Vm vm, final NetworkStack network) {
        vm.builtin(CONNECTOR, Vm.OBJECT)
                .staticField("READ", "I").staticField("WRITE", "I").staticField("READ_WRITE", "I")
                .staticMethod("open", "(Ljava/lang/String;)Ljavax/microedition/io/Connection;",
                        opener(network))
                .staticMethod("open", "(Ljava/lang/String;I)Ljavax/microedition/io/Connection;",
                        opener(network))
                .staticMethod("open", "(Ljava/lang/String;IZ)Ljavax/microedition/io/Connection;",
                        opener(network))
                .staticMethod("openInputStream", "(Ljava/lang/String;)Ljava/io/InputStream;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                VmObject connection = open(vm, network, Rt.s(vm, args, 0));
                                return responseStream(vm, network, connection);
                            }
                        })
                .staticMethod("openDataInputStream", "(Ljava/lang/String;)Ljava/io/DataInputStream;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                VmObject connection = open(vm, network, Rt.s(vm, args, 0));
                                VmObject stream = responseStream(vm, network, connection);
                                VmObject data = vm.newInstance("java/io/DataInputStream");
                                data.host = new java.io.DataInputStream(
                                        (java.io.InputStream) stream.host);
                                return data;
                            }
                        })
                .define();

        VmClass connector = vm.loadClass(CONNECTOR);
        vm.initialize(connector);
        MidpGfx.setStatic(vm, connector, "READ", 1);
        MidpGfx.setStatic(vm, connector, "WRITE", 2);
        MidpGfx.setStatic(vm, connector, "READ_WRITE", 3);
    }

    private static NativeMethod opener(final NetworkStack network) {
        return new NativeMethod() {
            public Object invoke(Vm vm, VmObject self, Object[] args) {
                // The mode, when the game gave one: READ is 1, and a file
                // opened for reading must refuse to be written to.
                int mode = args.length > 1 && args[1] instanceof Integer
                        ? Rt.i(args, 1) : 3;
                return open(vm, network, Rt.s(vm, args, 0), mode);
            }
        };
    }

    private static VmObject open(Vm vm, NetworkStack network, String url) {
        return open(vm, network, url, 3);
    }

    private static VmObject open(Vm vm, NetworkStack network, String url, int mode) {
        String scheme = NetworkPolicy.schemeOf(url);
        // JSR-75 comes in through the same door as everything else: a game
        // asks Connector for a file: URL exactly as it asks for an http one.
        if ("file".equals(scheme) && vm.findLoaded(MidpFiles.FILE_CONNECTION) != null) {
            return MidpFiles.open(vm, url, mode);
        }
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            VmObject socket = MidpSockets.open(vm, network, scheme, url);
            if (socket != null) {
                return socket;
            }
            // Comm ports, infrared and the rest are not emulated; saying so is
            // better than pretending the connection succeeded.
            throw vm.raise(CONNECTION_NOT_FOUND,
                    "MobiCore does not carry " + scheme + " connections");
        }
        VmObject connection = vm.newInstance(HTTP_CONNECTION);
        connection.host = new HttpState(url);
        return connection;
    }

    private static void httpConnection(final Vm vm, final NetworkStack network) {
        vm.builtin(HTTP_CONNECTION, Vm.OBJECT,
                        new String[]{CONNECTION, CONTENT_CONNECTION}, false)
                .staticField("GET", "Ljava/lang/String;")
                .staticField("POST", "Ljava/lang/String;")
                .staticField("HEAD", "Ljava/lang/String;")
                .staticField("HTTP_OK", "I")
                .staticField("HTTP_NOT_FOUND", "I")
                .staticField("HTTP_INTERNAL_ERROR", "I")
                .method("setRequestMethod", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        state(vm, self).request.method = Rt.s(vm, args, 0);
                        return null;
                    }
                })
                .method("getRequestMethod", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(state(vm, self).request.method);
                    }
                })
                .method("setRequestProperty", "(Ljava/lang/String;Ljava/lang/String;)V",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                state(vm, self).request.headers.put(Rt.s(vm, args, 0), Rt.s(vm, args, 1));
                                return null;
                            }
                        })
                .method("getRequestProperty", "(Ljava/lang/String;)Ljava/lang/String;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                String value = state(vm, self).request.headers.get(Rt.s(vm, args, 0));
                                return value == null ? null : vm.newString(value);
                            }
                        })
                .method("getURL", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(state(vm, self).request.url);
                    }
                })
                .method("getHost", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(NetworkPolicy.hostOf(state(vm, self).request.url));
                    }
                })
                .method("getResponseCode", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(send(vm, network, self).status);
                    }
                })
                .method("getResponseMessage", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(send(vm, network, self).message);
                    }
                })
                .method("getHeaderField", "(Ljava/lang/String;)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String name = Rt.s(vm, args, 0);
                        for (Map.Entry<String, String> header
                                : send(vm, network, self).headers.entrySet()) {
                            if (header.getKey().equalsIgnoreCase(name)) {
                                return vm.newString(header.getValue());
                            }
                        }
                        return null;
                    }
                })
                .method("getType", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        for (Map.Entry<String, String> header
                                : send(vm, network, self).headers.entrySet()) {
                            if ("Content-Type".equalsIgnoreCase(header.getKey())) {
                                return vm.newString(header.getValue());
                            }
                        }
                        return null;
                    }
                })
                .method("getLength", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(send(vm, network, self).body.length);
                    }
                })
                .method("openInputStream", "()Ljava/io/InputStream;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return responseStream(vm, network, self);
                    }
                })
                .method("openDataInputStream", "()Ljava/io/DataInputStream;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject stream = responseStream(vm, network, self);
                        VmObject data = vm.newInstance("java/io/DataInputStream");
                        data.host = new java.io.DataInputStream((java.io.InputStream) stream.host);
                        return data;
                    }
                })
                .method("openOutputStream", "()Ljava/io/OutputStream;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        HttpState state = state(vm, self);
                        VmObject stream = vm.newInstance("java/io/ByteArrayOutputStream");
                        stream.host = state.pending;
                        return stream;
                    }
                })
                .method("openDataOutputStream", "()Ljava/io/DataOutputStream;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        HttpState state = state(vm, self);
                        VmObject stream = vm.newInstance("java/io/DataOutputStream");
                        stream.host = new java.io.DataOutputStream(state.pending);
                        return stream;
                    }
                })
                .method("close", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = null;
                        return null;
                    }
                })
                .define();

        VmClass http = vm.loadClass(HTTP_CONNECTION);
        vm.initialize(http);
        http.staticRefs()[http.findField("GET").slot()] = vm.newString("GET");
        http.staticRefs()[http.findField("POST").slot()] = vm.newString("POST");
        http.staticRefs()[http.findField("HEAD").slot()] = vm.newString("HEAD");
        MidpGfx.setStatic(vm, http, "HTTP_OK", 200);
        MidpGfx.setStatic(vm, http, "HTTP_NOT_FOUND", 404);
        MidpGfx.setStatic(vm, http, "HTTP_INTERNAL_ERROR", 500);
    }

    static HttpState state(Vm vm, VmObject self) {
        if (!(self.host instanceof HttpState)) {
            throw vm.raise("java/io/IOException", "The connection is closed");
        }
        return (HttpState) self.host;
    }

    /** Sends the request once, on the first call that needs an answer. */
    private static NetworkTransport.Response send(Vm vm, NetworkStack network, VmObject self) {
        HttpState state = state(vm, self);
        if (state.sent) {
            return state.response;
        }
        byte[] body = state.pending.toByteArray();
        if (body.length > 0) {
            state.request.body = body;
            if ("GET".equalsIgnoreCase(state.request.method)) {
                // A game that wrote a body clearly meant to POST it.
                state.request.method = "POST";
            }
        }
        try {
            state.response = network.perform(state.request);
        } catch (IOException e) {
            state.sent = true;
            throw vm.raise("java/io/IOException", e.getMessage());
        }
        state.sent = true;
        return state.response;
    }

    private static VmObject responseStream(Vm vm, NetworkStack network, VmObject connection) {
        NetworkTransport.Response response = send(vm, network, connection);
        return IoClasses.newByteArrayInputStream(vm, response.body);
    }
}
