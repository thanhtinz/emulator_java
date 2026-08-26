package com.mobicore.tests;

import com.mobicore.core.bridge.MobiCoreFacade;
import com.mobicore.core.library.BatchImport;
import com.mobicore.core.library.GameLibrary;
import com.mobicore.core.storage.Json;
import com.mobicore.core.storage.MemoryVfs;
import com.mobicore.core.storage.StorageLayout;
import com.mobicore.core.storage.Vfs;
import com.mobicore.tools.SampleSuite;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Importing a folder of games at once, which is how anyone with a J2ME
 * collection actually has them.
 */
public final class BatchImportTest extends Test {

    private final String fixtureDir;

    public BatchImportTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Importing many at once";
    }

    @Override
    public void run() throws Exception {
        if (!new File(fixtureDir, "demo/SkyRunner.class").exists()) {
            fail("fixtures are not compiled; run ./build.sh fixtures");
            return;
        }
        pairs();
        collections();
        brokenFiles();
        throughTheBridge();
    }

    // --------------------------------------------------------------- pairs

    private void pairs() throws Exception {
        GameLibrary library = library();
        byte[] jar = SampleSuite.jar(fixtureDir);
        byte[] jad = SampleSuite.jad();

        // The picker hands these over in whatever order it likes, and the
        // descriptor is useless until its archive is in hand.
        BatchImport.Report report = BatchImport.run(library,
                new String[]{"SkyRunner.jad", "SkyRunner.jar", "Racer.jar"},
                new byte[][]{jad, jar, otherGame("Racer")});

        eq(2, report.installed(), "both games install");
        eq(0, report.count(BatchImport.Outcome.SKIPPED),
                "and the descriptor is not left over: it belongs to the JAR beside it");
        eq(2, library.size(), "the library holds both");
        check(report.summary().indexOf("2") >= 0, "the summary counts them: " + report.summary());

        // A descriptor whose archive was not picked can install nothing.
        BatchImport.Report lonely = BatchImport.run(library,
                new String[]{"Missing.jad"}, new byte[][]{jad});
        eq(1, lonely.count(BatchImport.Outcome.SKIPPED), "a lone descriptor is skipped");
        eq(0, lonely.installed(), "and installs nothing");
        check(lonely.outcomes().get(0).detail().indexOf(".jar") >= 0,
                "the reason names what is missing");

        // Importing the same game again replaces it rather than duplicating.
        BatchImport.Report again = BatchImport.run(library,
                new String[]{"SkyRunner.jar"}, new byte[][]{jar});
        eq(1, again.count(BatchImport.Outcome.REPLACED), "a game already there is replaced");
        eq(2, library.size(), "and the library does not grow");
    }

    // --------------------------------------------------------- collections

    private void collections() throws Exception {
        GameLibrary library = library();

        // A zip of JARs is how collections were passed around.
        Map<String, byte[]> bundle = new LinkedHashMap<String, byte[]>();
        bundle.put("games/Racer.jar", otherGame("Racer"));
        bundle.put("games/Puzzle.jar", otherGame("Puzzle"));
        bundle.put("games/readme.txt", "Chúc vui vẻ".getBytes("UTF-8"));
        BatchImport.Report report = BatchImport.run(library,
                new String[]{"collection.zip"}, new byte[][]{SampleSuite.zip(bundle)});

        eq(2, report.installed(), "every game inside the zip is imported");
        eq(2, library.size(), "and reaches the library");
        eq(0, report.count(BatchImport.Outcome.FAILED), "the text file is not an error");

        // A zip with nothing in it that is a game is not a failure either.
        Map<String, byte[]> empty = new LinkedHashMap<String, byte[]>();
        empty.put("notes.txt", "trống".getBytes("UTF-8"));
        BatchImport.Report nothing = BatchImport.run(library,
                new String[]{"notes.zip"}, new byte[][]{SampleSuite.zip(empty)});
        eq(1, nothing.count(BatchImport.Outcome.SKIPPED), "a zip with no games is skipped");
        eq("Không phải trò chơi J2ME", nothing.outcomes().get(0).detail(), "and says why");
    }

    // -------------------------------------------------------- broken files

    private void brokenFiles() throws Exception {
        GameLibrary library = library();
        byte[] jar = SampleSuite.jar(fixtureDir);
        byte[] truncated = new byte[64];
        System.arraycopy(jar, 0, truncated, 0, truncated.length);

        BatchImport.Report report = BatchImport.run(library,
                new String[]{"broken.jar", "Good.jar", "empty.jar", "photo.png"},
                new byte[][]{truncated, jar, new byte[0], new byte[]{(byte) 0x89, 'P', 'N', 'G'}});

        eq(1, report.installed(), "the good game still goes in");
        check(report.count(BatchImport.Outcome.FAILED) >= 2,
                "the broken and the empty file are reported as failures");
        eq(1, report.count(BatchImport.Outcome.SKIPPED), "and a picture is simply not a game");

        // Which file failed matters: "something went wrong" is not actionable.
        boolean named = false;
        List<BatchImport.Outcome> outcomes = report.outcomes();
        for (int i = 0; i < outcomes.size(); i++) {
            BatchImport.Outcome outcome = outcomes.get(i);
            if (outcome.status() == BatchImport.Outcome.FAILED
                    && "broken.jar".equals(outcome.name())) {
                named = true;
                check(outcome.detail().length() > 0, "with a reason: " + outcome.detail());
            }
        }
        check(named, "each failure names its file");
    }

    // ---------------------------------------------------------- the bridge

    private void throughTheBridge() throws Exception {
        MobiCoreFacade facade = new MobiCoreFacade(new MemoryVfs());
        facade.open("/data");
        Map<String, Object> result = Json.readObject(facade.importMany(
                new String[]{"SkyRunner.jar", "SkyRunner.jad", "Racer.jar", "junk.txt"},
                new byte[][]{SampleSuite.jar(fixtureDir), SampleSuite.jad(),
                        otherGame("Racer"), "hello".getBytes("UTF-8")}));

        check(Json.bool(result, "ok", false), "the batch crosses the bridge");
        eq(2, Json.integer(result, "installed", 0), "two games installed");
        eq(1, Json.integer(result, "skipped", 0), "and the stray file was skipped");
        eq(3, Json.array(result, "files").size(), "every file that mattered is reported");
        check(Json.string(result, "summary", "").length() > 0,
                "with a line to show the user: " + Json.string(result, "summary", ""));
        eq(2, Json.array(Json.readObject(facade.libraryJson()), "games").size(),
                "and the library lists both");
    }

    // --------------------------------------------------------------- tools

    private GameLibrary library() throws Exception {
        Vfs vfs = new MemoryVfs();
        GameLibrary library = new GameLibrary(vfs, new StorageLayout("/data/MobiCore"));
        library.setClock(1_700_000_000_000L);
        library.open();
        return library;
    }

    /** A second, distinct suite, so a batch is not the same game twice. */
    private byte[] otherGame(String title) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("META-INF/MANIFEST.MF", SampleSuite.utf8("Manifest-Version: 1.0\n"
                + "MIDlet-Name: " + title + "\n"
                + "MIDlet-Version: 1.0\n"
                + "MIDlet-Vendor: Test Games\n"
                + "MIDlet-1: " + title + ",,demo." + title + "\n"
                + "MicroEdition-Configuration: CLDC-1.1\n"
                + "MicroEdition-Profile: MIDP-2.0\n"));
        entries.put("demo/" + title + ".class",
                new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        return SampleSuite.zip(entries);
    }
}
