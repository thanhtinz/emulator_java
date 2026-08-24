package com.mobicore.tests;

import java.util.ArrayList;
import java.util.List;

/** Runs every registered test and prints a compact report. */
public final class Runner {

    public static void main(String[] args) {
        List<Test> tests = new ArrayList<Test>();
        tests.add(new TextTest());
        tests.add(new AttributeSetTest());
        tests.add(new JarArchiveTest());
        tests.add(new SuiteLoaderTest());
        tests.add(new StorageTest());
        tests.add(new VmTest(args.length > 0 ? args[0] : "build/classes/fixtures"));

        int failed = 0;
        int totalChecks = 0;
        System.out.println("MobiCore core test suite");
        System.out.println("========================");
        for (Test test : tests) {
            String status;
            try {
                test.run();
                status = test.failures().isEmpty() ? "PASS" : "FAIL";
            } catch (Exception e) {
                test.failures().add("threw " + e);
                status = "FAIL";
            }
            totalChecks += test.checks();
            System.out.println(pad(status, 6) + pad(test.name(), 34) + test.checks() + " checks");
            for (String failure : test.failures()) {
                System.out.println("       - " + failure);
            }
            if (!test.failures().isEmpty()) {
                failed++;
            }
        }
        System.out.println("------------------------");
        System.out.println((tests.size() - failed) + "/" + tests.size() + " suites passed, "
                + totalChecks + " checks total");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static String pad(String value, int width) {
        StringBuilder out = new StringBuilder(value);
        while (out.length() < width) {
            out.append(' ');
        }
        return out.toString();
    }
}
