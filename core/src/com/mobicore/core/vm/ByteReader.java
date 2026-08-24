package com.mobicore.core.vm;

/** Big-endian cursor over a class file. */
final class ByteReader {

    private final byte[] data;
    private int position;

    ByteReader(byte[] data) {
        this.data = data;
    }

    int position() {
        return position;
    }

    void seek(int position) {
        this.position = position;
    }

    void skip(int count) {
        position += count;
    }

    int remaining() {
        return data.length - position;
    }

    int u1() {
        require(1);
        return data[position++] & 0xFF;
    }

    int u2() {
        require(2);
        return ((data[position++] & 0xFF) << 8) | (data[position++] & 0xFF);
    }

    int s2() {
        return (short) u2();
    }

    int u4() {
        require(4);
        return ((data[position++] & 0xFF) << 24)
                | ((data[position++] & 0xFF) << 16)
                | ((data[position++] & 0xFF) << 8)
                | (data[position++] & 0xFF);
    }

    byte[] bytes(int length) {
        require(length);
        byte[] out = new byte[length];
        System.arraycopy(data, position, out, 0, length);
        position += length;
        return out;
    }

    /** Reads a modified-UTF8 string as written by {@code javac}. */
    String utf8(int length) {
        require(length);
        StringBuilder out = new StringBuilder(length);
        int end = position + length;
        while (position < end) {
            int b = data[position++] & 0xFF;
            if (b < 0x80) {
                out.append((char) b);
            } else if ((b & 0xE0) == 0xC0) {
                int b2 = data[position++] & 0x3F;
                out.append((char) (((b & 0x1F) << 6) | b2));
            } else {
                int b2 = data[position++] & 0x3F;
                int b3 = data[position++] & 0x3F;
                out.append((char) (((b & 0x0F) << 12) | (b2 << 6) | b3));
            }
        }
        position = end;
        return out.toString();
    }

    private void require(int count) {
        if (position + count > data.length) {
            throw new VmError("Truncated class file at offset " + position);
        }
    }
}
