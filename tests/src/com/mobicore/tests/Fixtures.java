package com.mobicore.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds synthetic MIDlet suites so tests never depend on copyrighted games. */
public final class Fixtures {

    private Fixtures() {
    }

    public static byte[] jar(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out);
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            zip.putNextEntry(new ZipEntry(entry.getKey()));
            zip.write(entry.getValue());
            zip.closeEntry();
        }
        zip.close();
        return out.toByteArray();
    }

    public static byte[] utf8(String text) {
        try {
            return text.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sampleManifest() {
        return "Manifest-Version: 1.0\n"
                + "MIDlet-Name: Sky Runner\n"
                + "MIDlet-Version: 1.2.0\n"
                + "MIDlet-Vendor: MobiCore Samples\n"
                + "MIDlet-1: Sky Runner,/icon.png,demo.SkyRunner\n"
                + "MIDlet-2: Sky Runner Editor,,demo.Editor\n"
                + "MicroEdition-Configuration: CLDC-1.1\n"
                + "MicroEdition-Profile: MIDP-2.0\n";
    }
}
