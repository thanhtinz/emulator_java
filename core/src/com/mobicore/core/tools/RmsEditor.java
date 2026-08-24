package com.mobicore.core.tools;

import com.mobicore.core.rms.RecordStoreManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer-mode editor for record stores.
 *
 * <p>Every write takes a snapshot of the store first: hand-editing a save is
 * exactly the situation where an undo is needed most.</p>
 */
public final class RmsEditor {

    /** A record rendered for display, in both text and hex. */
    public static final class Record {

        private final int id;
        private final byte[] data;

        Record(int id, byte[] data) {
            this.id = id;
            this.data = data;
        }

        public int id() {
            return id;
        }

        public byte[] data() {
            return data;
        }

        public int size() {
            return data.length;
        }

        /** Printable rendering, with unprintable bytes shown as dots. */
        public String asText() {
            StringBuilder out = new StringBuilder(data.length);
            for (byte b : data) {
                int value = b & 0xFF;
                out.append(value >= 0x20 && value < 0x7F ? (char) value : '.');
            }
            return out.toString();
        }

        public String asHex() {
            StringBuilder out = new StringBuilder(data.length * 3);
            for (int i = 0; i < data.length; i++) {
                if (i > 0) {
                    out.append(i % 16 == 0 ? '\n' : ' ');
                }
                String hex = Integer.toHexString(data[i] & 0xFF).toUpperCase();
                if (hex.length() < 2) {
                    out.append('0');
                }
                out.append(hex);
            }
            return out.toString();
        }

        /** Interprets the first four bytes as a big-endian int, as games do. */
        public int asInt() {
            if (data.length < 4) {
                return 0;
            }
            return ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
                    | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        }
    }

    private final RecordStoreManager manager;
    private final long timestamp;

    public RmsEditor(RecordStoreManager manager, long timestamp) {
        this.manager = manager;
        this.timestamp = timestamp;
    }

    public List<String> stores() {
        return manager.listStoreNames();
    }

    public List<Record> records(String storeName) throws IOException {
        List<Record> records = new ArrayList<Record>();
        RecordStoreManager.Store store = manager.openStore(storeName, false);
        if (store == null) {
            return records;
        }
        for (Integer id : store.recordIds()) {
            records.add(new Record(id.intValue(), store.get(id.intValue())));
        }
        return records;
    }

    /** Replaces a record; returns false when the id does not exist. */
    public boolean setRecord(String storeName, int recordId, byte[] data) throws IOException {
        RecordStoreManager.Store store = manager.openStore(storeName, false);
        if (store == null || !store.set(recordId, data, timestamp)) {
            return false;
        }
        manager.flush(storeName);
        return true;
    }

    public int addRecord(String storeName, byte[] data) throws IOException {
        RecordStoreManager.Store store = manager.openStore(storeName, true);
        int id = store.add(data, timestamp);
        manager.flush(storeName);
        return id;
    }

    public boolean deleteRecord(String storeName, int recordId) throws IOException {
        RecordStoreManager.Store store = manager.openStore(storeName, false);
        if (store == null || !store.delete(recordId, timestamp)) {
            return false;
        }
        manager.flush(storeName);
        return true;
    }

    /** Parses "48 65 6C" style input from the hex editor. */
    public static byte[] parseHex(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (isHex) {
                digits.append(c);
            }
        }
        if (digits.length() % 2 != 0) {
            digits.insert(0, '0');
        }
        byte[] data = new byte[digits.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(digits.substring(i * 2, i * 2 + 2), 16);
        }
        return data;
    }
}
