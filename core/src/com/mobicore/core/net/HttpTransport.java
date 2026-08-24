package com.mobicore.core.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * Real HTTP, over the platform's own stack.
 *
 * <p>{@code HttpURLConnection} is available on Android and is mapped onto
 * {@code NSURLSession} by J2ObjC, so one implementation serves both mobile
 * targets.</p>
 */
public final class HttpTransport implements NetworkTransport {

    @Override
    public Response execute(Request request) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(request.url).openConnection();
        try {
            connection.setRequestMethod(request.method);
            connection.setConnectTimeout(request.timeoutMs);
            connection.setReadTimeout(request.timeoutMs);
            connection.setInstanceFollowRedirects(true);
            for (Map.Entry<String, String> header : request.headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if (request.body != null && request.body.length > 0) {
                connection.setDoOutput(true);
                OutputStream out = connection.getOutputStream();
                try {
                    out.write(request.body);
                } finally {
                    out.close();
                }
            }

            int status = connection.getResponseCode();
            // A 4xx or 5xx body arrives on the error stream, and games read it.
            InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] body = in == null ? new byte[0] : readAll(in);
            Response response = new Response(status, connection.getResponseMessage(), body);
            for (int i = 0; ; i++) {
                String name = connection.getHeaderFieldKey(i);
                String value = connection.getHeaderField(i);
                if (name == null && value == null) {
                    break;
                }
                if (name != null) {
                    response.headers.put(name, value);
                }
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
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
}
