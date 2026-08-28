package com.mobicore.core.rt;

import com.mobicore.core.vm.Descriptors;
import com.mobicore.core.vm.Monitors;
import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmClass;
import com.mobicore.core.vm.VmError;
import com.mobicore.core.vm.VmObject;

/**
 * The {@code java.lang} half of CLDC, implemented natively.
 *
 * <p>Providing the runtime library as host code rather than bytecode means the
 * emulator ships no {@code .class} files of its own, starts a suite without
 * loading a class library first, and can map {@code String} straight onto a
 * host string instead of interpreting character loops.</p>
 */
public final class LangClasses {

    private LangClasses() {
    }

    public static void install(final Vm vm) {
        object(vm);
        classClass(vm);
        string(vm);
        stringBuffer(vm);
        system(vm);
        math(vm);
        wrappers(vm);
        throwables(vm);
        threads(vm);
    }

    // ------------------------------------------------------------- Object

    private static void object(final Vm vm) {
        vm.builtin("java/lang/Object", null)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("getClass", "()Ljava/lang/Class;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.mirrorOf(self.type());
                    }
                })
                .method("hashCode", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(identity(self));
                    }
                })
                .method("equals", "(Ljava/lang/Object;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(self == args[0]);
                    }
                })
                .method("toString", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(self.type().binaryName() + "@"
                                + Integer.toHexString(identity(self)));
                    }
                })
                .method("notify", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Monitors.signal(self, false);
                        return null;
                    }
                })
                .method("notifyAll", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Monitors.signal(self, true);
                        return null;
                    }
                })
                .method("wait", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return await(vm, self, 0);
                    }
                })
                .method("wait", "(J)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return await(vm, self, Rt.l(args, 0));
                    }
                })
                .define();
    }

    private static Object await(Vm vm, VmObject self, long millis) {
        try {
            Monitors.await(self, millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw vm.raise("java/lang/InterruptedException", "wait interrupted");
        }
        return null;
    }

    private static int identity(Object value) {
        return System.identityHashCode(value);
    }

    // -------------------------------------------------------------- Class

    private static void classClass(final Vm vm) {
        vm.builtin(Vm.CLASS, Vm.OBJECT)
                .method("getName", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(((VmClass) self.host).binaryName());
                    }
                })
                .method("isInterface", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(((VmClass) self.host).isInterface());
                    }
                })
                .method("isArray", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(((VmClass) self.host).isArray());
                    }
                })
                .method("isInstance", "(Ljava/lang/Object;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject value = Rt.obj(args, 0);
                        return Rt.box(value != null && value.type().isAssignableTo((VmClass) self.host));
                    }
                })
                .method("newInstance", "()Ljava/lang/Object;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmClass type = (VmClass) self.host;
                        VmObject instance = vm.newInstance(type);
                        vm.invoke(type.findMethod("<init>", "()V"), instance, new Object[0]);
                        return instance;
                    }
                })
                .method("getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String path = Rt.s(vm, args, 0);
                        if (path == null) {
                            return null;
                        }
                        byte[] data = null;
                        for (int i = vm.sources().size() - 1; i >= 0 && data == null; i--) {
                            data = vm.sources().get(i).resourceBytes(path);
                        }
                        if (data == null) {
                            return null;
                        }
                        return IoClasses.newByteArrayInputStream(vm, data);
                    }
                })
                .staticMethod("forName", "(Ljava/lang/String;)Ljava/lang/Class;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String name = Descriptors.toInternalName(Rt.s(vm, args, 0));
                        return vm.mirrorOf(vm.loadClass(name));
                    }
                })
                .define();
    }

    // ------------------------------------------------------------- String

    private static void string(final Vm vm) {
        vm.builtin(Vm.STRING, Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = "";
                        return null;
                    }
                })
                .method("<init>", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = Rt.s(vm, args, 0);
                        return null;
                    }
                })
                .method("<init>", "([C)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmArray chars = Rt.array(args, 0);
                        self.host = new String(chars.chars());
                        return null;
                    }
                })
                .method("<init>", "([CII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = Rt.chars(Rt.array(args, 0), Rt.i(args, 1), Rt.i(args, 2));
                        return null;
                    }
                })
                .method("<init>", "([B)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = decode(Rt.array(args, 0).bytes(), 0, Rt.array(args, 0).length(), "UTF-8");
                        return null;
                    }
                })
                .method("<init>", "([BII)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = decode(Rt.array(args, 0).bytes(), Rt.i(args, 1), Rt.i(args, 2), "UTF-8");
                        return null;
                    }
                })
                .method("<init>", "([BIILjava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = decode(Rt.array(args, 0).bytes(), Rt.i(args, 1), Rt.i(args, 2),
                                Rt.s(vm, args, 3));
                        return null;
                    }
                })
                .method("length", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(text(self).length());
                    }
                })
                .method("charAt", "(I)C", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String value = text(self);
                        int index = Rt.i(args, 0);
                        if (index < 0 || index >= value.length()) {
                            throw vm.raise("java/lang/StringIndexOutOfBoundsException", String.valueOf(index));
                        }
                        return Integer.valueOf(value.charAt(index));
                    }
                })
                .method("indexOf", "(I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(text(self).indexOf(Rt.i(args, 0)));
                    }
                })
                .method("indexOf", "(II)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(text(self).indexOf(Rt.i(args, 0), Rt.i(args, 1)));
                    }
                })
                .method("indexOf", "(Ljava/lang/String;)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(text(self).indexOf(Rt.s(vm, args, 0)));
                    }
                })
                .method("indexOf", "(Ljava/lang/String;I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(text(self).indexOf(Rt.s(vm, args, 0), Rt.i(args, 1)));
                    }
                })
                .method("lastIndexOf", "(I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(text(self).lastIndexOf(Rt.i(args, 0)));
                    }
                })
                .method("substring", "(I)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return substring(vm, text(self), Rt.i(args, 0), text(self).length());
                    }
                })
                .method("substring", "(II)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return substring(vm, text(self), Rt.i(args, 0), Rt.i(args, 1));
                    }
                })
                .method("concat", "(Ljava/lang/String;)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(text(self).concat(Rt.s(vm, args, 0)));
                    }
                })
                .method("equals", "(Ljava/lang/Object;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject other = Rt.obj(args, 0);
                        return Rt.box(other != null && other.host instanceof String
                                && text(self).equals(other.host));
                    }
                })
                .method("equalsIgnoreCase", "(Ljava/lang/String;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String other = Rt.s(vm, args, 0);
                        return Rt.box(other != null && text(self).equalsIgnoreCase(other));
                    }
                })
                .method("compareTo", "(Ljava/lang/String;)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(text(self).compareTo(Rt.s(vm, args, 0)));
                    }
                })
                .method("hashCode", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(text(self).hashCode());
                    }
                })
                .method("startsWith", "(Ljava/lang/String;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(text(self).startsWith(Rt.s(vm, args, 0)));
                    }
                })
                .method("startsWith", "(Ljava/lang/String;I)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(text(self).startsWith(Rt.s(vm, args, 0), Rt.i(args, 1)));
                    }
                })
                .method("endsWith", "(Ljava/lang/String;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(text(self).endsWith(Rt.s(vm, args, 0)));
                    }
                })
                .method("trim", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(text(self).trim());
                    }
                })
                .method("toLowerCase", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(text(self).toLowerCase());
                    }
                })
                .method("toUpperCase", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(text(self).toUpperCase());
                    }
                })
                .method("replace", "(CC)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(text(self).replace((char) Rt.i(args, 0), (char) Rt.i(args, 1)));
                    }
                })
                .method("toString", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self;
                    }
                })
                .method("toCharArray", "()[C", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        char[] chars = text(self).toCharArray();
                        return vm.wrapArray("C", chars, chars.length);
                    }
                })
                .method("getBytes", "()[B", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        byte[] bytes = encode(text(self), "UTF-8");
                        return vm.wrapArray("B", bytes, bytes.length);
                    }
                })
                .method("getBytes", "(Ljava/lang/String;)[B", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // CLDC có bản nhận tên bảng mã, và game dùng nó thật:
                        // ghi tên người chơi bằng "UTF-8" là chuyện thường.
                        // Bảng mã lạ thì lùi về UTF-8 chứ không ném ra, vì
                        // ném ở đây làm chết một câu lưu tên nhân vật.
                        String charset = Rt.s(vm, args, 0);
                        byte[] bytes = encode(text(self),
                                charset == null || charset.length() == 0 ? "UTF-8" : charset);
                        return vm.wrapArray("B", bytes, bytes.length);
                    }
                })
                .method("getChars", "(II[CI)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        text(self).getChars(Rt.i(args, 0), Rt.i(args, 1), Rt.array(args, 2).chars(), Rt.i(args, 3));
                        return null;
                    }
                })
                .staticMethod("valueOf", "(I)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(String.valueOf(Rt.i(args, 0)));
                    }
                })
                .staticMethod("valueOf", "(J)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(String.valueOf(Rt.l(args, 0)));
                    }
                })
                .staticMethod("valueOf", "(Z)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(String.valueOf(Rt.bool(args, 0)));
                    }
                })
                .staticMethod("valueOf", "(C)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(String.valueOf((char) Rt.i(args, 0)));
                    }
                })
                .staticMethod("valueOf", "(F)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(String.valueOf(Rt.f(args, 0)));
                    }
                })
                .staticMethod("valueOf", "(D)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(String.valueOf(Rt.d(args, 0)));
                    }
                })
                .staticMethod("valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(toStringOf(vm, Rt.obj(args, 0)));
                    }
                })
                .staticMethod("valueOf", "([C)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(new String(Rt.array(args, 0).chars()));
                    }
                })
                .define();
    }

    private static Object substring(Vm vm, String value, int start, int end) {
        if (start < 0 || end > value.length() || start > end) {
            throw vm.raise("java/lang/StringIndexOutOfBoundsException", start + ".." + end);
        }
        return vm.newString(value.substring(start, end));
    }

    static String text(VmObject self) {
        return self.host instanceof String ? (String) self.host : "";
    }

    private static String decode(byte[] data, int offset, int length, String encoding) {
        try {
            return new String(data, offset, length, encoding == null ? "UTF-8" : encoding);
        } catch (java.io.UnsupportedEncodingException e) {
            return new String(data, offset, length);
        }
    }

    private static byte[] encode(String value, String encoding) {
        try {
            return value.getBytes(encoding);
        } catch (java.io.UnsupportedEncodingException e) {
            return value.getBytes();
        }
    }

    /** Calls the emulated {@code toString}, as {@code String.valueOf} must. */
    static String toStringOf(Vm vm, VmObject value) {
        if (value == null) {
            return "null";
        }
        if (value.host instanceof String) {
            return (String) value.host;
        }
        Object result = vm.callVirtual(value, "toString", "()Ljava/lang/String;");
        return vm.stringOf(result);
    }

    // -------------------------------------------------------- StringBuffer

    private static void stringBuffer(final Vm vm) {
        installBuffer(vm, "java/lang/StringBuffer");
        installBuffer(vm, "java/lang/StringBuilder");
    }

    private static void installBuffer(final Vm vm, String name) {
        final String descriptor = "L" + name + ";";
        vm.builtin(name, Vm.OBJECT)
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new StringBuilder();
                        return null;
                    }
                })
                .method("<init>", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new StringBuilder(Math.max(0, Rt.i(args, 0)));
                        return null;
                    }
                })
                .method("<init>", "(Ljava/lang/String;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = new StringBuilder(Rt.s(vm, args, 0));
                        return null;
                    }
                })
                .method("append", "(Ljava/lang/String;)" + descriptor, new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).append(args[0] == null ? "null" : Rt.s(vm, args, 0));
                        return self;
                    }
                })
                .method("append", "(I)" + descriptor, new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).append(Rt.i(args, 0));
                        return self;
                    }
                })
                .method("append", "(J)" + descriptor, new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).append(Rt.l(args, 0));
                        return self;
                    }
                })
                .method("append", "(C)" + descriptor, new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).append((char) Rt.i(args, 0));
                        return self;
                    }
                })
                .method("append", "(Z)" + descriptor, new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).append(Rt.bool(args, 0));
                        return self;
                    }
                })
                .method("append", "(F)" + descriptor, new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).append(Rt.f(args, 0));
                        return self;
                    }
                })
                .method("append", "(D)" + descriptor, new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).append(Rt.d(args, 0));
                        return self;
                    }
                })
                .method("append", "([C)" + descriptor, new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).append(Rt.array(args, 0).chars());
                        return self;
                    }
                })
                .method("append", "(Ljava/lang/Object;)" + descriptor, new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).append(toStringOf(vm, Rt.obj(args, 0)));
                        return self;
                    }
                })
                .method("insert", "(ILjava/lang/String;)" + descriptor, new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).insert(Rt.i(args, 0), Rt.s(vm, args, 1));
                        return self;
                    }
                })
                .method("deleteCharAt", "(I)" + descriptor, new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).deleteCharAt(Rt.i(args, 0));
                        return self;
                    }
                })
                .method("reverse", "()" + descriptor, new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).reverse();
                        return self;
                    }
                })
                .method("setLength", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Rt.builder(self).setLength(Rt.i(args, 0));
                        return null;
                    }
                })
                .method("length", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(Rt.builder(self).length());
                    }
                })
                .method("charAt", "(I)C", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(Rt.builder(self).charAt(Rt.i(args, 0)));
                    }
                })
                .method("toString", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(Rt.builder(self).toString());
                    }
                })
                .define();
    }

    // ------------------------------------------------------------- System

    private static void system(final Vm vm) {
        vm.builtin("java/lang/System", Vm.OBJECT)
                .staticField("out", "Ljava/io/PrintStream;")
                .staticField("err", "Ljava/io/PrintStream;")
                .staticMethod("currentTimeMillis", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(vm.host().currentTimeMillis());
                    }
                })
                .staticMethod("arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmArray source = Rt.array(args, 0);
                        VmArray target = Rt.array(args, 2);
                        if (source == null || target == null) {
                            throw vm.nullPointer("arraycopy with a null array");
                        }
                        int sourceOffset = Rt.i(args, 1);
                        int targetOffset = Rt.i(args, 3);
                        int length = Rt.i(args, 4);
                        if (length < 0 || sourceOffset < 0 || targetOffset < 0
                                || sourceOffset + length > source.length()
                                || targetOffset + length > target.length()) {
                            throw vm.raise("java/lang/ArrayIndexOutOfBoundsException", "arraycopy out of range");
                        }
                        System.arraycopy(source.data(), sourceOffset, target.data(), targetOffset, length);
                        return null;
                    }
                })
                .staticMethod("getProperty", "(Ljava/lang/String;)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String value = vm.host().property(Rt.s(vm, args, 0));
                        return value == null ? null : vm.newString(value);
                    }
                })
                .staticMethod("exit", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        vm.host().exit(Rt.i(args, 0));
                        return null;
                    }
                })
                .staticMethod("gc", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .staticMethod("identityHashCode", "(Ljava/lang/Object;)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(identity(args[0]));
                    }
                })
                .define();

        vm.builtin("java/lang/Runtime", Vm.OBJECT)
                .staticMethod("getRuntime", "()Ljava/lang/Runtime;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newInstance("java/lang/Runtime");
                    }
                })
                .method("freeMemory", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(Runtime.getRuntime().freeMemory());
                    }
                })
                .method("totalMemory", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(Runtime.getRuntime().totalMemory());
                    }
                })
                .method("gc", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("exit", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        vm.host().exit(Rt.i(args, 0));
                        return null;
                    }
                })
                .define();
    }

    // --------------------------------------------------------------- Math

    private static void math(final Vm vm) {
        vm.builtin("java/lang/Math", Vm.OBJECT)
                .staticField("PI", "D")
                .staticField("E", "D")
                .staticMethod("abs", "(I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(Math.abs(Rt.i(args, 0)));
                    }
                })
                .staticMethod("abs", "(J)J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(Math.abs(Rt.l(args, 0)));
                    }
                })
                .staticMethod("abs", "(F)F", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Float.valueOf(Math.abs(Rt.f(args, 0)));
                    }
                })
                .staticMethod("abs", "(D)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.abs(Rt.d(args, 0)));
                    }
                })
                .staticMethod("min", "(II)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(Math.min(Rt.i(args, 0), Rt.i(args, 1)));
                    }
                })
                .staticMethod("max", "(II)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(Math.max(Rt.i(args, 0), Rt.i(args, 1)));
                    }
                })
                .staticMethod("min", "(JJ)J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(Math.min(Rt.l(args, 0), Rt.l(args, 1)));
                    }
                })
                .staticMethod("max", "(JJ)J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(Math.max(Rt.l(args, 0), Rt.l(args, 1)));
                    }
                })
                .staticMethod("min", "(FF)F", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Float.valueOf(Math.min(Rt.f(args, 0), Rt.f(args, 1)));
                    }
                })
                .staticMethod("max", "(FF)F", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Float.valueOf(Math.max(Rt.f(args, 0), Rt.f(args, 1)));
                    }
                })
                .staticMethod("min", "(DD)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.min(Rt.d(args, 0), Rt.d(args, 1)));
                    }
                })
                .staticMethod("max", "(DD)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.max(Rt.d(args, 0), Rt.d(args, 1)));
                    }
                })
                .staticMethod("sqrt", "(D)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.sqrt(Rt.d(args, 0)));
                    }
                })
                .staticMethod("sin", "(D)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.sin(Rt.d(args, 0)));
                    }
                })
                .staticMethod("cos", "(D)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.cos(Rt.d(args, 0)));
                    }
                })
                .staticMethod("tan", "(D)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.tan(Rt.d(args, 0)));
                    }
                })
                .staticMethod("atan2", "(DD)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.atan2(Rt.d(args, 0), Rt.d(args, 1)));
                    }
                })
                .staticMethod("pow", "(DD)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.pow(Rt.d(args, 0), Rt.d(args, 1)));
                    }
                })
                .staticMethod("floor", "(D)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.floor(Rt.d(args, 0)));
                    }
                })
                .staticMethod("ceil", "(D)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.ceil(Rt.d(args, 0)));
                    }
                })
                .staticMethod("toRadians", "(D)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.toRadians(Rt.d(args, 0)));
                    }
                })
                .staticMethod("toDegrees", "(D)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(Math.toDegrees(Rt.d(args, 0)));
                    }
                })
                .define();

        VmClass math = vm.loadClass("java/lang/Math");
        vm.initialize(math);
        math.setStaticLong(math.findField("PI").slot(), Double.doubleToRawLongBits(Math.PI));
        math.setStaticLong(math.findField("E").slot(), Double.doubleToRawLongBits(Math.E));
    }

    // ------------------------------------------------------------ wrappers

    private static void wrappers(final Vm vm) {
        vm.builtin("java/lang/Integer", Vm.OBJECT)
                .staticField("MAX_VALUE", "I")
                .staticField("MIN_VALUE", "I")
                .method("<init>", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = Integer.valueOf(Rt.i(args, 0));
                        return null;
                    }
                })
                .method("intValue", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(((Number) self.host).intValue());
                    }
                })
                .method("longValue", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(((Number) self.host).longValue());
                    }
                })
                .method("toString", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(String.valueOf(self.host));
                    }
                })
                .method("equals", "(Ljava/lang/Object;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject other = Rt.obj(args, 0);
                        return Rt.box(other != null && self.host.equals(other.host));
                    }
                })
                .method("hashCode", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(self.host.hashCode());
                    }
                })
                .staticMethod("parseInt", "(Ljava/lang/String;)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(parseInt(vm, Rt.s(vm, args, 0), 10));
                    }
                })
                .staticMethod("parseInt", "(Ljava/lang/String;I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(parseInt(vm, Rt.s(vm, args, 0), Rt.i(args, 1)));
                    }
                })
                .staticMethod("valueOf", "(I)Ljava/lang/Integer;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return boxed(vm, "java/lang/Integer", Integer.valueOf(Rt.i(args, 0)));
                    }
                })
                .staticMethod("toString", "(I)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(String.valueOf(Rt.i(args, 0)));
                    }
                })
                .staticMethod("toHexString", "(I)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(Integer.toHexString(Rt.i(args, 0)));
                    }
                })
                .staticMethod("toBinaryString", "(I)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(Integer.toBinaryString(Rt.i(args, 0)));
                    }
                })
                .define();
        setStaticInt(vm, "java/lang/Integer", "MAX_VALUE", Integer.MAX_VALUE);
        setStaticInt(vm, "java/lang/Integer", "MIN_VALUE", Integer.MIN_VALUE);

        vm.builtin("java/lang/Long", Vm.OBJECT)
                .method("<init>", "(J)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = Long.valueOf(Rt.l(args, 0));
                        return null;
                    }
                })
                .method("longValue", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(((Number) self.host).longValue());
                    }
                })
                .method("intValue", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(((Number) self.host).intValue());
                    }
                })
                .method("toString", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(String.valueOf(self.host));
                    }
                })
                .staticMethod("parseLong", "(Ljava/lang/String;)J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Long.valueOf(Long.parseLong(Rt.s(vm, args, 0).trim()));
                        } catch (NumberFormatException e) {
                            throw vm.raise("java/lang/NumberFormatException", Rt.s(vm, args, 0));
                        }
                    }
                })
                .staticMethod("toString", "(J)Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(String.valueOf(Rt.l(args, 0)));
                    }
                })
                .define();

        vm.builtin("java/lang/Boolean", Vm.OBJECT)
                .method("<init>", "(Z)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = Boolean.valueOf(Rt.bool(args, 0));
                        return null;
                    }
                })
                .method("booleanValue", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(((Boolean) self.host).booleanValue());
                    }
                })
                .method("toString", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newString(String.valueOf(self.host));
                    }
                })
                .define();

        vm.builtin("java/lang/Character", Vm.OBJECT)
                .method("<init>", "(C)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = Character.valueOf((char) Rt.i(args, 0));
                        return null;
                    }
                })
                .method("charValue", "()C", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(((Character) self.host).charValue());
                    }
                })
                .staticMethod("isDigit", "(C)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(Character.isDigit((char) Rt.i(args, 0)));
                    }
                })
                .staticMethod("isLetter", "(C)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(Character.isLetter((char) Rt.i(args, 0)));
                    }
                })
                .staticMethod("isWhitespace", "(C)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Rt.box(Character.isWhitespace((char) Rt.i(args, 0)));
                    }
                })
                .staticMethod("toLowerCase", "(C)C", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(Character.toLowerCase((char) Rt.i(args, 0)));
                    }
                })
                .staticMethod("toUpperCase", "(C)C", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(Character.toUpperCase((char) Rt.i(args, 0)));
                    }
                })
                .staticMethod("digit", "(CI)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(Character.digit((char) Rt.i(args, 0), Rt.i(args, 1)));
                    }
                })
                .define();

        vm.builtin("java/lang/Float", Vm.OBJECT)
                .method("<init>", "(F)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = Float.valueOf(Rt.f(args, 0));
                        return null;
                    }
                })
                .method("floatValue", "()F", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Float.valueOf(((Number) self.host).floatValue());
                    }
                })
                .staticMethod("parseFloat", "(Ljava/lang/String;)F", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Float.valueOf(Float.parseFloat(Rt.s(vm, args, 0).trim()));
                        } catch (NumberFormatException e) {
                            throw vm.raise("java/lang/NumberFormatException", Rt.s(vm, args, 0));
                        }
                    }
                })
                .staticMethod("floatToIntBits", "(F)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(Float.floatToIntBits(Rt.f(args, 0)));
                    }
                })
                .staticMethod("intBitsToFloat", "(I)F", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Float.valueOf(Float.intBitsToFloat(Rt.i(args, 0)));
                    }
                })
                .define();

        vm.builtin("java/lang/Double", Vm.OBJECT)
                .method("<init>", "(D)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.host = Double.valueOf(Rt.d(args, 0));
                        return null;
                    }
                })
                .method("doubleValue", "()D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Double.valueOf(((Number) self.host).doubleValue());
                    }
                })
                .staticMethod("parseDouble", "(Ljava/lang/String;)D", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            return Double.valueOf(Double.parseDouble(Rt.s(vm, args, 0).trim()));
                        } catch (NumberFormatException e) {
                            throw vm.raise("java/lang/NumberFormatException", Rt.s(vm, args, 0));
                        }
                    }
                })
                .define();
    }

    private static int parseInt(Vm vm, String value, int radix) {
        try {
            return Integer.parseInt(value.trim(), radix);
        } catch (NumberFormatException e) {
            throw vm.raise("java/lang/NumberFormatException", "For input string: \"" + value + "\"");
        } catch (NullPointerException e) {
            throw vm.raise("java/lang/NumberFormatException", "null");
        }
    }

    private static VmObject boxed(Vm vm, String type, Object value) {
        VmObject instance = vm.newInstance(type);
        instance.host = value;
        return instance;
    }

    private static void setStaticInt(Vm vm, String type, String field, int value) {
        VmClass owner = vm.loadClass(type);
        vm.initialize(owner);
        owner.staticInts()[owner.findField(field).slot()] = value;
    }

    // ---------------------------------------------------------- throwables

    private static final String[][] EXCEPTIONS = {
            {"java/lang/Exception", "java/lang/Throwable"},
            {"java/lang/Error", "java/lang/Throwable"},
            {"java/lang/RuntimeException", "java/lang/Exception"},
            {"java/lang/NullPointerException", "java/lang/RuntimeException"},
            {"java/lang/ArithmeticException", "java/lang/RuntimeException"},
            {"java/lang/ClassCastException", "java/lang/RuntimeException"},
            {"java/lang/IllegalArgumentException", "java/lang/RuntimeException"},
            {"java/lang/IllegalStateException", "java/lang/RuntimeException"},
            {"java/lang/IllegalMonitorStateException", "java/lang/RuntimeException"},
            {"java/lang/NumberFormatException", "java/lang/IllegalArgumentException"},
            {"java/lang/IndexOutOfBoundsException", "java/lang/RuntimeException"},
            {"java/lang/ArrayIndexOutOfBoundsException", "java/lang/IndexOutOfBoundsException"},
            {"java/lang/StringIndexOutOfBoundsException", "java/lang/IndexOutOfBoundsException"},
            {"java/lang/NegativeArraySizeException", "java/lang/RuntimeException"},
            {"java/lang/ArrayStoreException", "java/lang/RuntimeException"},
            {"java/lang/SecurityException", "java/lang/RuntimeException"},
            {"java/lang/InterruptedException", "java/lang/Exception"},
            {"java/lang/ClassNotFoundException", "java/lang/Exception"},
            {"java/lang/InstantiationException", "java/lang/Exception"},
            {"java/lang/IllegalAccessException", "java/lang/Exception"},
            {"java/lang/OutOfMemoryError", "java/lang/Error"},
            {"java/lang/StackOverflowError", "java/lang/Error"},
            {"java/lang/NoClassDefFoundError", "java/lang/Error"},
            {"java/lang/NoSuchMethodError", "java/lang/Error"},
            {"java/lang/NoSuchFieldError", "java/lang/Error"},
            {"java/lang/AbstractMethodError", "java/lang/Error"},
            {"java/lang/VirtualMachineError", "java/lang/Error"},
            {"java/io/IOException", "java/lang/Exception"},
            {"java/io/EOFException", "java/io/IOException"},
            {"java/io/InterruptedIOException", "java/io/IOException"},
            {"java/io/UnsupportedEncodingException", "java/io/IOException"},
            {"java/util/NoSuchElementException", "java/lang/RuntimeException"},
            {"java/util/EmptyStackException", "java/lang/RuntimeException"},
    };

    private static void throwables(final Vm vm) {
        vm.builtin(Vm.THROWABLE, Vm.OBJECT)
                .field("message", "Ljava/lang/String;")
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
                .method("getMessage", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("message");
                    }
                })
                .method("toString", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Object message = self.get("message");
                        return vm.newString(self.type().binaryName()
                                + (message == null ? "" : ": " + vm.stringOf(message)));
                    }
                })
                .method("printStackTrace", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Object message = self.get("message");
                        vm.host().print(true, self.type().binaryName()
                                + (message == null ? "" : ": " + vm.stringOf(message)) + "\n");
                        vm.host().print(true, vm.interpreter().stackTrace());
                        return null;
                    }
                })
                .define();

        for (String[] entry : EXCEPTIONS) {
            defineThrowable(vm, entry[0], entry[1]);
        }
    }

    private static void defineThrowable(final Vm vm, String name, String superName) {
        vm.builtin(name, superName)
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
    }

    // ------------------------------------------------------------- threads

    private static void threads(final Vm vm) {
        vm.builtin("java/lang/Runnable", Vm.OBJECT, new String[0], true)
                .abstractMethod("run", "()V")
                .define();

        vm.builtin("java/lang/Thread", Vm.OBJECT, new String[]{"java/lang/Runnable"}, false)
                .field("target", "Ljava/lang/Runnable;")
                .field("name", "Ljava/lang/String;")
                .field("priority", "I")
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("<init>", "(Ljava/lang/Runnable;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("target", args[0]);
                        return null;
                    }
                })
                .method("start", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return startThread(vm, self);
                    }
                })
                .method("run", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject target = (VmObject) self.get("target");
                        if (target != null) {
                            vm.callVirtual(target, "run", "()V");
                        }
                        return null;
                    }
                })
                .method("join", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Thread host = (Thread) self.host;
                        if (host != null) {
                            try {
                                host.join();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        return null;
                    }
                })
                .method("isAlive", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Thread host = (Thread) self.host;
                        return Rt.box(host != null && host.isAlive());
                    }
                })
                .method("setPriority", "(I)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("priority", Integer.valueOf(Rt.i(args, 0)));
                        return null;
                    }
                })
                .method("getPriority", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("priority");
                    }
                })
                .method("interrupt", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Thread host = (Thread) self.host;
                        if (host != null) {
                            host.interrupt();
                        }
                        return null;
                    }
                })
                .staticMethod("sleep", "(J)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        try {
                            vm.host().sleep(Rt.l(args, 0));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw vm.raise("java/lang/InterruptedException", "sleep interrupted");
                        }
                        return null;
                    }
                })
                .staticMethod("currentThread", "()Ljava/lang/Thread;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return vm.newInstance("java/lang/Thread");
                    }
                })
                .staticMethod("yield", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        Thread.yield();
                        return null;
                    }
                })
                .define();
    }

    private static Object startThread(final Vm vm, final VmObject self) {
        if (self.host instanceof Thread) {
            throw vm.raise("java/lang/IllegalStateException", "Thread already started");
        }
        Thread host = new Thread(new Runnable() {
            public void run() {
                try {
                    vm.callVirtual(self, "run", "()V");
                } catch (VmError e) {
                    vm.host().print(true, "Thread aborted: " + e.getMessage() + "\n");
                } catch (RuntimeException e) {
                    vm.host().print(true, "Uncaught in thread: " + e + "\n");
                }
            }
        });
        self.host = host;
        host.setDaemon(true);
        host.start();
        return null;
    }
}
