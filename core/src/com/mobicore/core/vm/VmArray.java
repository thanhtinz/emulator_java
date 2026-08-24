package com.mobicore.core.vm;

/**
 * An array instance.
 *
 * <p>Backing storage is the matching host primitive array so that bulk work —
 * blitting pixels, copying record bytes — stays a {@code System.arraycopy}
 * rather than a per-element boxing loop.</p>
 */
public final class VmArray extends VmObject {

    private final char componentKind;
    private final String componentType;
    private final int length;
    private final Object data;

    VmArray(VmClass arrayClass, char componentKind, String componentType, int length, Object data) {
        super(arrayClass);
        this.componentKind = componentKind;
        this.componentType = componentType;
        this.length = length;
        this.data = data;
    }

    public char componentKind() {
        return componentKind;
    }

    /** Descriptor of the component type, e.g. {@code I} or {@code Ljava/lang/String;}. */
    public String componentType() {
        return componentType;
    }

    public int length() {
        return length;
    }

    public Object data() {
        return data;
    }

    public int[] ints() {
        return (int[]) data;
    }

    public byte[] bytes() {
        return (byte[]) data;
    }

    public char[] chars() {
        return (char[]) data;
    }

    public short[] shorts() {
        return (short[]) data;
    }

    public long[] longs() {
        return (long[]) data;
    }

    public float[] floats() {
        return (float[]) data;
    }

    public double[] doubles() {
        return (double[]) data;
    }

    public Object[] objects() {
        return (Object[]) data;
    }

    @Override
    public String toString() {
        return Descriptors.pretty(componentType) + "[" + length + "]";
    }
}
