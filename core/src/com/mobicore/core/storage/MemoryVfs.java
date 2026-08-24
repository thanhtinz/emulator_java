package com.mobicore.core.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** In-memory {@link Vfs}, used by tests and by the JAD editor preview. */
public final class MemoryVfs implements Vfs {

    private final Map<String, byte[]> files = new TreeMap<String, byte[]>();
    private final Set<String> directories = new HashSet<String>();

    private static String norm(String path) {
        String value = path == null ? "" : path.replace('\\', '/');
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    @Override
    public boolean exists(String path) {
        String key = norm(path);
        return files.containsKey(key) || directories.contains(key);
    }

    @Override
    public boolean isDirectory(String path) {
        return directories.contains(norm(path));
    }

    @Override
    public long size(String path) {
        byte[] data = files.get(norm(path));
        return data == null ? 0 : data.length;
    }

    @Override
    public byte[] read(String path) throws IOException {
        byte[] data = files.get(norm(path));
        if (data == null) {
            throw new FileNotFoundException(path);
        }
        byte[] copy = new byte[data.length];
        System.arraycopy(data, 0, copy, 0, data.length);
        return copy;
    }

    @Override
    public void write(String path, byte[] data) throws IOException {
        String key = norm(path);
        int slash = key.lastIndexOf('/');
        if (slash > 0) {
            mkdirs(key.substring(0, slash));
        }
        byte[] copy = new byte[data.length];
        System.arraycopy(data, 0, copy, 0, data.length);
        files.put(key, copy);
    }

    @Override
    public void mkdirs(String path) {
        String key = norm(path);
        boolean absolute = key.startsWith("/");
        StringBuilder current = new StringBuilder(absolute ? "/" : "");
        for (String part : key.split("/")) {
            if (part.length() == 0) {
                continue;
            }
            if (current.length() > 0 && current.charAt(current.length() - 1) != '/') {
                current.append('/');
            }
            current.append(part);
            directories.add(current.toString());
        }
    }

    @Override
    public List<String> list(String path) {
        String prefix = norm(path) + "/";
        Set<String> children = new HashSet<String>();
        for (String key : files.keySet()) {
            addChild(children, key, prefix);
        }
        for (String key : directories) {
            addChild(children, key, prefix);
        }
        List<String> result = new ArrayList<String>(children);
        Collections.sort(result);
        return result;
    }

    private static void addChild(Set<String> out, String key, String prefix) {
        if (!key.startsWith(prefix)) {
            return;
        }
        String rest = key.substring(prefix.length());
        int slash = rest.indexOf('/');
        out.add(slash < 0 ? rest : rest.substring(0, slash));
    }

    @Override
    public boolean delete(String path) {
        String key = norm(path);
        String prefix = key + "/";
        boolean removed = files.remove(key) != null;
        removed |= directories.remove(key);
        List<String> doomed = new ArrayList<String>();
        for (String name : files.keySet()) {
            if (name.startsWith(prefix)) {
                doomed.add(name);
            }
        }
        for (String name : doomed) {
            files.remove(name);
            removed = true;
        }
        doomed.clear();
        for (String name : directories) {
            if (name.startsWith(prefix)) {
                doomed.add(name);
            }
        }
        for (String name : doomed) {
            directories.remove(name);
            removed = true;
        }
        return removed;
    }

    @Override
    public void copy(String from, String to) throws IOException {
        write(to, read(from));
    }
}
