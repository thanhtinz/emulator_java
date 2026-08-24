package com.mobicore.core.model;

import com.mobicore.core.jar.AttributeSet;
import com.mobicore.core.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Descriptor of an installable suite, assembled from the JAR manifest and the
 * optional JAD file.
 *
 * <p>When both sources are present the JAD wins: that is what the MIDP
 * specification requires for the attributes an operator may rewrite, and it is
 * also what users expect when they hand-edit a descriptor in the JAD editor.</p>
 */
public final class MidletSuiteInfo {

    public static final String ATTR_NAME = "MIDlet-Name";
    public static final String ATTR_VERSION = "MIDlet-Version";
    public static final String ATTR_VENDOR = "MIDlet-Vendor";
    public static final String ATTR_ICON = "MIDlet-Icon";
    public static final String ATTR_DESCRIPTION = "MIDlet-Description";
    public static final String ATTR_JAR_URL = "MIDlet-Jar-URL";
    public static final String ATTR_JAR_SIZE = "MIDlet-Jar-Size";
    public static final String ATTR_CLDC = "MicroEdition-Configuration";
    public static final String ATTR_MIDP = "MicroEdition-Profile";

    private final AttributeSet attributes;
    private final List<MidletEntry> midlets;

    private MidletSuiteInfo(AttributeSet attributes, List<MidletEntry> midlets) {
        this.attributes = attributes;
        this.midlets = Collections.unmodifiableList(midlets);
    }

    /**
     * Merges a manifest and a descriptor. Either argument may be {@code null},
     * which covers both the bare {@code .jar} and the {@code .jad}-only cases.
     */
    public static MidletSuiteInfo merge(AttributeSet manifest, AttributeSet jad) {
        AttributeSet merged = new AttributeSet();
        if (manifest != null) {
            for (String key : manifest.keys()) {
                merged.put(key, manifest.get(key));
            }
        }
        if (jad != null) {
            for (String key : jad.keys()) {
                merged.put(key, jad.get(key));
            }
        }
        return new MidletSuiteInfo(merged, parseMidlets(merged));
    }

    private static List<MidletEntry> parseMidlets(AttributeSet attributes) {
        List<MidletEntry> entries = new ArrayList<MidletEntry>();
        for (int index = 1; index <= 64; index++) {
            String raw = attributes.get("MIDlet-" + index);
            if (Text.isEmpty(raw)) {
                // Suites number their MIDlets from 1 without gaps, but a broken
                // descriptor should not silently hide the remaining entries.
                if (index > 1) {
                    break;
                }
                continue;
            }
            String[] parts = Text.split(raw, ',');
            String name = parts.length > 0 ? Text.orDefault(parts[0], "MIDlet " + index) : "MIDlet " + index;
            String icon = parts.length > 1 ? Text.trimOrNull(parts[1]) : null;
            String className = parts.length > 2 ? Text.trimOrNull(parts[2]) : null;
            if (className == null) {
                continue;
            }
            entries.add(new MidletEntry(index, name, icon, className));
        }
        return entries;
    }

    public AttributeSet attributes() {
        return attributes;
    }

    public List<MidletEntry> midlets() {
        return midlets;
    }

    /** The MIDlet started by default when the user presses Play. */
    public MidletEntry primaryMidlet() {
        return midlets.isEmpty() ? null : midlets.get(0);
    }

    public String title() {
        String name = attributes.get(ATTR_NAME);
        if (!Text.isEmpty(name)) {
            return name.trim();
        }
        MidletEntry primary = primaryMidlet();
        return primary != null ? primary.name() : "Unknown Suite";
    }

    public String vendor() {
        return attributes.get(ATTR_VENDOR, "Unknown");
    }

    public String version() {
        return attributes.get(ATTR_VERSION, "1.0");
    }

    public String description() {
        return attributes.get(ATTR_DESCRIPTION);
    }

    public String iconPath() {
        String icon = attributes.get(ATTR_ICON);
        if (!Text.isEmpty(icon)) {
            return icon.trim();
        }
        MidletEntry primary = primaryMidlet();
        return primary != null ? primary.iconPath() : null;
    }

    public String configuration() {
        return attributes.get(ATTR_CLDC, "CLDC-1.1");
    }

    public String profile() {
        return attributes.get(ATTR_MIDP, "MIDP-2.0");
    }

    /** Stable identifier used for the per-game sandbox directory. */
    public String suiteId() {
        return Text.slug(vendor()) + "." + Text.slug(title()) + "." + Text.slug(version());
    }

    public boolean isValid() {
        return !midlets.isEmpty();
    }
}
