package javax.microedition.io.file;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.microedition.io.Connection;

/** Compile-time stub; the emulator implements this natively. */
public interface FileConnection extends Connection {

    boolean exists();

    boolean isDirectory();

    boolean canRead();

    boolean canWrite();

    boolean isHidden();

    long fileSize() throws IOException;

    long directorySize(boolean includeSubDirs) throws IOException;

    long lastModified();

    void create() throws IOException;

    void mkdir() throws IOException;

    void delete() throws IOException;

    void truncate(long byteOffset) throws IOException;

    void rename(String newName) throws IOException;

    Enumeration list() throws IOException;

    Enumeration list(String filter, boolean includeHidden) throws IOException;

    String getName();

    String getPath();

    String getURL();

    void setFileConnection(String fileName) throws IOException;

    InputStream openInputStream() throws IOException;

    DataInputStream openDataInputStream() throws IOException;

    OutputStream openOutputStream() throws IOException;

    OutputStream openOutputStream(long byteOffset) throws IOException;

    DataOutputStream openDataOutputStream() throws IOException;

    long availableSize();

    long totalSize();

    long usedSize();

    boolean isOpen();

    void close() throws IOException;
}
