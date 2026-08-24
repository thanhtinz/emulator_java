package com.mobicore.core.vm;

import java.util.ArrayList;
import java.util.List;

/** Helpers for JVM field and method descriptors. */
public final class Descriptors {

    private Descriptors() {
    }

    /** True for {@code long} and {@code double}, which occupy two slots. */
    public static boolean isWide(char kind) {
        return kind == 'J' || kind == 'D';
    }

    public static boolean isReference(char kind) {
        return kind == 'L' || kind == '[';
    }

    /** Number of local variable slots the arguments of a descriptor occupy. */
    public static int argumentSlots(String descriptor) {
        int slots = 0;
        for (String argument : argumentTypes(descriptor)) {
            slots += isWide(argument.charAt(0)) ? 2 : 1;
        }
        return slots;
    }

    public static List<String> argumentTypes(String descriptor) {
        List<String> types = new ArrayList<String>();
        int index = descriptor.indexOf('(');
        if (index < 0) {
            return types;
        }
        index++;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            int end = typeEnd(descriptor, index);
            types.add(descriptor.substring(index, end));
            index = end;
        }
        return types;
    }

    public static String returnType(String descriptor) {
        int close = descriptor.indexOf(')');
        return close < 0 ? descriptor : descriptor.substring(close + 1);
    }

    public static char returnKind(String descriptor) {
        String type = returnType(descriptor);
        return type.length() == 0 ? 'V' : type.charAt(0);
    }

    private static int typeEnd(String descriptor, int start) {
        int index = start;
        while (index < descriptor.length() && descriptor.charAt(index) == '[') {
            index++;
        }
        if (index < descriptor.length() && descriptor.charAt(index) == 'L') {
            int semicolon = descriptor.indexOf(';', index);
            if (semicolon < 0) {
                throw new VmError("Malformed descriptor: " + descriptor);
            }
            return semicolon + 1;
        }
        return index + 1;
    }

    /** Turns {@code java/lang/String} into {@code java.lang.String}. */
    public static String toBinaryName(String internalName) {
        return internalName.replace('/', '.');
    }

    public static String toInternalName(String binaryName) {
        return binaryName.replace('.', '/');
    }

    /** Class name referenced by a field descriptor, or {@code null} for primitives. */
    public static String referencedClass(String descriptor) {
        if (descriptor.startsWith("[")) {
            return descriptor;
        }
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1);
        }
        return null;
    }

    /** Human readable form used by stack traces and the inspector. */
    public static String pretty(String descriptor) {
        int dimensions = 0;
        String type = descriptor;
        while (type.startsWith("[")) {
            dimensions++;
            type = type.substring(1);
        }
        String base;
        if (type.startsWith("L")) {
            base = toBinaryName(type.substring(1, type.length() - 1));
        } else {
            switch (type.charAt(0)) {
                case 'I': base = "int"; break;
                case 'J': base = "long"; break;
                case 'F': base = "float"; break;
                case 'D': base = "double"; break;
                case 'Z': base = "boolean"; break;
                case 'B': base = "byte"; break;
                case 'C': base = "char"; break;
                case 'S': base = "short"; break;
                case 'V': base = "void"; break;
                default: base = type; break;
            }
        }
        StringBuilder out = new StringBuilder(base);
        for (int i = 0; i < dimensions; i++) {
            out.append("[]");
        }
        return out.toString();
    }
}
