package com.mobicore.tests;

import com.mobicore.core.jar.JarArchive;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JarArchiveTest extends Test {

    @Override
    public String name() {
        return "JAR archive reader";
    }

    @Override
    public void run() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("META-INF/MANIFEST.MF", Fixtures.utf8(Fixtures.sampleManifest()));
        entries.put("demo/SkyRunner.class", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        entries.put("demo/Editor.class", new byte[]{1, 2, 3});
        entries.put("icon.png", new byte[64]);
        entries.put("res/level1.dat", new byte[128]);

        JarArchive archive = JarArchive.read(new ByteArrayInputStream(Fixtures.jar(entries)));
        eq(5, archive.size(), "every entry is read");
        check(archive.contains("icon.png"), "contains finds an entry");
        check(archive.contains("/icon.png"), "leading slash is normalised away");
        check(!archive.contains("missing.png"), "contains rejects absent entries");
        eq(128, archive.read("res/level1.dat").length, "entry payload survives round-trip");
        eq(null, archive.read("nope"), "missing entry reads as null");

        eq(2, archive.classNames().size(), "class list only contains .class entries");
        eq("demo.Editor", archive.classNames().get(0), "class names are dotted and sorted");
        check(archive.uncompressedSize() > 190, "uncompressed size sums the payloads");
        eq("META-INF/MANIFEST.MF", archive.names().get(0), "names are sorted");
    }
}
