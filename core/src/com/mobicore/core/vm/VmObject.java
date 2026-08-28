package com.mobicore.core.vm;

/**
 * An instance in the emulated heap.
 *
 * <p>Primitive fields live in {@link #ints} and references in {@link #refs};
 * both arrays are indexed by the field's slot so a field lookup is a single
 * array access. {@link #host} lets a native class hang arbitrary host state off
 * an instance — a {@code String}'s characters, a framebuffer, a record store —
 * without the interpreter needing to know about it.</p>
 */
public class VmObject {

    private final VmClass type;
    final int[] ints;
    final Object[] refs;

    /** Host-side payload for natively implemented classes. */
    public Object host;

    /** Monitor recursion count, used by monitorenter/monitorexit. */
    int monitorDepth;
    /** Thread currently holding the monitor, or {@code null}. */
    Thread monitorOwner;

    /**
     * Chỗ nằm đợi của {@code wait}/{@code notify}, tách khỏi chỗ giành khoá.
     *
     * <p>Hai hàng đợi này khác nhau: một hàng là "đợi tới lượt cầm khoá", hàng
     * kia là "đợi ai đó báo". Dùng chung một cái khoá máy chủ cho cả hai thì
     * mỗi lần có người nhả khoá là mọi người đang {@code wait} đều bị đánh
     * thức — và một vòng lặp game viết theo lối đợi-báo sẽ chạy loạn lên.</p>
     */
    private Object waitSet;

    /** Hàng đợi của {@code wait}, dựng khi có người dùng tới. */
    synchronized Object waitSet() {
        if (waitSet == null) {
            waitSet = new Object();
        }
        return waitSet;
    }

    public VmObject(VmClass type) {
        this.type = type;
        int slots = type == null ? 0 : type.instanceSlots();
        this.ints = new int[slots];
        this.refs = new Object[slots];
    }

    public VmClass type() {
        return type;
    }

    public int getInt(int slot) {
        return ints[slot];
    }

    public void setInt(int slot, int value) {
        ints[slot] = value;
    }

    public long getLong(int slot) {
        return ((long) ints[slot] << 32) | (ints[slot + 1] & 0xFFFFFFFFL);
    }

    public void setLong(int slot, long value) {
        ints[slot] = (int) (value >>> 32);
        ints[slot + 1] = (int) value;
    }

    public Object getRef(int slot) {
        return refs[slot];
    }

    public void setRef(int slot, Object value) {
        refs[slot] = value;
    }

    /** Field access by name, used by native code and the object inspector. */
    public Object get(String fieldName) {
        VmField field = type.findField(fieldName);
        if (field == null) {
            throw new VmError("No field " + fieldName + " on " + type.name());
        }
        if (field.isReference()) {
            return refs[field.slot()];
        }
        if (field.isWide()) {
            return Long.valueOf(getLong(field.slot()));
        }
        return Integer.valueOf(ints[field.slot()]);
    }

    public void set(String fieldName, Object value) {
        VmField field = type.findField(fieldName);
        if (field == null) {
            throw new VmError("No field " + fieldName + " on " + type.name());
        }
        if (field.isReference()) {
            refs[field.slot()] = value;
        } else if (field.isWide()) {
            setLong(field.slot(), ((Number) value).longValue());
        } else {
            ints[field.slot()] = ((Number) value).intValue();
        }
    }

    @Override
    public String toString() {
        return type == null ? "null-type object" : Descriptors.toBinaryName(type.name()) + "@"
                + Integer.toHexString(System.identityHashCode(this));
    }
}
