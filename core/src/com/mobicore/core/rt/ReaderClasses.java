package com.mobicore.core.rt;

import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

/**
 * {@code Reader}, {@code Writer} và hai lớp nối chúng với dòng byte.
 *
 * <p>Đây là cách game đọc chữ trong chính gói của nó: màn chơi, lời thoại,
 * bảng chữ — {@code new InputStreamReader(getClass().getResourceAsStream(
 * "/level1.txt"))}. Thiếu bốn lớp này thì game chết ngay ở dòng ấy, trước cả
 * khi kịp vẽ gì lên màn hình.</p>
 */
public final class ReaderClasses {

    private ReaderClasses() {
    }

    public static void install(final Vm vm) {
        readers(vm);
        writers(vm);
    }

    static Reader reader(Vm vm, VmObject self) {
        if (self == null || !(self.host instanceof Reader)) {
            throw vm.raise("java/io/IOException", "Chỗ đọc đã đóng");
        }
        return (Reader) self.host;
    }

    static Writer writer(Vm vm, VmObject self) {
        if (self == null || !(self.host instanceof Writer)) {
            throw vm.raise("java/io/IOException", "Chỗ ghi đã đóng");
        }
        return (Writer) self.host;
    }

    /**
     * Bảng mã game nêu tên, hoặc UTF-8 khi nó không nêu.
     *
     * <p>Tên bảng mã máy chủ không biết thì ném {@code UnsupportedEncoding
     * Exception} đúng như thật, chứ không lặng lẽ đọc bằng bảng khác: một
     * tệp đọc sai bảng mã ra một màn hình đầy dấu hỏi, và không ai đoán được
     * vì sao.</p>
     */
    private static String charset(Vm vm, String named) {
        String name = named == null || named.length() == 0 ? "UTF-8" : named;
        try {
            "x".getBytes(name);
        } catch (java.io.UnsupportedEncodingException unknown) {
            throw vm.raise("java/io/UnsupportedEncodingException", name);
        }
        return name;
    }

