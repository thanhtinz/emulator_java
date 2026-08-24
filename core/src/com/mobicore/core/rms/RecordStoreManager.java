package com.mobicore.core.rms;

import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent record stores for one suite.
 *
 * <p>Each suite gets its own directory under {@code rms/}, so one game can
 * never read or overwrite another's save — the sandbox guarantee the
 * specification makes. Stores are held in memory while a game runs and flushed
 * on close, which keeps a game that writes a record per frame from hammering
 * flash storage.</p>
 */
public final class RecordStoreManager {

    /** A single record store: numbered records plus its metadata. */
    public static final class Store {

        private final String name;
        private final Map<Integer, byte[]> records = new LinkedHashMap<Integer, byte[]>();
        private int nextRecordId = 1;
        private int version;
        private long lastModified;
        private boolean dirty;

        Store(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public int version() {
            return version;
        }

        public long lastModified() {
            return lastModified;
        }

        public int nextRecordId() {
            return nextRecordId;
        }

        public int size() {
            return records.size();
        }

        /** Total bytes used by the records, as {@code getSize} reports. */
        public int byteSize() {
            int total = 0;
            for (byte[] data : records.values()) {
                total += data.length;
            }
            return total;
        }

        public List<Integer> recordIds() {
            List<Integer> ids = new ArrayList<Integer>(records.keySet());
            Collections.sort(ids);
            return ids;
        }

        public byte[] get(int recordId) {
            return records.get(Integer.valueOf(recordId));
        }

        public int add(byte[] data, long timestamp) {
            int id = nextRecordId++;
            records.put(Integer.valueOf(id), copy(data));
            touch(timestamp);
            return id;
        }

        public boolean set(int recordId, byte[] data, long timestamp) {
            if (!records.containsKey(Integer.valueOf(recordId))) {
                return false;
            }
            records.put(Integer.valueOf(recordId), copy(data));
            touch(timestamp);
            return true;
        }

        public boolean delete(int recordId, long timestamp) {
            if (records.remove(Integer.valueOf(recordId)) == null) {
                return false;
            }
            touch(timestamp);
            return true;
        }

        private void touch(long timestamp) {
            version++;
            lastModified = timestamp;
            dirty = true;
        }

        private static byte[] copy(byte[] data) {
            if (data == null) {
                return new byte[0];
            }
            byte[] out = new byte[data.length];
            System.arraycopy(data, 0, out, 0, data.length);
            return out;
        }
    }

    /** File format marker, so a future change can be detected rather than guessed. */
    private static final int MAGIC = 0x4D43524D;
    private static final int FORMAT_VERSION = 1;

    private final Vfs vfs;
    private final StorageLayout layout;
    private final String suiteId;
    private final Map<String, Store> open = new LinkedHashMap<String, Store>();

    public RecordStoreManager(Vfs vfs, StorageLayout layout, String suiteId) {
        this.vfs = vfs;
        this.layout = layout;
        this.suiteId = suiteId;
    }

    public String suiteId() {
        return suiteId;
    }

    private String directory() {
        return layout.rmsDir(suiteId);
    }

    private String pathFor(String storeName) {
        return StorageLayout.join(directory(), encodeName(storeName) + ".rms");
    }

    /** Store names may contain characters a filesystem rejects. */
    private static String encodeName(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean safe = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (safe) {
                out.append(c);
            } else {
                out.append('%').append(Integer.toHexString(c));
            }
        }
        return out.length() == 0 ? "_" : out.toString();
    }

    /** Names of every store this suite has on disk. */
    public List<String> listStoreNames() {
        List<String> names = new ArrayList<String>();
        for (String file : vfs.list(directory())) {
            if (!file.endsWith(".rms")) {
                continue;
            }
            try {
                Store store = load(StorageLayout.join(directory(), file));
                if (store != null) {
                    names.add(store.name());
                }
            } catch (IOException e) {
                // A corrupt store must not hide the healthy ones.
            }
        }
        Collections.sort(names);
        return names;
    }

