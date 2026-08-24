package com.mobicore.core.vm;

/**
 * Host implementation of a method the emulated program calls.
 *
 * <p>CLDC and MIDP classes are provided this way instead of as bytecode: the
 * runtime library then needs no {@code .class} files of its own, and platform
 * services such as the framebuffer or record stores can be reached directly.</p>
 */
public interface NativeMethod {

    /**
     * @param vm      the running virtual machine
     * @param self    receiver, or {@code null} for a static method
     * @param args    arguments in declaration order; {@code long} and
     *                {@code double} occupy a single entry each
     * @return the return value, or {@code null} for {@code void}
     */
    Object invoke(Vm vm, VmObject self, Object[] args);
}
