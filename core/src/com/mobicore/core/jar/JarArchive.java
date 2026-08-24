package com.mobicore.core.jar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * In-memory view of a MIDlet suite archive.
 *
 * <p>Suites are small (a few hundred kilobytes at most) so the whole archive is
 * decompressed once and kept in memory. That keeps class loading and resource
 * lookups free of file handles, which matters on mobile where the emulator may
 * be suspended at any point.</p>
 */
public final class JarArchive {

    private final Map<String, byte[]> entries;
    private final List<String> names;

    private JarArchive(Map<String, byte[]> entries) {
        this.entries = entries;
        List<String> sorted = new ArrayList<String>(entries.keySet());
        Collections.sort(sorted);
        this.names = Collections.unmodifiableList(sorted);
    }

    public static JarArchive read(InputStream input) throws IOException {
        Map<String, byte[]> entries = new HashMap<String, byte[]>();
        ZipInputStream zip = new ZipInputStream(input);
        try {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                entries.put(normalize(entry.getName()), out.toByteArray());
            }
        } finally {
            zip.close();
        }
        return new JarArchive(entries);
    }

    public static JarArchive of(Map<String, byte[]> entries) {
        Map<String, byte[]> copy = new HashMap<String, byte[]>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            copy.put(normalize(entry.getKey()), entry.getValue());
        }
        return new JarArchive(copy);
    }

    private static String normalize(String name) {
        String value = name.replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    public boolean contains(String name) {
        return entries.containsKey(normalize(name));
    }

    public byte[] read(String name) {
        byte[] data = entries.get(normalize(name));
        return data == null ? null : data;
    }

    /** Entry names sorted alphabetically; useful for the resource viewer. */
    public List<String> names() {
        return names;
    }

    public int size() {
        return entries.size();
    }

    /** Total uncompressed size in bytes. */
    public long uncompressedSize() {
        long total = 0;
        for (byte[] data : entries.values()) {
            total += data.length;
        }
        return total;
    }

    public List<String> classNames() {
        List<String> classes = new ArrayList<String>();
        for (String name : names) {
            if (name.endsWith(".class")) {
                classes.add(name.substring(0, name.length() - 6).replace('/', '.'));
            }
        }
        return classes;
    }
}