    public boolean exists(String storeName) {
        return open.containsKey(storeName) || vfs.exists(pathFor(storeName));
    }

    /**
     * Opens a store, creating it when asked.
     *
     * @return the store, or {@code null} when it does not exist and
     *         {@code createIfMissing} is false
     */
    public Store openStore(String storeName, boolean createIfMissing) throws IOException {
        Store cached = open.get(storeName);
        if (cached != null) {
            return cached;
        }
        String path = pathFor(storeName);
        Store store;
        if (vfs.exists(path)) {
            store = load(path);
        } else if (createIfMissing) {
            store = new Store(storeName);
            store.dirty = true;
        } else {
            return null;
        }
        open.put(storeName, store);
        return store;
    }

    public void flush(String storeName) throws IOException {
        Store store = open.get(storeName);
        if (store != null && store.dirty) {
            save(store);
            store.dirty = false;
        }
    }

    public void flushAll() throws IOException {
        for (String name : new ArrayList<String>(open.keySet())) {
            flush(name);
        }
    }

    public void close(String storeName) throws IOException {
        flush(storeName);
        open.remove(storeName);
    }

    public boolean deleteStore(String storeName) throws IOException {
        open.remove(storeName);
        return vfs.delete(pathFor(storeName));
    }

    /** Removes every store for the suite; used by "clear game data". */
    public boolean deleteAll() {
        open.clear();
        return vfs.delete(directory());
    }

    // --------------------------------------------------------- file format

    private Store load(String path) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(vfs.read(path)));
        try {
            if (in.readInt() != MAGIC) {
                throw new IOException("Not a MobiCore record store: " + path);
            }
            int format = in.readInt();
            if (format != FORMAT_VERSION) {
                throw new IOException("Unsupported record store format " + format);
            }
            Store store = new Store(in.readUTF());
            store.version = in.readInt();
            store.lastModified = in.readLong();
            store.nextRecordId = in.readInt();
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int id = in.readInt();
                byte[] data = new byte[in.readInt()];
                in.readFully(data);
                store.records.put(Integer.valueOf(id), data);
            }
            return store;
        } finally {
            in.close();
        }
    }

    private void save(Store store) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeInt(FORMAT_VERSION);
        out.writeUTF(store.name());
        out.writeInt(store.version);
        out.writeLong(store.lastModified);
        out.writeInt(store.nextRecordId);
        out.writeInt(store.records.size());
        for (Map.Entry<Integer, byte[]> entry : store.records.entrySet()) {
            out.writeInt(entry.getKey().intValue());
            out.writeInt(entry.getValue().length);
            out.write(entry.getValue());
        }
        out.flush();
        vfs.write(pathFor(store.name()), bytes.toByteArray());
    }

    // ------------------------------------------------------ backup/restore

    /**
     * Bundles every store into one archive so a save can be exported, or
     * snapshotted before a reset — which the specification requires.
     */
    public byte[] exportAll() throws IOException {
        flushAll();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeInt(FORMAT_VERSION);
        out.writeUTF(suiteId);
        List<String> files = new ArrayList<String>();
        for (String file : vfs.list(directory())) {
            if (file.endsWith(".rms")) {
                files.add(file);
            }
        }
        out.writeInt(files.size());
        for (String file : files) {
            byte[] data = vfs.read(StorageLayout.join(directory(), file));
            out.writeUTF(file);
            out.writeInt(data.length);
            out.write(data);
        }
        out.flush();
        return bytes.toByteArray();
    }

    /** Restores an archive produced by {@link #exportAll()}. */
    public int importAll(byte[] archive, boolean replaceExisting) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(archive));
        try {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT_VERSION) {
                throw new IOException("Not a MobiCore record store backup");
            }
            in.readUTF(); // originating suite id, kept for diagnostics
            if (replaceExisting) {
                vfs.delete(directory());
            }
            open.clear();
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String file = in.readUTF();
                byte[] data = new byte[in.readInt()];
                in.readFully(data);
                vfs.write(StorageLayout.join(directory(), file), data);
            }
            return count;
        } finally {
            in.close();
        }
    }
}
