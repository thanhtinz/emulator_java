package com.mobicore.core.rt;

import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The {@code java.io} subset CLDC defines.
 *
 * <p>Every emulated stream wraps a host stream, so a game reading a level file
 * out of its JAR goes through the same buffered path the rest of the emulator
 * uses instead of an interpreted byte-at-a-time loop.</p>
 */
public final class IoClasses {

    private IoClasses() {
    }

    public static void install(final Vm vm) {
        inputStreams(vm);
        outputStreams(vm);
        printStream(vm);
    }

    /** Wraps host bytes in an emulated {@code ByteArrayInputStream}. */
    public static VmObject newByteArrayInputStream(Vm vm, byte[] data) {
        VmObject stream = vm.newInstance("java/io/ByteArrayInputStream");
        stream.host = new ByteArrayInputStream(data);
        return stream;
    }

    static InputStream input(Vm vm, VmObject self) {
        if (self == null || !(self.host instanceof InputStream)) {
            throw vm.raise("java/io/IOException", "Stream is closed or not readable");
        }
        return (InputStream) self.host;
    }

    static OutputStream output(Vm vm, VmObject self) {
        if (self == null || !(self.host instanceof OutputStream)) {
            throw vm.raise("java/io/IOException", "Stream is closed or not writable");
        }
        return (OutputStream) self.host;
    }

    /** Converts a host I/O failure into an emulated {@code IOException}. */
    static RuntimeException ioFailure(Vm vm, IOException cause) {
        return vm.raise("java/io/IOException", cause.getMessage() == null ? "I/O error" : cause.getMessage());
    }

