package com.mobicore.core.vm;

/** Supplies class file bytes to the virtual machine. */
public interface ClassSource {

    /**
     * @param internalName class name in internal form, e.g. {@code demo/Game}
     * @return the class file bytes, or {@code null} when this source has none
     */
    byte[] classBytes(String internalName);

    /** Resource bytes for {@code Class.getResourceAsStream}. */
    byte[] resourceBytes(String path);
}
