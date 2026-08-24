package com.mobicore.core.vm;

/**
 * Class file constant pool.
 *
 * <p>Entries are kept in their raw, unresolved form and decoded on demand.
 * Resolution results are cached by the owning {@link VmClass}, so a hot
 * {@code invokevirtual} does not re-walk the pool on every execution.</p>
 */
public final class ConstantPool {

    public static final int UTF8 = 1;
    public static final int INTEGER = 3;
    public static final int FLOAT = 4;
    public static final int LONG = 5;
    public static final int DOUBLE = 6;
    public static final int CLASS = 7;
    public static final int STRING = 8;
    public static final int FIELDREF = 9;
    public static final int METHODREF = 10;
    public static final int INTERFACE_METHODREF = 11;
    public static final int NAME_AND_TYPE = 12;
    public static final int METHOD_HANDLE = 15;
    public static final int METHOD_TYPE = 16;
    public static final int INVOKE_DYNAMIC = 18;

    private final int[] tags;
    private final int[] first;
    private final int[] second;
    private final Object[] values;

    ConstantPool(int count) {
        tags = new int[count];
        first = new int[count];
        second = new int[count];
        values = new Object[count];
    }

    void set(int index, int tag, int a, int b, Object value) {
        tags[index] = tag;
        first[index] = a;
        second[index] = b;
        values[index] = value;
    }

    public int count() {
        return tags.length;
    }

    public int tag(int index) {
        return index <= 0 || index >= tags.length ? 0 : tags[index];
    }

    public String utf8(int index) {
        Object value = values[index];
        if (!(value instanceof String)) {
            throw new VmError("Constant pool entry " + index + " is not a UTF-8 string");
        }
        return (String) value;
    }

    public int intValue(int index) {
        return first[index];
    }

    public long longValue(int index) {
        return ((long) first[index] << 32) | (second[index] & 0xFFFFFFFFL);
    }

    public float floatValue(int index) {
        return Float.intBitsToFloat(first[index]);
    }

    public double doubleValue(int index) {
        return Double.longBitsToDouble(longValue(index));
    }

    /** Class name of a {@code CONSTANT_Class} entry, in internal form. */
    public String className(int index) {
        return utf8(first[index]);
    }

    /** String value of a {@code CONSTANT_String} entry. */
    public String stringValue(int index) {
        return utf8(first[index]);
    }

    /** Owner class of a field or method reference. */
    public String refClass(int index) {
        return className(first[index]);
    }

    public String refName(int index) {
        return utf8(first[second[index]]);
    }

    public String refDescriptor(int index) {
        return utf8(second[second[index]]);
    }
}
