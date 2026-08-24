package com.mobicore.core.tools;

import com.mobicore.core.jar.AttributeSet;
import com.mobicore.core.model.MidletSuiteInfo;
import com.mobicore.core.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Edits a suite descriptor and checks it before it is written back.
 *
 * <p>A JAD with a broken {@code MIDlet-1} line produces a game that installs
 * and then refuses to start, which is a confusing failure. Validating here
 * turns it into a message the user can act on.</p>
 */
public final class JadEditor {

    /** One problem found in the descriptor. */
    public static final class Problem {

        public static final int ERROR = 0;
        public static final int WARNING = 1;

        private final int severity;
        private final String attribute;
        private final String message;

        Problem(int severity, String attribute, String message) {
            this.severity = severity;
            this.attribute = attribute;
            this.message = message;
        }

        public int severity() {
            return severity;
        }

        public boolean isError() {
            return severity == ERROR;
        }

        public String attribute() {
            return attribute;
        }

        public String message() {
            return message;
        }

        @Override
        public String toString() {
            return (isError() ? "error" : "warning") + ": " + attribute + " — " + message;
        }
    }

    private final AttributeSet attributes;

    public JadEditor(AttributeSet attributes) {
        this.attributes = attributes == null ? new AttributeSet() : attributes;
    }

    public static JadEditor parse(byte[] descriptor) {
        return new JadEditor(AttributeSet.parse(descriptor));
    }

    public AttributeSet attributes() {
        return attributes;
    }

    public List<String> keys() {
        return attributes.keys();
    }

    public String get(String key) {
        return attributes.get(key);
    }

    public void set(String key, String value) {
        if (Text.isEmpty(key)) {
            return;
        }
        attributes.put(key.trim(), value == null ? "" : value.trim());
    }

    public void remove(String key) {
        attributes.remove(key);
    }

    /** The descriptor as text, ready to write back to disk. */
    public String toDescriptor() {
        return attributes.toDescriptor();
    }

    public byte[] toBytes() {
        try {
            return toDescriptor().getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return toDescriptor().getBytes();
        }
    }

    /** Checks the descriptor; an empty list means it is ready to save. */
    public List<Problem> validate() {
        List<Problem> problems = new ArrayList<Problem>();
        requireNonEmpty(problems, MidletSuiteInfo.ATTR_NAME, "A suite needs a name");
        requireNonEmpty(problems, MidletSuiteInfo.ATTR_VENDOR, "A suite needs a vendor");
        requireNonEmpty(problems, MidletSuiteInfo.ATTR_VERSION, "A suite needs a version");

        String version = attributes.get(MidletSuiteInfo.ATTR_VERSION);
        if (!Text.isEmpty(version) && !looksLikeVersion(version)) {
            problems.add(new Problem(Problem.WARNING, MidletSuiteInfo.ATTR_VERSION,
                    "Expected a form like 1.0 or 1.2.3, found \"" + version + "\""));
        }

        if (Text.isEmpty(attributes.get("MIDlet-1"))) {
            problems.add(new Problem(Problem.ERROR, "MIDlet-1",
                    "At least one MIDlet entry is required"));
        }
        for (int index = 1; index <= 64; index++) {
            String raw = attributes.get("MIDlet-" + index);
            if (Text.isEmpty(raw)) {
                break;
            }
            String[] parts = Text.split(raw, ',');
            if (parts.length < 3 || Text.isEmpty(parts[2])) {
                problems.add(new Problem(Problem.ERROR, "MIDlet-" + index,
                        "Expected name,icon,class but the class is missing"));
            } else if (parts[2].trim().indexOf(' ') >= 0) {
                problems.add(new Problem(Problem.ERROR, "MIDlet-" + index,
                        "The class name contains a space"));
            }
        }

        String size = attributes.get(MidletSuiteInfo.ATTR_JAR_SIZE);
        if (!Text.isEmpty(size)) {
            try {
                if (Integer.parseInt(size.trim()) <= 0) {
                    problems.add(new Problem(Problem.WARNING, MidletSuiteInfo.ATTR_JAR_SIZE,
                            "The declared JAR size is not positive"));
                }
            } catch (NumberFormatException e) {
                problems.add(new Problem(Problem.ERROR, MidletSuiteInfo.ATTR_JAR_SIZE,
                        "The declared JAR size is not a number"));
            }
        }

        if (Text.isEmpty(attributes.get(MidletSuiteInfo.ATTR_CLDC))) {
            problems.add(new Problem(Problem.WARNING, MidletSuiteInfo.ATTR_CLDC,
                    "No configuration declared; CLDC-1.1 will be assumed"));
        }
        return problems;
    }

    public boolean isValid() {
        for (Problem problem : validate()) {
            if (problem.isError()) {
                return false;
            }
        }
        return true;
    }

    /** Applies the declared JAR size and URL for a JAR that is actually present. */
    public void syncWithJar(String jarUrl, int jarSize) {
        set(MidletSuiteInfo.ATTR_JAR_URL, jarUrl);
        set(MidletSuiteInfo.ATTR_JAR_SIZE, String.valueOf(jarSize));
    }

    private void requireNonEmpty(List<Problem> problems, String key, String message) {
        if (Text.isEmpty(attributes.get(key))) {
            problems.add(new Problem(Problem.ERROR, key, message));
        }
    }

    private static boolean looksLikeVersion(String value) {
        String trimmed = value.trim();
        if (trimmed.length() == 0) {
            return false;
        }
        boolean digitSeen = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= '0' && c <= '9') {
                digitSeen = true;
            } else if (c != '.') {
                return false;
            }
        }
        return digitSeen;
    }
}
