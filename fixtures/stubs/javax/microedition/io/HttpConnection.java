package javax.microedition.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Compile-time stub; the emulator implements this natively. */
public interface HttpConnection extends ContentConnection {

    String GET = "GET";
    String POST = "POST";
    String HEAD = "HEAD";

    int HTTP_OK = 200;
    int HTTP_NOT_FOUND = 404;
    int HTTP_INTERNAL_ERROR = 500;

    void setRequestMethod(String method) throws IOException;

    String getRequestMethod();

    void setRequestProperty(String key, String value) throws IOException;

    String getRequestProperty(String key);

    String getURL();

    String getHost();

    int getResponseCode() throws IOException;

    String getResponseMessage() throws IOException;

    String getHeaderField(String name) throws IOException;

    InputStream openInputStream() throws IOException;

    DataInputStream openDataInputStream() throws IOException;

    OutputStream openOutputStream() throws IOException;

    DataOutputStream openDataOutputStream() throws IOException;
}
