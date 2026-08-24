package com.mobicore.core.vm;

/**
 * Reads a {@code .class} file into a {@link VmClass}.
 *
 * <p>Only the attributes the interpreter needs are decoded: {@code Code},
 * {@code ConstantValue}, {@code LineNumberTable} and {@code SourceFile}.
 * Everything else — including {@code StackMapTable}, which matters to a
 * verifier but not to an interpreter — is skipped.</p>
 */
public final class ClassFileParser {

    private static final int MAGIC = 0xCAFEBABE;

    private ClassFileParser() {
    }

    public static VmClass parse(byte[] data) {
        ByteReader in = new ByteReader(data);
        if (in.u4() != MAGIC) {
            throw new VmError("Not a class file: bad magic number");
        }
        in.u2(); // minor version
        int major = in.u2();
        if (major > 68) {
            throw new VmError("Unsupported class file version " + major);
        }

        ConstantPool pool = readConstantPool(in);
        int access = in.u2();
        String name = pool.className(in.u2());
        int superIndex = in.u2();
        String superName = superIndex == 0 ? null : pool.className(superIndex);

        int interfaceCount = in.u2();
        String[] interfaceNames = new String[interfaceCount];
        for (int i = 0; i < interfaceCount; i++) {
            interfaceNames[i] = pool.className(in.u2());
        }

        VmClass type = new VmClass(name, access, pool, superName, interfaceNames, null);
        type.setFields(readFields(in, pool, type));
        type.setMethods(readMethods(in, pool, type));
        readClassAttributes(in, pool, type);
        return type;
    }

    private static ConstantPool readConstantPool(ByteReader in) {
        int count = in.u2();
        ConstantPool pool = new ConstantPool(count);
        for (int index = 1; index < count; index++) {
            int tag = in.u1();
            switch (tag) {
                case ConstantPool.UTF8: {
                    int length = in.u2();
                    pool.set(index, tag, 0, 0, in.utf8(length));
                    break;
                }
                case ConstantPool.INTEGER:
                case ConstantPool.FLOAT:
                    pool.set(index, tag, in.u4(), 0, null);
                    break;
                case ConstantPool.LONG:
                case ConstantPool.DOUBLE:
                    pool.set(index, tag, in.u4(), in.u4(), null);
                    // Longs and doubles occupy two pool entries.
                    index++;
                    break;
                case ConstantPool.CLASS:
                case ConstantPool.STRING:
                    pool.set(index, tag, in.u2(), 0, null);
                    break;
                case ConstantPool.FIELDREF:
                case ConstantPool.METHODREF:
                case ConstantPool.INTERFACE_METHODREF:
                case ConstantPool.NAME_AND_TYPE:
                    pool.set(index, tag, in.u2(), in.u2(), null);
                    break;
                case ConstantPool.METHOD_HANDLE:
                    pool.set(index, tag, in.u1(), in.u2(), null);
                    break;
                case ConstantPool.METHOD_TYPE:
                    pool.set(index, tag, in.u2(), 0, null);
                    break;
                case ConstantPool.INVOKE_DYNAMIC:
                    pool.set(index, tag, in.u2(), in.u2(), null);
                    break;
                default:
                    throw new VmError("Unknown constant pool tag " + tag + " at index " + index);
            }
        }
        return pool;
    }

    private static VmField[] readFields(ByteReader in, ConstantPool pool, VmClass owner) {
        int count = in.u2();
        VmField[] fields = new VmField[count];
        for (int i = 0; i < count; i++) {
            int access = in.u2();
            String name = pool.utf8(in.u2());
            String descriptor = pool.utf8(in.u2());
            VmField field = new VmField(owner, name, descriptor, access);
            int attributeCount = in.u2();
            for (int a = 0; a < attributeCount; a++) {
                String attributeName = pool.utf8(in.u2());
                int length = in.u4();
                if ("ConstantValue".equals(attributeName)) {
                    field.setConstantValue(constantValue(pool, in.u2()));
                } else {
                    in.skip(length);
                }
            }
            fields[i] = field;
        }
        return fields;
    }

    private static Object constantValue(ConstantPool pool, int index) {
        switch (pool.tag(index)) {
            case ConstantPool.INTEGER:
                return Integer.valueOf(pool.intValue(index));
            case ConstantPool.FLOAT:
                return Float.valueOf(pool.floatValue(index));
            case ConstantPool.LONG:
                return Long.valueOf(pool.longValue(index));
            case ConstantPool.DOUBLE:
                return Double.valueOf(pool.doubleValue(index));
            case ConstantPool.STRING:
                return pool.stringValue(index);
            default:
                return null;
        }
    }

    private static VmMethod[] readMethods(ByteReader in, ConstantPool pool, VmClass owner) {
        int count = in.u2();
        VmMethod[] methods = new VmMethod[count];
        for (int i = 0; i < count; i++) {
            int access = in.u2();
            String name = pool.utf8(in.u2());
            String descriptor = pool.utf8(in.u2());
            VmMethod method = new VmMethod(owner, name, descriptor, access);
            int attributeCount = in.u2();
            for (int a = 0; a < attributeCount; a++) {
                String attributeName = pool.utf8(in.u2());
                int length = in.u4();
                if ("Code".equals(attributeName)) {
                    readCode(in, pool, method);
                } else {
                    in.skip(length);
                }
            }
            methods[i] = method;
        }
        return methods;
    }

    private static void readCode(ByteReader in, ConstantPool pool, VmMethod method) {
        int maxStack = in.u2();
        int maxLocals = in.u2();
        int codeLength = in.u4();
        byte[] code = in.bytes(codeLength);

        int handlerCount = in.u2();
        int[] table = new int[handlerCount * 4];
        for (int i = 0; i < handlerCount; i++) {
            table[i * 4] = in.u2();
            table[i * 4 + 1] = in.u2();
            table[i * 4 + 2] = in.u2();
            table[i * 4 + 3] = in.u2();
        }
        method.setCode(code, maxStack, maxLocals, table);

        int attributeCount = in.u2();
        for (int a = 0; a < attributeCount; a++) {
            String attributeName = pool.utf8(in.u2());
            int length = in.u4();
            if ("LineNumberTable".equals(attributeName)) {
                int entries = in.u2();
                int[] lines = new int[entries * 2];
                for (int i = 0; i < entries; i++) {
                    lines[i * 2] = in.u2();
                    lines[i * 2 + 1] = in.u2();
                }
                method.setLineNumbers(lines);
            } else {
                in.skip(length);
            }
        }
    }

    private static void readClassAttributes(ByteReader in, ConstantPool pool, VmClass type) {
        if (in.remaining() < 2) {
            return;
        }
        int count = in.u2();
        for (int i = 0; i < count; i++) {
            String name = pool.utf8(in.u2());
            int length = in.u4();
            if ("SourceFile".equals(name)) {
                type.setSourceFile(pool.utf8(in.u2()));
            } else {
                in.skip(length);
            }
        }
    }
}
