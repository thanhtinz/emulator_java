package com.mobicore.core.vm;

/** A field declaration together with the slot it occupies. */
public final class VmField {

    public static final int ACC_STATIC = 0x0008;
    public static final int ACC_FINAL = 0x0010;

    private final VmClass owner;
    private final String name;
    private final String descriptor;
    private final int access;
    private final char kind;
    private int slot = -1;
    private Object constantValue;

    VmField(VmClass owner, String name, String descriptor, int access) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.access = access;
        this.kind = descriptor.length() == 0 ? 'I' : descriptor.charAt(0);
    }

    public VmClass owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public String descriptor() {
        return descriptor;
    }

    public char kind() {
        return kind;
    }

    public boolean isStatic() {
        return (access & ACC_STATIC) != 0;
    }

    public boolean isWide() {
        return Descriptors.isWide(kind);
    }

    public boolean isReference() {
        return Descriptors.isReference(kind);
    }

    public int slot() {
        return slot;
    }

    void setSlot(int slot) {
        this.slot = slot;
    }

    /** Value of a {@code ConstantValue} attribute, or {@code null}. */
    public Object constantValue() {
        return constantValue;
    }

    void setConstantValue(Object value) {
        this.constantValue = value;
    }

    @Override
    public String toString() {
        return owner.name() + "." + name + " " + descriptor;
    }
}
