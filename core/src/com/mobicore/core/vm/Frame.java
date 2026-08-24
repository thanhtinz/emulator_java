package com.mobicore.core.vm;

/**
 * One activation record.
 *
 * <p>Primitives and references are kept in parallel arrays rather than boxed
 * into a single {@code Object[]}: a game's inner loop pushes and pops integers
 * thousands of times per frame, and boxing all of them would dominate the
 * emulator's cost.</p>
 */
public final class Frame {

    final VmMethod method;
    final int[] locals;
    final Object[] localRefs;
    final int[] stack;
    final Object[] stackRefs;

    int sp;
    int pc;
    /** Monitor held for a synchronized method, released when the frame exits. */
    VmObject monitor;

    Frame(VmMethod method) {
        this.method = method;
        int localCount = Math.max(method.maxLocals(), method.incomingSlots());
        this.locals = new int[localCount];
        this.localRefs = new Object[localCount];
        int stackSize = Math.max(method.maxStack(), 4);
        this.stack = new int[stackSize + 2];
        this.stackRefs = new Object[stackSize + 2];
    }

    public VmMethod method() {
        return method;
    }

    public int pc() {
        return pc;
    }

    // ---------------------------------------------------------------- stack

    void push(int value) {
        stack[sp] = value;
        stackRefs[sp] = null;
        sp++;
    }

    void pushRef(Object value) {
        stack[sp] = 0;
        stackRefs[sp] = value;
        sp++;
    }

    void pushLong(long value) {
        push((int) (value >>> 32));
        push((int) value);
    }

    void pushFloat(float value) {
        push(Float.floatToRawIntBits(value));
    }

    void pushDouble(double value) {
        pushLong(Double.doubleToRawLongBits(value));
    }

    int pop() {
        sp--;
        stackRefs[sp] = null;
        return stack[sp];
    }

    Object popRef() {
        sp--;
        Object value = stackRefs[sp];
        stackRefs[sp] = null;
        return value;
    }

    long popLong() {
        int low = pop();
        int high = pop();
        return ((long) high << 32) | (low & 0xFFFFFFFFL);
    }

    float popFloat() {
        return Float.intBitsToFloat(pop());
    }

    double popDouble() {
        return Double.longBitsToDouble(popLong());
    }

    int peek(int depth) {
        return stack[sp - 1 - depth];
    }

    Object peekRef(int depth) {
        return stackRefs[sp - 1 - depth];
    }

    // --------------------------------------------------------------- locals

    void setLocal(int index, int value) {
        locals[index] = value;
        localRefs[index] = null;
    }

    void setLocalRef(int index, Object value) {
        locals[index] = 0;
        localRefs[index] = value;
    }

    void setLocalLong(int index, long value) {
        locals[index] = (int) (value >>> 32);
        locals[index + 1] = (int) value;
        localRefs[index] = null;
        localRefs[index + 1] = null;
    }

    int local(int index) {
        return locals[index];
    }

    Object localRef(int index) {
        return localRefs[index];
    }

    long localLong(int index) {
        return ((long) locals[index] << 32) | (locals[index + 1] & 0xFFFFFFFFL);
    }

    @Override
    public String toString() {
        int line = method.lineFor(pc);
        return method.key() + " @" + pc + (line >= 0 ? " (line " + line + ")" : "");
    }
}
