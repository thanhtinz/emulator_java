package com.mobicore.tests;

import com.mobicore.core.jar.AttributeSet;

public final class AttributeSetTest extends Test {

    @Override
    public String name() {
        return "Manifest/JAD attributes";
    }

    @Override
    public void run() {
        String manifest = "Manifest-Version: 1.0\r\n"
                + "MIDlet-Name: Bounce Tales\r\n"
                + "MIDlet-Vendor: Nokia\r\n"
                + "MIDlet-1: Bounce Tales,/icon.png,com.nokia.bounce.Main\r\n"
                + "MIDlet-Description: A very long description that the packaging\r\n"
                + "  tool wrapped across two lines\r\n"
                + "MicroEdition-Profile: MIDP-2.0\r\n";

        AttributeSet set = AttributeSet.parse(manifest);
        eq("Bounce Tales", set.get("MIDlet-Name"), "reads a plain attribute");
        eq("Nokia", set.get("midlet-vendor"), "lookup is case-insensitive");
        eq("A very long description that the packaging tool wrapped across two lines",
                set.get("MIDlet-Description"), "joins continuation lines");
        check(set.has("MicroEdition-Profile"), "has() finds present keys");
        check(!set.has("MIDlet-Jar-URL"), "has() rejects absent keys");

        eq("Manifest-Version", set.keys().get(0), "iteration order is preserved");
        check(set.toDescriptor().indexOf("MIDlet-Name: Bounce Tales") >= 0, "round-trips to descriptor syntax");

        set.put("MIDlet-Name", "Bounce Tales HD");
        eq("Bounce Tales HD", set.get("MIDlet-Name"), "put overwrites");
        eq(6, set.size(), "overwrite does not add a duplicate key");
        set.remove("MIDlet-Vendor");
        check(!set.has("MIDlet-Vendor"), "remove deletes the key");

        AttributeSet utf8 = AttributeSet.parse(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
                'A', ':', ' ', 'B'});
        eq("B", utf8.get("A"), "byte order mark is skipped");
    }
}