    private static void inputStreams(final Vm vm) {
        vm.builtin("java/io/InputStream", Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("read", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Integer.valueOf(input(vm, self).read());
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                    }
                })
                .method("read", "([B)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmArray buffer = Rt.array(args, 0);
                        return readInto(vm, self, buffer, 0, buffer.length());
                    }
                })
                .method("read", "([BII)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return readInto(vm, self, Rt.array(args, 0), Rt.i(args, 1), Rt.i(args, 2));
                    }
                })
                .method("available", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Integer.valueOf(input(vm, self).available());
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                    }
                })
                .method("skip", "(J)J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Long.valueOf(input(vm, self).skip(Rt.l(args, 0)));
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                    }
                })
                .method("close", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            if (self.host instanceof InputStream) {
                                ((InputStream) self.host).close();
                            }
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                        return null;
                    }
                })
                .method("markSupported", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(input(vm, self).markSupported());
                    }
                })
                .method("mark", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        input(vm, self).mark(Rt.i(args, 0));
                        return null;
                    }
                })
                .method("reset", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            input(vm, self).reset();
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                        return null;
                    }
                })
                .define();

        vm.builtin("java/io/ByteArrayInputStream", "java/io/InputStream")
                .method("<init>", "([B)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new ByteArrayInputStream(Rt.array(args, 0).bytes());
                        return null;
                    }
                })
                .method("<init>", "([BII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new ByteArrayInputStream(Rt.array(args, 0).bytes(),
                                Rt.i(args, 1), Rt.i(args, 2));
                        return null;
                    }
                })
                .define();

        vm.builtin("java/io/DataInputStream", "java/io/InputStream")
                .method("<init>", "(Ljava/io/InputStream;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new DataInputStream(input(vm, Rt.obj(args, 0)));
                        return null;
                    }
                })
                .method("readInt", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Integer.valueOf(data(vm, self).readInt());
                        } catch (IOException e) {
                            throw eof(vm, e);
                        }
                    }
                })
                .method("readShort", "()S", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Integer.valueOf(data(vm, self).readShort());
                        } catch (IOException e) {
                            throw eof(vm, e);
                        }
                    }
                })
                .method("readUnsignedShort", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Integer.valueOf(data(vm, self).readUnsignedShort());
                        } catch (IOException e) {
                            throw eof(vm, e);
                        }
                    }
                })
                .method("readByte", "()B", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Integer.valueOf(data(vm, self).readByte());
                        } catch (IOException e) {
                            throw eof(vm, e);
                        }
                    }
                })
                .method("readUnsignedByte", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Integer.valueOf(data(vm, self).readUnsignedByte());
                        } catch (IOException e) {
                            throw eof(vm, e);
                        }
                    }
                })
                .method("readBoolean", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Rt.box(data(vm, self).readBoolean());
                        } catch (IOException e) {
                            throw eof(vm, e);
                        }
                    }
                })
                .method("readChar", "()C", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Integer.valueOf(data(vm, self).readChar());
                        } catch (IOException e) {
                            throw eof(vm, e);
                        }
                    }
                })
                .method("readLong", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Long.valueOf(data(vm, self).readLong());
                        } catch (IOException e) {
                            throw eof(vm, e);
                        }
                    }
                })
                .method("readUTF", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return vm.newString(data(vm, self).readUTF());
                        } catch (IOException e) {
                            throw eof(vm, e);
                        }
                    }
                })
                .method("readFully", "([B)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            data(vm, self).readFully(Rt.array(args, 0).bytes());
                        } catch (IOException e) {
                            throw eof(vm, e);
                        }
                        return null;
                    }
                })
                .define();
    }

    private static DataInputStream data(Vm vm, VmObject self) {
        if (!(self.host instanceof DataInputStream)) {
            throw vm.raise("java/io/IOException", "Not a data stream");
        }
        return (DataInputStream) self.host;
    }

    private static RuntimeException eof(Vm vm, IOException cause) {
        if (cause instanceof java.io.EOFException) {
            return vm.raise("java/io/EOFException", "End of stream");
        }
        return ioFailure(vm, cause);
    }

    private static Object readInto(Vm vm, VmObject self, VmArray buffer, int offset, int length) {
        try {
            return Integer.valueOf(input(vm, self).read(buffer.bytes(), offset, length));
        } catch (IOException e) {
            throw ioFailure(vm, e);
        } catch (IndexOutOfBoundsException e) {
            throw vm.raise("java/lang/ArrayIndexOutOfBoundsException", "read out of range");
        }
    }

    private static void outputStreams(final Vm vm) {
        vm.builtin("java/io/OutputStream", Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("write", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            output(vm, self).write(Rt.i(args, 0));
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                        return null;
                    }
                })
                .method("write", "([B)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmArray buffer = Rt.array(args, 0);
                        return writeFrom(vm, self, buffer, 0, buffer.length());
                    }
                })
                .method("write", "([BII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return writeFrom(vm, self, Rt.array(args, 0), Rt.i(args, 1), Rt.i(args, 2));
                    }
                })
                .method("flush", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            output(vm, self).flush();
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                        return null;
                    }
                })
                .method("close", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            if (self.host instanceof OutputStream) {
                                ((OutputStream) self.host).close();
                            }
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                        return null;
                    }
                })
                .define();

        vm.builtin("java/io/ByteArrayOutputStream", "java/io/OutputStream")
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new ByteArrayOutputStream();
                        return null;
                    }
                })
                .method("<init>", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new ByteArrayOutputStream(Math.max(1, Rt.i(args, 0)));
                        return null;
                    }
                })
                .method("toByteArray", "()[B", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        byte[] bytes = ((ByteArrayOutputStream) self.host).toByteArray();
                        return vm.wrapArray("B", bytes, bytes.length);
                    }
                })
                .method("size", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(((ByteArrayOutputStream) self.host).size());
                    }
                })
                .method("reset", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        ((ByteArrayOutputStream) self.host).reset();
                        return null;
                    }
                })
                .define();

        vm.builtin("java/io/DataOutputStream", "java/io/OutputStream")
                .method("<init>", "(Ljava/io/OutputStream;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new DataOutputStream(output(vm, Rt.obj(args, 0)));
                        return null;
                    }
                })
                .method("writeInt", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            sink(vm, self).writeInt(Rt.i(args, 0));
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                        return null;
                    }
                })
                .method("writeShort", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            sink(vm, self).writeShort(Rt.i(args, 0));
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                        return null;
                    }
                })
                .method("writeByte", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            sink(vm, self).writeByte(Rt.i(args, 0));
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                        return null;
                    }
                })
                .method("writeBoolean", "(Z)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            sink(vm, self).writeBoolean(Rt.bool(args, 0));
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                        return null;
                    }
                })
                .method("writeChar", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            sink(vm, self).writeChar(Rt.i(args, 0));
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                        return null;
                    }
                })
                .method("writeLong", "(J)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            sink(vm, self).writeLong(Rt.l(args, 0));
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                        return null;
                    }
                })
                .method("writeUTF", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            sink(vm, self).writeUTF(Rt.s(vm, args, 0));
                        } catch (IOException e) {
                            throw ioFailure(vm, e);
                        }
                        return null;
                    }
                })
                .define();
    }

    private static DataOutputStream sink(Vm vm, VmObject self) {
        if (!(self.host instanceof DataOutputStream)) {
            throw vm.raise("java/io/IOException", "Not a data stream");
        }
        return (DataOutputStream) self.host;
    }

    private static Object writeFrom(Vm vm, VmObject self, VmArray buffer, int offset, int length) {
        try {
            output(vm, self).write(buffer.bytes(), offset, length);
        } catch (IOException e) {
            throw ioFailure(vm, e);
        } catch (IndexOutOfBoundsException e) {
            throw vm.raise("java/lang/ArrayIndexOutOfBoundsException", "write out of range");
        }
        return null;
    }

    private static void printStream(final Vm vm) {
        vm.builtin("java/io/PrintStream", "java/io/OutputStream")
                .field("stderr", "I")
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("print", "(Ljava/lang/String;)V", printer(false))
                .method("print", "(I)V", printer(false))
                .method("print", "(J)V", printer(false))
                .method("print", "(C)V", printer(false))
                .method("print", "(Z)V", printer(false))
                .method("print", "(F)V", printer(false))
                .method("print", "(D)V", printer(false))
                .method("print", "(Ljava/lang/Object;)V", printer(false))
                .method("println", "()V", printer(true))
                .method("println", "(Ljava/lang/String;)V", printer(true))
                .method("println", "(I)V", printer(true))
                .method("println", "(J)V", printer(true))
                .method("println", "(C)V", printer(true))
                .method("println", "(Z)V", printer(true))
                .method("println", "(F)V", printer(true))
                .method("println", "(D)V", printer(true))
                .method("println", "(Ljava/lang/Object;)V", printer(true))
                .method("flush", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .define();

        installConsole(vm);
    }

    private static NativeMethod printer(final boolean newline) {
        return new NativeMethod() {
            public Object invoke(Vm vm, VmObject self, Object[] args) {
                String text = args.length == 0 ? "" : format(vm, args[0]);
                boolean toError = ((Integer) self.get("stderr")).intValue() != 0;
                vm.host().print(toError, newline ? text + "\n" : text);
                return null;
            }
        };
    }

    private static String format(Vm vm, Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof VmObject) {
            return LangClasses.toStringOf(vm, (VmObject) value);
        }
        if (value instanceof Integer) {
            return String.valueOf(((Integer) value).intValue());
        }
        return String.valueOf(value);
    }

    /** Wires {@code System.out} and {@code System.err} to the host console. */
    private static void installConsole(Vm vm) {
        com.mobicore.core.vm.VmClass system = vm.loadClass("java/lang/System");
        vm.initialize(system);
        VmObject out = vm.newInstance("java/io/PrintStream");
        VmObject err = vm.newInstance("java/io/PrintStream");
        err.set("stderr", Integer.valueOf(1));
        system.staticRefs()[system.findField("out").slot()] = out;
        system.staticRefs()[system.findField("err").slot()] = err;
    }
}
