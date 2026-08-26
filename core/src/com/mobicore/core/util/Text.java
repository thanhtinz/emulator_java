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


    /**
     * A string reduced to what a search should match on: lower case, with
     * Vietnamese marks removed.
     *
     * <p>Someone looking for "Người Chạy" types "nguoi chay" — the marks are
     * slow on a phone keyboard and half the library's titles were typed
     * without them in the first place. A search that insisted on them would
     * find nothing and look broken.</p>
     */
    public static String searchKey(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            out.append(unmark(c));
        }
        return out.toString();
    }

    /** The plain letter behind a marked Vietnamese one, or the letter itself. */
    private static char unmark(char c) {
        switch (c) {
            case 'à': return 'a';
            case 'á': return 'a';
            case 'ả': return 'a';
            case 'ã': return 'a';
            case 'ạ': return 'a';
            case 'ă': return 'a';
            case 'ằ': return 'a';
            case 'ắ': return 'a';
            case 'ẳ': return 'a';
            case 'ẵ': return 'a';
            case 'ặ': return 'a';
            case 'â': return 'a';
            case 'ầ': return 'a';
            case 'ấ': return 'a';
            case 'ẩ': return 'a';
            case 'ẫ': return 'a';
            case 'ậ': return 'a';
            case 'è': return 'e';
            case 'é': return 'e';
            case 'ẻ': return 'e';
            case 'ẽ': return 'e';
            case 'ẹ': return 'e';
            case 'ê': return 'e';
            case 'ề': return 'e';
            case 'ế': return 'e';
            case 'ể': return 'e';
            case 'ễ': return 'e';
            case 'ệ': return 'e';
            case 'ì': return 'i';
            case 'í': return 'i';
            case 'ỉ': return 'i';
            case 'ĩ': return 'i';
            case 'ị': return 'i';
            case 'ò': return 'o';
            case 'ó': return 'o';
            case 'ỏ': return 'o';
            case 'õ': return 'o';
            case 'ọ': return 'o';
            case 'ô': return 'o';
            case 'ồ': return 'o';
            case 'ố': return 'o';
            case 'ổ': return 'o';
            case 'ỗ': return 'o';
            case 'ộ': return 'o';
            case 'ơ': return 'o';
            case 'ờ': return 'o';
            case 'ớ': return 'o';
            case 'ở': return 'o';
            case 'ỡ': return 'o';
            case 'ợ': return 'o';
            case 'ù': return 'u';
            case 'ú': return 'u';
            case 'ủ': return 'u';
            case 'ũ': return 'u';
            case 'ụ': return 'u';
            case 'ư': return 'u';
            case 'ừ': return 'u';
            case 'ứ': return 'u';
            case 'ử': return 'u';
            case 'ữ': return 'u';
            case 'ự': return 'u';
            case 'ỳ': return 'y';
            case 'ý': return 'y';
            case 'ỷ': return 'y';
            case 'ỹ': return 'y';
            case 'ỵ': return 'y';
            case 'đ': return 'd';
            default: return c;
        }
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
