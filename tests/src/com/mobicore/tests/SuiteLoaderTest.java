package com.mobicore.tests;

import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.model.MidletSuiteInfo;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SuiteLoaderTest extends Test {

    @Override
    public String name() {
        return "Suite metadata";
    }

    @Override
    public void run() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("META-INF/MANIFEST.MF", Fixtures.utf8(Fixtures.sampleManifest()));
        entries.put("icon.png", new byte[]{9, 9, 9});
        entries.put("demo/SkyRunner.class", new byte[]{0});
        byte[] jar = Fixtures.jar(entries);

        SuiteLoader plain = SuiteLoader.load(jar, null);
        MidletSuiteInfo info = plain.info();
        eq("Sky Runner", info.title(), "title comes from MIDlet-Name");
        eq("MobiCore Samples", info.vendor(), "vendor is read");
        eq("1.2.0", info.version(), "version is read");
        eq("CLDC-1.1", info.configuration(), "configuration is read");
        eq(2, info.midlets().size(), "both MIDlet entries are parsed");
        eq("demo.SkyRunner", info.primaryMidlet().className(), "primary MIDlet is entry 1");
        eq("/icon.png", info.primaryMidlet().iconPath(), "icon path is kept verbatim");
        eq("Sky Runner Editor", info.midlets().get(1).name(), "second entry keeps its name");
        eq(null, info.midlets().get(1).iconPath(), "missing icon becomes null");
        check(info.isValid(), "a suite with MIDlets is valid");
        eq("mobicore-samples.sky-runner.1-2-0", info.suiteId(), "suite id is sandbox-safe");
        eqBytes(new byte[]{9, 9, 9}, plain.iconBytes(), "icon bytes are pulled from the JAR");

        byte[] jad = Fixtures.utf8("MIDlet-Name: Sky Runner Deluxe\n"
                + "MIDlet-Vendor: MobiCore Samples\n"
                + "MIDlet-Jar-URL: SkyRunner.jar\n"
                + "MIDlet-Jar-Size: 40960\n"
                + "MIDlet-1: Sky Runner,/icon.png,demo.SkyRunner\n");
        SuiteLoader withJad = SuiteLoader.load(jar, jad);
        eq("Sky Runner Deluxe", withJad.info().title(), "JAD overrides the manifest");
        eq("1.2.0", withJad.info().version(), "manifest fills gaps the JAD leaves");
        eq("40960", withJad.info().attributes().get("MIDlet-Jar-Size"), "JAD-only attributes survive");
        check(withJad.jad() != null, "the raw descriptor stays available for the editor");

        MidletSuiteInfo jadOnly = SuiteLoader.describe(jad);
        eq("Sky Runner Deluxe", jadOnly.title(), "a stand-alone JAD still describes the suite");
        eq("SkyRunner.jar", jadOnly.attributes().get(MidletSuiteInfo.ATTR_JAR_URL), "JAR URL is exposed");

        Map<String, byte[]> bare = new LinkedHashMap<String, byte[]>();
        bare.put("Main.class", new byte[]{0});
        MidletSuiteInfo empty = SuiteLoader.load(Fixtures.jar(bare), null).info();
        check(!empty.isValid(), "a JAR without MIDlet entries is rejected");
        eq("Unknown Suite", empty.title(), "invalid suites still render a title");
    }
}
