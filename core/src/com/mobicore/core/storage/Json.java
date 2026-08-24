package com.mobicore.core.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer.
 *
 * <p>Profiles and the library index are stored as JSON so a user can inspect or
 * hand-edit them, and so a backup taken on Android restores on iOS. The core
 * carries no third-party dependency, so the codec lives here: objects become
 * {@link LinkedHashMap} (order preserved, which keeps diffs readable), arrays
 * become {@link List}, numbers become {@link Double} or {@link Long}.</p>
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------- writing

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value, 0);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map) {
            writeObject(out, (Map<?, ?>) value, depth);
        } else if (value instanceof List) {
            writeArray(out, (List<?>) value, depth);
        } else if (value instanceof Boolean) {
            out.append(((Boolean) value).booleanValue() ? "true" : "false");
        } else if (value instanceof Number) {
            writeNumber(out, (Number) value);
        } else {
            writeString(out, value.toString());
        }
    }

    private static void writeNumber(StringBuilder out, Number value) {
        double d = value.doubleValue();
        if (value instanceof Double || value instanceof Float) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                out.append((long) d);
                return;
            }
            out.append(d);
            return;
        }
        out.append(value.longValue());
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        int index = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            indent(out, depth + 1);
            writeString(out, String.valueOf(entry.getKey()));
            out.append(": ");
            writeValue(out, entry.getValue(), depth + 1);
            if (++index < map.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        indent(out, depth);
        out.append('}');
    }

    private static void writeArray(StringBuilder out, List<?> list, int depth) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        boolean scalarsOnly = true;
        for (Object item : list) {
            if (item instanceof Map || item instanceof List) {
                scalarsOnly = false;
                break;
            }
        }
        if (scalarsOnly) {
            // Key mappings and frame sequences read far better on one line.
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                writeValue(out, list.get(i), depth);
            }
            out.append(']');
            return;
        }
        out.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(out, depth + 1);
            writeValue(out, list.get(i), depth + 1);
            if (i < list.size() - 1) {
                out.append(',');
            }
            out.append('\n');
        }
        indent(out, depth);
        out.append(']');
    }

    private static void indent(StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append("\\u").append(pad(Integer.toHexString(c)));
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }
        out.append('"');
    }

    private static String pad(String hex) {
        StringBuilder out = new StringBuilder(hex);
        while (out.length() < 4) {
            out.insert(0, '0');
        }
        return out.toString();
    }

    // ------------------------------------------------------------- reading

    /** Thrown when a stored profile or index cannot be parsed. */
    public static final class SyntaxException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        SyntaxException(String message, int position) {
            super(message + " at offset " + position);
        }
    }

    public static Object read(String text) {
        Parser parser = new Parser(text == null ? "" : text);
        parser.skipWhitespace();
        Object value = parser.value();
        parser.skipWhitespace();
        return value;
    }

    /** Reads a JSON object, returning an empty map for anything else. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readObject(String text) {
        try {
            Object value = read(text);
            return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
        } catch (SyntaxException e) {
            return new LinkedHashMap<String, Object>();
        }
    }

    public static Map<String, Object> object() {
        return new LinkedHashMap<String, Object>();
    }

    // --------------------------------------------------------- convenience

    public static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map == null ? null : map.get(key);
        return value == null ? fallback : value.toString();
    }

    public static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    public static long longValue(Map<String, Object> map, String key, long fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString().trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return value == null ? fallback : "true".equalsIgnoreCase(value.toString());
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof List ? (List<Object>) value : new ArrayList<Object>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> child(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
    }

    private static final class Parser {

        private final String text;
        private int position;

        Parser(String text) {
            this.text = text;
        }

        void skipWhitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        Object value() {
            skipWhitespace();
            if (position >= text.length()) {
                throw new SyntaxException("Unexpected end of input", position);
            }
            char c = text.charAt(position);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': return literal("true", Boolean.TRUE);
                case 'f': return literal("false", Boolean.FALSE);
                case 'n': return literal("null", null);
                default: return number();
            }
        }

        private Object literal(String token, Object value) {
            if (!text.startsWith(token, position)) {
                throw new SyntaxException("Expected " + token, position);
            }
            position += token.length();
            return value;
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            position++;
            skipWhitespace();
            if (position < text.length() && text.charAt(position) == '}') {
                position++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                expect(':');
                map.put(key, value());
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new SyntaxException("Expected , or }", position);
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<Object>();
            position++;
            skipWhitespace();
            if (position < text.length() && text.charAt(position) == ']') {
                position++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new SyntaxException("Expected , or ]", position);
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (position >= text.length()) {
                    throw new SyntaxException("Unterminated string", position);
                }
                char c = text.charAt(position++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = text.charAt(position++);
                switch (escape) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'u':
                        out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                        break;
                    default: out.append(escape); break;
                }
            }
        }

        private Object number() {
            int start = position;
            while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
                position++;
            }
            String token = text.substring(start, position);
            if (token.length() == 0) {
                throw new SyntaxException("Expected a value", start);
            }
            try {
                if (token.indexOf('.') < 0 && token.indexOf('e') < 0 && token.indexOf('E') < 0) {
                    return Long.valueOf(Long.parseLong(token));
                }
                return Double.valueOf(Double.parseDouble(token));
            } catch (NumberFormatException e) {
                throw new SyntaxException("Malformed number " + token, start);
            }
        }

        private void expect(char expected) {
            if (position >= text.length() || text.charAt(position) != expected) {
                throw new SyntaxException("Expected " + expected, position);
            }
            position++;
        }

        private char next() {
            if (position >= text.length()) {
                throw new SyntaxException("Unexpected end of input", position);
            }
            return text.charAt(position++);
        }
    }
}
