package com.mobicore.core.rt;

import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmArray;
import com.mobicore.core.vm.VmObject;

/** Argument helpers shared by the native runtime classes. */
public final class Rt {

    private Rt() {
    }

    public static int i(Object[] args, int index) {
        Object value = args[index];
        return value == null ? 0 : ((Number) value).intValue();
    }

    public static long l(Object[] args, int index) {
        Object value = args[index];
        return value == null ? 0L : ((Number) value).longValue();
    }

    public static float f(Object[] args, int index) {
        Object value = args[index];
        return value == null ? 0f : ((Number) value).floatValue();
    }

    public static double d(Object[] args, int index) {
        Object value = args[index];
        return value == null ? 0d : ((Number) value).doubleValue();
    }

    public static boolean bool(Object[] args, int index) {
        return i(args, index) != 0;
    }

    public static VmObject obj(Object[] args, int index) {
        return (VmObject) args[index];
    }

    public static VmArray array(Object[] args, int index) {
        return (VmArray) args[index];
    }

    /** Emulated string argument as a host string. */
    public static String s(Vm vm, Object[] args, int index) {
        return vm.stringOf(args[index]);
    }

    public static Object box(boolean value) {
        return Integer.valueOf(value ? 1 : 0);
    }

    /** Host payload of a natively backed instance. */
    public static Object host(VmObject self) {
        return self == null ? null : self.host;
    }

    public static StringBuilder builder(VmObject self) {
        return (StringBuilder) self.host;
    }

    /** Copies an emulated char array into a host string. */
    public static String chars(VmArray array, int offset, int count) {
        return new String(array.chars(), offset, count);
    }
}
