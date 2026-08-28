package com.mobicore.core.jar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Đóng một gói .zip, đối xứng với {@link JarArchive} đọc nó.
 *
 * <p>Có vì bản mod riêng của người chơi phải được đóng lại mỗi lần họ thay
 * thêm một tệp: máy ảo lâu nay chỉ biết mở hộp ra, chưa biết đóng hộp lại.</p>
 */
public final class Zip {

    private Zip() {
    }

    /** @param entries đường dẫn bên trong gói, và nội dung của nó */
    public static byte[] write(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out);
        try {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                byte[] data = entry.getValue();
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                if (data != null) {
                    zip.write(data);
                }
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        return out.toByteArray();
    }
}
