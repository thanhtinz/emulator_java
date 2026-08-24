package com.mobicore.core.storage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** {@link Vfs} backed by the platform filesystem. */
public final class LocalVfs implements Vfs {

    @Override
    public boolean exists(String path) {
        return new File(path).exists();
    }

    @Override
    public boolean isDirectory(String path) {
        return new File(path).isDirectory();
    }

    @Override
    public long size(String path) {
        return new File(path).length();
    }

    @Override
    public byte[] read(String path) throws IOException {
        InputStream in = new FileInputStream(path);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    @Override
    public void write(String path, byte[] data) throws IOException {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory " + parent.getPath());
        }
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    @Override
    public void mkdirs(String path) throws IOException {
        File file = new File(path);
        if (!file.exists() && !file.mkdirs()) {
            throw new IOException("Cannot create directory " + path);
        }
    }

    @Override
    public List<String> list(String path) {
        String[] children = new File(path).list();
        if (children == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>(Arrays.asList(children));
        Collections.sort(result);
        return result;
    }

    @Override
    public boolean delete(String path) {
        return deleteRecursive(new File(path));
    }

    private static boolean deleteRecursive(File file) {
        if (!file.exists()) {
            return false;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        return file.delete();
    }

    @Override
    public void copy(String from, String to) throws IOException {
        write(to, read(from));
    }
}
