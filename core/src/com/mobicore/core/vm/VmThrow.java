package com.mobicore.core.vm;

/**
 * An exception raised by the emulated program.
 *
 * <p>It rides the host stack so that native methods propagate emulated
 * exceptions naturally, and carries the emulated {@code Throwable} so the
 * interpreter can match it against a handler's catch type.</p>
 */
public final class VmThrow extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final VmObject throwable;

    public VmThrow(VmObject throwable, String message) {
        super(message);
        this.throwable = throwable;
    }

    public VmObject throwable() {
        return throwable;
    }

    public VmClass type() {
        return throwable == null ? null : throwable.type();
    }

    @Override
    public String toString() {
        return (throwable == null ? "throw" : throwable.type().binaryName())
                + (getMessage() == null ? "" : ": " + getMessage());
    }
}
