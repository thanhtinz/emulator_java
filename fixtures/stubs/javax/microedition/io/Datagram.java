package javax.microedition.io;

import java.io.IOException;

/** Compile-time stub; the emulator implements this natively. */
public interface Datagram {

    byte[] getData();

    int getLength();

    int getOffset();

    void setLength(int length);

    void setData(byte[] buffer, int offset, int length);

    void reset();

    String getAddress();

    void setAddress(String address) throws IOException;

    void setAddress(Datagram reference) throws IOException;

    void write(int value) throws IOException;

    void write(byte[] buffer, int offset, int length) throws IOException;

    void writeByte(int value) throws IOException;

    void writeShort(int value) throws IOException;

    void writeInt(int value) throws IOException;

    void writeUTF(String value) throws IOException;

    byte readByte() throws IOException;

    int readUnsignedByte() throws IOException;

    short readShort() throws IOException;

    int readInt() throws IOException;

    String readUTF() throws IOException;
}