    private static void readers(final Vm vm) {
        vm.builtin("java/io/Reader", Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("read", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Integer.valueOf(reader(vm, self).read());
                        } catch (IOException e) {
                            throw IoClasses.ioFailure(vm, e);
                        }
                    }
                })
                .method("read", "([C)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmArray buffer = Rt.array(args, 0);
                        return readInto(vm, self, buffer, 0, buffer.length());
                    }
                })
                .method("read", "([CII)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return readInto(vm, self, Rt.array(args, 0), Rt.i(args, 1), Rt.i(args, 2));
                    }
                })
                .method("skip", "(J)J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Long.valueOf(reader(vm, self).skip(Rt.l(args, 0)));
                        } catch (IOException e) {
                            throw IoClasses.ioFailure(vm, e);
                        }
                    }
                })
                .method("ready", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Rt.box(reader(vm, self).ready());
                        } catch (IOException e) {
                            throw IoClasses.ioFailure(vm, e);
                        }
                    }
                })
                .method("markSupported", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(reader(vm, self).markSupported());
                    }
                })
                .method("mark", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            reader(vm, self).mark(Rt.i(args, 0));
                            return null;
                        } catch (IOException e) {
                            throw IoClasses.ioFailure(vm, e);
                        }
                    }
                })
                .method("reset", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            reader(vm, self).reset();
                            return null;
                        } catch (IOException e) {
                            throw IoClasses.ioFailure(vm, e);
                        }
                    }
                })
                .method("close", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            if (self.host instanceof Reader) {
                                ((Reader) self.host).close();
                            }
                            self.host = null;
                            return null;
                        } catch (IOException e) {
                            throw IoClasses.ioFailure(vm, e);
                        }
                    }
                })
                .define();

        vm.builtin("java/io/InputStreamReader", "java/io/Reader")
                .method("<init>", "(Ljava/io/InputStream;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new InputStreamReader(source(vm, Rt.obj(args, 0)));
                        return null;
                    }
                })
                .method("<init>", "(Ljava/io/InputStream;Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String name = charset(vm, Rt.s(vm, args, 1));
                        try {
                            self.host = new InputStreamReader(source(vm, Rt.obj(args, 0)), name);
                        } catch (java.io.UnsupportedEncodingException e) {
                            throw vm.raise("java/io/UnsupportedEncodingException", name);
                        }
                        return null;
                    }
                })
                .define();
    }

    /**
     * Dòng byte nằm dưới một {@code InputStreamReader}.
     *
     * <p>Dòng của game có thể là dòng do máy ảo dựng — tệp trong gói, ô lưu —
     * và khi ấy nó mang sẵn một {@code InputStream} thật của máy chủ. Không
     * mang thì đọc qua chính lớp của game, từng byte một.</p>
     */
    private static InputStream source(final Vm vm, final VmObject stream) {
        if (stream == null) {
            throw vm.raise("java/lang/NullPointerException", "InputStreamReader(null)");
        }
        if (stream.host instanceof InputStream) {
            return (InputStream) stream.host;
        }
        return new InputStream() {
            @Override
            public int read() throws IOException {
                Object value = vm.callVirtual(stream, "read", "()I");
                return value == null ? -1 : ((Number) value).intValue();
            }
        };
    }

    private static OutputStream sink(final Vm vm, final VmObject stream) {
        if (stream == null) {
            throw vm.raise("java/lang/NullPointerException", "OutputStreamWriter(null)");
        }
        if (stream.host instanceof OutputStream) {
            return (OutputStream) stream.host;
        }
        return new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                vm.callVirtual(stream, "write", "(I)V", Integer.valueOf(value));
            }
        };
    }

    private static Object readInto(Vm vm, VmObject self, VmArray buffer, int offset, int length) {
        char[] chars = buffer.chars();
        if (offset < 0 || length < 0 || offset + length > chars.length) {
            throw vm.raise("java/lang/ArrayIndexOutOfBoundsException", offset + "+" + length);
        }
        try {
            return Integer.valueOf(reader(vm, self).read(chars, offset, length));
        } catch (IOException e) {
            throw IoClasses.ioFailure(vm, e);
        }
    }

    private static void writers(final Vm vm) {
        vm.builtin("java/io/Writer", Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("write", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            writer(vm, self).write(Rt.i(args, 0));
                            return null;
                        } catch (IOException e) {
                            throw IoClasses.ioFailure(vm, e);
                        }
                    }
                })
                .method("write", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            writer(vm, self).write(Rt.s(vm, args, 0));
                            return null;
                        } catch (IOException e) {
                            throw IoClasses.ioFailure(vm, e);
                        }
                    }
                })
                .method("write", "([C)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmArray buffer = Rt.array(args, 0);
                        return writeFrom(vm, self, buffer, 0, buffer.length());
                    }
                })
                .method("write", "([CII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return writeFrom(vm, self, Rt.array(args, 0), Rt.i(args, 1), Rt.i(args, 2));
                    }
                })
                .method("flush", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            writer(vm, self).flush();
                            return null;
                        } catch (IOException e) {
                            throw IoClasses.ioFailure(vm, e);
                        }
                    }
                })
                .method("close", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            if (self.host instanceof Writer) {
                                ((Writer) self.host).close();
                            }
                            self.host = null;
                            return null;
                        } catch (IOException e) {
                            throw IoClasses.ioFailure(vm, e);
                        }
                    }
                })
                .define();

        vm.builtin("java/io/OutputStreamWriter", "java/io/Writer")
                .method("<init>", "(Ljava/io/OutputStream;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new OutputStreamWriter(sink(vm, Rt.obj(args, 0)));
                        return null;
                    }
                })
                .method("<init>", "(Ljava/io/OutputStream;Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String name = charset(vm, Rt.s(vm, args, 1));
                        try {
                            self.host = new OutputStreamWriter(sink(vm, Rt.obj(args, 0)), name);
                        } catch (java.io.UnsupportedEncodingException e) {
                            throw vm.raise("java/io/UnsupportedEncodingException", name);
                        }
                        return null;
                    }
                })
                .define();
    }

    private static Object writeFrom(Vm vm, VmObject self, VmArray buffer, int offset, int length) {
        char[] chars = buffer.chars();
        if (offset < 0 || length < 0 || offset + length > chars.length) {
            throw vm.raise("java/lang/ArrayIndexOutOfBoundsException", offset + "+" + length);
        }
        try {
            writer(vm, self).write(chars, offset, length);
            return null;
        } catch (IOException e) {
            throw IoClasses.ioFailure(vm, e);
        }
    }
}
