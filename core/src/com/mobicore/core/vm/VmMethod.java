package com.mobicore.core.vm;

/** A method declaration, its bytecode and its exception table. */
public final class VmMethod {

    public static final int ACC_PUBLIC = 0x0001;
    public static final int ACC_STATIC = 0x0008;
    public static final int ACC_FINAL = 0x0010;
    public static final int ACC_SYNCHRONIZED = 0x0020;
    public static final int ACC_NATIVE = 0x0100;
    public static final int ACC_ABSTRACT = 0x0400;

    private final VmClass owner;
    private final String name;
    private final String descriptor;
    private final int access;
    private final int argumentSlots;
    private final char returnKind;

    private byte[] code;
    private int maxStack;
    private int maxLocals;
    /** Flattened {start, end, handler, catchTypeIndex} rows. */
    private int[] exceptionTable = new int[0];
    private int[] lineNumbers = new int[0];
    private NativeMethod nativeImpl;

    VmMethod(VmClass owner, String name, String descriptor, int access) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.access = access;
        this.argumentSlots = Descriptors.argumentSlots(descriptor);
        this.returnKind = Descriptors.returnKind(descriptor);
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

    public int access() {
        return access;
    }

    public boolean isStatic() {
        return (access & ACC_STATIC) != 0;
    }

    public boolean isAbstract() {
        return (access & ACC_ABSTRACT) != 0;
    }

    public boolean isNative() {
        return (access & ACC_NATIVE) != 0;
    }

    public boolean isSynchronized() {
        return (access & ACC_SYNCHRONIZED) != 0;
    }

    /** Argument slots, excluding {@code this}. */
    public int argumentSlots() {
        return argumentSlots;
    }

    /** Total incoming slots, including {@code this} for instance methods. */
    public int incomingSlots() {
        return argumentSlots + (isStatic() ? 0 : 1);
    }

    public char returnKind() {
        return returnKind;
    }

    public byte[] code() {
        return code;
    }

    public int maxStack() {
        return maxStack;
    }

    public int maxLocals() {
        return maxLocals;
    }

    public int[] exceptionTable() {
        return exceptionTable;
    }

    public NativeMethod nativeImpl() {
        return nativeImpl;
    }

    public void bindNative(NativeMethod impl) {
        this.nativeImpl = impl;
    }

    void setCode(byte[] code, int maxStack, int maxLocals, int[] exceptionTable) {
        this.code = code;
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
        this.exceptionTable = exceptionTable;
    }

    void setLineNumbers(int[] lineNumbers) {
        this.lineNumbers = lineNumbers;
    }

    /** Source line for a bytecode offset, or -1 when the class has no table. */
    public int lineFor(int pc) {
        int line = -1;
        for (int i = 0; i + 1 < lineNumbers.length; i += 2) {
            if (lineNumbers[i] <= pc) {
                line = lineNumbers[i + 1];
            } else {
                break;
            }
        }
        return line;
    }

    /** Key used by the native method registry. */
    public String key() {
        return owner.name() + "." + name + descriptor;
    }

    @Override
    public String toString() {
        return key();
    }
}
