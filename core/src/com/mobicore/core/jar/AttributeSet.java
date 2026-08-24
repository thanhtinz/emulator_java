package com.mobicore.core.jar;

import com.mobicore.core.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ordered {@code Key: value} attribute table used by both {@code MANIFEST.MF}
 * and {@code .jad} descriptors.
 *
 * <p>Lookups are case-insensitive because real world suites are inconsistent
 * about casing ({@code MIDlet-Name} vs {@code Midlet-Name}), while iteration
 * keeps the original order so the JAD editor can round-trip a file without
 * reshuffling it.</p>
 */
public final class AttributeSet {

    private final Map<String, String> byLowerKey = new LinkedHashMap<String, String>();
    private final Map<String, String> originalKeys = new LinkedHashMap<String, String>();

    public static AttributeSet parse(byte[] data) {
        return parse(decode(data));
    }

    public static AttributeSet parse(String text) {
        AttributeSet set = new AttributeSet();
        if (text == null) {
            return set;
        }
        List<String> logicalLines = joinContinuations(text);
        for (String line : logicalLines) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (key.length() > 0) {
                set.put(key, value);
            }
        }
        return set;
    }

    /**
     * Manifest files wrap long values by starting the next line with a single
     * space. JAD files do not, but tolerating the syntax in both is harmless.
     */
    private static List<String> joinContinuations(String text) {
        List<String> result = new ArrayList<String>();
        String[] rawLines = Text.split(text.replace("\r\n", "\n").replace('\r', '\n'), '\n');
        StringBuilder current = null;
        for (String raw : rawLines) {
            if (raw.length() > 0 && (raw.charAt(0) == ' ' || raw.charAt(0) == '\t') && current != null) {
                current.append(raw.substring(1));
                continue;
            }
            if (current != null) {
                result.add(current.toString());
            }
            current = new StringBuilder(raw);
        }
        if (current != null) {
            result.add(current.toString());
        }
        return result;
    }

    /** Decodes UTF-8, tolerating a byte order mark. */
    private static String decode(byte[] data) {
        if (data == null) {
            return "";
        }
        int offset = 0;
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        try {
            return new String(data, offset, data.length - offset, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return new String(data, offset, data.length - offset);
        }
    }

    public void put(String key, String value) {
        String lower = key.toLowerCase();
        byLowerKey.put(lower, value);
        originalKeys.put(lower, key);
    }

    public void remove(String key) {
        String lower = key.toLowerCase();
        byLowerKey.remove(lower);
        originalKeys.remove(lower);
    }

    public String get(String key) {
        return key == null ? null : byLowerKey.get(key.toLowerCase());
    }

    public String get(String key, String fallback) {
        return Text.orDefault(get(key), fallback);
    }

    public boolean has(String key) {
        return get(key) != null;
    }

    public int size() {
        return byLowerKey.size();
    }

    /** Keys in insertion order, with their original casing preserved. */
    public List<String> keys() {
        return Collections.unmodifiableList(new ArrayList<String>(originalKeys.values()));
    }

    /** Re-serialises the table back into descriptor syntax. */
    public String toDescriptor() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : originalKeys.entrySet()) {
            out.append(entry.getValue()).append(": ").append(byLowerKey.get(entry.getKey())).append('\n');
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return toDescriptor();
    }
}
