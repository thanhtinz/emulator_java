package com.mobicore.core.util;

/** Small string helpers kept dependency-free so the core stays portable. */
public final class Text {

    private Text() {
    }

    public static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    public static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    public static String orDefault(String value, String fallback) {
        String trimmed = trimOrNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    /** Splits on a single character without relying on regular expressions. */
    public static String[] split(String value, char separator) {
        if (value == null) {
            return new String[0];
        }
        int count = 1;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == separator) {
                count++;
            }
        }
        String[] parts = new String[count];
        int index = 0;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == separator) {
                parts[index++] = value.substring(start, i);
                start = i + 1;
            }
        }
        parts[index] = value.substring(start);
        return parts;
    }

    /** Filesystem-safe identifier derived from a human readable name. */
    public static String slug(String value) {
        String source = orDefault(value, "untitled");
        StringBuilder out = new StringBuilder(source.length());
        boolean lastDash = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            boolean keep = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (keep) {
                out.append(Character.toLowerCase(c));
                lastDash = false;
            } else if (!lastDash && out.length() > 0) {
                out.append('-');
                lastDash = true;
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
            out.setLength(out.length() - 1);
        }
        return out.length() == 0 ? "untitled" : out.toString();
    }
}
