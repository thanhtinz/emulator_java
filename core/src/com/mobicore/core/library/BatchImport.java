package com.mobicore.core.library;

import com.mobicore.core.jar.JarArchive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports a pile of files at once.
 *
 * <p>Nobody with a J2ME collection has one game. They have a folder of eighty,
 * often as {@code .jar} and {@code .jad} pairs, often inside a zip someone
 * shared years ago. Importing those one at a time is not a chore, it is a
 * reason not to bother.</p>
 *
 * <p>Every file is reported on individually. One broken download in a folder
 * of eighty must not stop the other seventy-nine, and the user is told which
 * one it was rather than left to work out what is missing.</p>
 */
public final class BatchImport {

    /** What happened to one file. */
    public static final class Outcome {

        public static final int INSTALLED = 0;
        public static final int REPLACED = 1;
        public static final int FAILED = 2;
        /** A descriptor with no JAR beside it, or a file that is not a game. */
        public static final int SKIPPED = 3;

        private final String name;
        private final int status;
        private final String detail;

        Outcome(String name, int status, String detail) {
            this.name = name;
            this.status = status;
            this.detail = detail;
        }

        public String name() {
            return name;
        }

        public int status() {
            return status;
        }

        /** The game's title when it installed, or why it did not. */
        public String detail() {
            return detail;
        }
    }

    /** Everything that happened, and the counts worth showing. */
    public static final class Report {

        private final List<Outcome> outcomes;

        Report(List<Outcome> outcomes) {
            this.outcomes = outcomes;
        }

        public List<Outcome> outcomes() {
            return outcomes;
        }

        public int count(int status) {
            int total = 0;
            for (int i = 0; i < outcomes.size(); i++) {
                if (outcomes.get(i).status() == status) {
                    total++;
                }
            }
            return total;
        }

        public int installed() {
            return count(Outcome.INSTALLED) + count(Outcome.REPLACED);
        }

        /** One line for the user: what went in, and what did not. */
        public String summary() {
            int ok = installed();
            int failed = count(Outcome.FAILED);
            int skipped = count(Outcome.SKIPPED);
            StringBuilder text = new StringBuilder();
            text.append("Đã nhập ").append(ok).append(" trò chơi");
            if (failed > 0) {
                text.append(", ").append(failed).append(" tệp lỗi");
            }
            if (skipped > 0) {
                text.append(", bỏ qua ").append(skipped);
            }
            return text.toString();
        }
    }

    private BatchImport() {
    }

    /**
     * Imports every game among {@code files}.
     *
     * @param names file names as the picker gave them, used to pair a
     *     descriptor with its archive and to name a file in the report
     * @param payloads the bytes, in the same order
     */
    public static Report run(GameLibrary library, String[] names, byte[][] payloads) {
        List<Outcome> outcomes = new ArrayList<Outcome>();
        Map<String, byte[]> descriptors = new LinkedHashMap<String, byte[]>();
        List<String> jarNames = new ArrayList<String>();
        List<byte[]> jars = new ArrayList<byte[]>();

        // Sort the pile first: a descriptor is only useful once its archive
        // is in hand, and the picker gives them in whatever order it likes.
        for (int i = 0; i < names.length && i < payloads.length; i++) {
            String name = names[i] == null ? "" : names[i];
            byte[] data = payloads[i];
            if (data == null || data.length == 0) {
                outcomes.add(new Outcome(name, Outcome.FAILED, "Tệp rỗng"));
                continue;
            }
            if (looksLikeDescriptor(name, data)) {
                descriptors.put(baseName(name), data);
            } else if (isZip(data)) {
                expand(name, data, jarNames, jars, outcomes);
            } else {
                outcomes.add(new Outcome(name, Outcome.SKIPPED, "Không phải trò chơi J2ME"));
            }
        }

        for (int i = 0; i < jars.size(); i++) {
            String name = jarNames.get(i);
            byte[] jad = descriptors.remove(baseName(name));
            try {
                GameLibrary.InstallResult result = library.install(jars.get(i), jad);
                outcomes.add(new Outcome(name,
                        result.replaced() ? Outcome.REPLACED : Outcome.INSTALLED,
                        result.entry().title()));
            } catch (IOException e) {
                outcomes.add(new Outcome(name, Outcome.FAILED, reason(e)));
            } catch (RuntimeException e) {
                // A malformed archive can fail in more ways than an IOException
                // covers, and one bad file must not end the batch.
                outcomes.add(new Outcome(name, Outcome.FAILED, reason(e)));
            }
        }

        // A descriptor whose archive was not picked cannot install anything;
        // saying so is more use than silence.
        for (Map.Entry<String, byte[]> left : descriptors.entrySet()) {
            outcomes.add(new Outcome(left.getKey() + ".jad", Outcome.SKIPPED,
                    "Thiếu tệp .jar đi kèm"));
        }
        return new Report(outcomes);
    }

    /**
     * Adds a zip's contents to the pile.
     *
     * <p>A JAR is itself a zip, so the test is what is inside: a game has a
     * manifest, and a collection has games. A zip of zips is not unpacked
     * further — that is a folder someone should unpack themselves, and
     * recursing into archives is how a file picker turns into a file
     * manager.</p>
     */
    private static void expand(String name, byte[] data, List<String> jarNames,
                               List<byte[]> jars, List<Outcome> outcomes) {
        JarArchive archive;
        try {
            archive = JarArchive.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            outcomes.add(new Outcome(name, Outcome.FAILED, reason(e)));
            return;
        }
        if (archive.contains("META-INF/MANIFEST.MF")) {
            jarNames.add(name);
            jars.add(data);
            return;
        }
        List<String> entries = archive.names();
        boolean found = false;
        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            String lower = entry.toLowerCase();
            if (lower.endsWith(".jar")) {
                byte[] inner = archive.read(entry);
                if (inner != null && inner.length > 0) {
                    jarNames.add(entry);
                    jars.add(inner);
                    found = true;
                }
            }
        }
        if (!found) {
            outcomes.add(new Outcome(name, Outcome.SKIPPED, "Không phải trò chơi J2ME"));
        }
    }

    /** A JAD is text; a JAR begins with the zip signature. */
    private static boolean looksLikeDescriptor(String name, byte[] data) {
        if (isZip(data)) {
            return false;
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".jad")) {
            return true;
        }
        // Some pickers hand over a name with no extension at all, so the
        // content decides: a descriptor states the MIDlet's name.
        String head = new String(data, 0, Math.min(data.length, 512)).toLowerCase();
        return head.indexOf("midlet-name:") >= 0 || head.indexOf("midlet-jar-url:") >= 0;
    }

    private static boolean isZip(byte[] data) {
        return data.length > 4 && data[0] == 'P' && data[1] == 'K'
                && (data[2] == 3 || data[2] == 5 || data[2] == 7);
    }

    /** The name without its extension, which is how a pair is recognised. */
    private static String baseName(String name) {
        String out = name;
        int slash = Math.max(out.lastIndexOf('/'), out.lastIndexOf('\\'));
        if (slash >= 0) {
            out = out.substring(slash + 1);
        }
        int dot = out.lastIndexOf('.');
        if (dot > 0) {
            out = out.substring(0, dot);
        }
        return out.toLowerCase();
    }

    private static String reason(Exception e) {
        String message = e.getMessage();
        return message == null || message.length() == 0 ? "Tệp hỏng" : message;
    }
}
