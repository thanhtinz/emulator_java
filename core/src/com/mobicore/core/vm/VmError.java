package com.mobicore.core.vm;

/**
 * Failure inside the emulator itself: a malformed class file, an unsupported
 * instruction, or a broken link.
 *
 * <p>Distinct from {@link VmThrow}, which carries an exception raised by the
 * emulated program and is catchable by that program.</p>
 */
public class VmError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public VmError(String message) {
        super(message);
    }

    public VmError(String message, Throwable cause) {
        super(message, cause);
    }
}
