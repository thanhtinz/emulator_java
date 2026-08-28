package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.core.rt.CivilTime;
import com.mobicore.core.vm.VmObject;
import com.mobicore.tools.EmulatorScreen;
import com.mobicore.tools.SampleSuite;

import java.util.Calendar;
import java.util.Random;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

/**
 * Phần thư viện chuẩn còn thiếu.
 *
 * <p>Đi rà từng hàm CLDC 1.1 mà game hay gọi thì mười chín trên năm mươi ba
 * hàm chưa có, và mỗi hàm thiếu là một cái chết ngay tại dòng gọi nó — không
 * có nửa vời, không có lỗi mờ nhạt. Nặng nhất là bốn lớp vắng hẳn:
 * {@code Calendar}, {@code TimeZone}, {@code Short}/{@code Byte} và
 * {@code Reader}/{@code Writer}.</p>
 *
 * <p>Lịch thì không kiểm bằng vài mốc chọn tay: nó được đối chiếu với lịch
 * của máy chủ trên hàng nghìn mốc ngẫu nhiên và trên bốn múi giờ, vì sai một
 * ngày trong lịch là thứ không nhìn ra được bằng ba phép thử.</p>
 */
public final class StdlibTest extends Test {

    private final String fixtureDir;

    public StdlibTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Thư viện chuẩn CLDC 1.1";
    }

    @Override
    public void run() throws Exception {
        everyCornerThatWasMissing();
        theDateArithmetic();
        theCalendarAgainstAReference();
        settingAFieldAgainstAReference();
        theZone();
        readingTextFromTheJar();
    }

    private EmulatorSession boot(int offsetMinutes) throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, 240, 320,
                new EmulatorScreen.FixedClock());
        session.vm().setTimeZone("Thử", offsetMinutes * 60000);
        session.start("demo.ClockDemo");
        return session;
    }

    // -------------------------------------------- từng góc của thư viện

    /**
     * Mỗi hàm còn thiếu trả về đúng thứ Java thật trả về.
     *
     * <p>Không so với một chuỗi chép tay: chuỗi chép tay chỉ chứng minh hôm
     * nay nó ra như vậy. Cùng một lớp được chạy hai lần — một lần trong máy
     * ảo, một lần trên máy chủ — rồi đem hai kết quả ra so. Hàm nào thiếu thì
     * máy ảo dừng ngay tại dòng gọi nó, và chỗ này nói ra tên hàm ấy.</p>
     */
    private void everyCornerThatWasMissing() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, 240, 320,
                new EmulatorScreen.FixedClock());
        String inside;
        try {
            inside = session.vm().stringOf(session.vm().callStatic("demo/Stdlib", "everything",
                    "()Ljava/lang/String;"));
        } catch (RuntimeException missing) {
            inside = "máy ảo dừng: " + missing.getMessage();
        }
        String outside = onTheHost();
        if (inside.equals(outside)) {
            check(true, "cả " + outside.split("\n").length
                    + " góc của thư viện chuẩn trả về đúng như Java thật");
            return;
        }
        eq(outside, inside, "thư viện chuẩn trả về đúng như Java thật: " + firstDifference(
                outside, inside));
    }

    /** Chạy đúng lớp ấy trên máy chủ, để có câu trả lời thật mà so. */
    private String onTheHost() throws Exception {
        java.net.URLClassLoader loader = new java.net.URLClassLoader(
                new java.net.URL[]{new java.io.File(fixtureDir).toURI().toURL()},
                String.class.getClassLoader());
        try {
            Object answer = loader.loadClass("demo.Stdlib")
                    .getMethod("everything").invoke(null);
            return String.valueOf(answer);
        } finally {
            loader.close();
        }
    }

    /** Dòng đầu tiên khác nhau, vì so cả hai bản dài thì không đọc được. */
    private String firstDifference(String expected, String actual) {
        String[] want = expected.split("\n");
        String[] got = actual.split("\n");
        for (int i = 0; i < want.length; i++) {
            String line = i < got.length ? got[i] : "<thiếu>";
            if (!want[i].equals(line)) {
                return "chỗ đầu tiên khác: cần \"" + want[i] + "\", có \"" + line + "\"";
            }
        }
        return "bản của máy ảo dài hơn";
    }

    // ------------------------------------------------------- số học lịch

    /**
     * Đổi xuôi rồi đổi ngược phải ra đúng chỗ cũ, suốt bốn nghìn năm.
     *
     * <p>Đây là chỗ duy nhất trong máy ảo mà một lỗi lệch một đơn vị không
     * hiện ra ở đâu cả: game vẫn chạy, chỉ là phần thưởng mỗi ngày rơi vào
     * hôm qua.</p>
     */
    private void theDateArithmetic() {
        int broken = 0;
        for (long day = -730000; day <= 730000; day++) {
            long[] civil = CivilTime.civilFromDays(day);
            if (CivilTime.daysFromCivil(civil[0], (int) civil[1], (int) civil[2]) != day
                    || civil[1] < 1 || civil[1] > 12 || civil[2] < 1 || civil[2] > 31) {
                broken++;
            }
        }
        eq(0, broken, "đổi ngày xuôi ngược khớp nhau trên bốn nghìn năm");
        eq(29, CivilTime.daysInMonth(2024, 2), "tháng hai năm nhuận có 29 ngày");
        eq(28, CivilTime.daysInMonth(2023, 2), "tháng hai năm thường có 28 ngày");
        eq(28, CivilTime.daysInMonth(1900, 2), "năm chia hết 100 mà không chia hết 400 thì không nhuận");
        eq(29, CivilTime.daysInMonth(2000, 2), "năm chia hết 400 thì nhuận");
        eq(-1L, CivilTime.floorDiv(-1L, 2L), "chia làm tròn xuống, không làm tròn về không");
    }

    // ------------------------------------------ đối chiếu với lịch máy chủ

    private void theCalendarAgainstAReference() throws Exception {
        int[] zones = {0, 420, -330, 60};
        for (int i = 0; i < zones.length; i++) {
            EmulatorSession session = boot(zones[i]);
            VmObject game = session.context().midlet();
            Calendar reference = Calendar.getInstance(
                    new SimpleTimeZone(zones[i] * 60000, "Thử"));
            Random rng = new Random(7);
            int wrong = 0;
            String firstWrong = "";
            for (int n = 0; n < 400; n++) {
                long millis = rng.nextLong() % 4000000000000L;
                String mine = session.vm().stringOf(session.vm().callVirtual(game, "readAt",
                        "(J)Ljava/lang/String;", Long.valueOf(millis)));
                reference.setTimeInMillis(millis);
                String theirs = reference.get(Calendar.YEAR) + "|"
                        + reference.get(Calendar.MONTH) + "|"
                        + reference.get(Calendar.DAY_OF_MONTH) + "|"
                        + reference.get(Calendar.DAY_OF_WEEK) + "|"
                        + reference.get(Calendar.DAY_OF_YEAR) + "|"
                        + reference.get(Calendar.HOUR_OF_DAY) + "|"
                        + reference.get(Calendar.HOUR) + "|"
                        + reference.get(Calendar.AM_PM) + "|"
                        + reference.get(Calendar.MINUTE) + "|"
                        + reference.get(Calendar.SECOND) + "|"
                        + reference.get(Calendar.MILLISECOND);
                if (!mine.equals(theirs)) {
                    wrong++;
                    if (firstWrong.length() == 0) {
                        firstWrong = " tại " + millis + ": " + mine + " != " + theirs;
                    }
                }
            }
            eq(0, wrong, "lịch đọc đúng mọi trường ở múi lệch " + zones[i] + " phút" + firstWrong);
        }
    }

    private void settingAFieldAgainstAReference() throws Exception {
        EmulatorSession session = boot(420);
        VmObject game = session.context().midlet();
        Calendar reference = Calendar.getInstance(new SimpleTimeZone(420 * 60000, "Thử"));
        int[] fields = {Calendar.YEAR, Calendar.MONTH, Calendar.DAY_OF_MONTH,
                Calendar.DAY_OF_WEEK, Calendar.DAY_OF_YEAR, Calendar.HOUR_OF_DAY,
                Calendar.HOUR, Calendar.AM_PM, Calendar.MINUTE, Calendar.SECOND,
                Calendar.MILLISECOND};
        Random rng = new Random(11);
        int wrong = 0;
        String firstWrong = "";
        for (int n = 0; n < 60; n++) {
            long millis = Math.abs(rng.nextLong() % 4000000000000L);
            for (int i = 0; i < fields.length; i++) {
                int value = sane(fields[i], rng);
                long mine = ((Number) session.vm().callVirtual(game, "setAt", "(JII)J",
                        Long.valueOf(millis), Integer.valueOf(fields[i]),
                        Integer.valueOf(value))).longValue();
                reference.setTimeInMillis(millis);
                reference.set(fields[i], value);
                if (mine != reference.getTimeInMillis()) {
                    wrong++;
                    if (firstWrong.length() == 0) {
                        firstWrong = " trường " + fields[i] + " = " + value + " tại " + millis
                                + ": lệch " + (mine - reference.getTimeInMillis()) + " ms";
                    }
                }
            }
        }
        eq(0, wrong, "đặt một trường rồi đọc lại đúng như lịch thật" + firstWrong);

        // Cộng dồn vào ngày là cách game đếm sang hôm sau, kể cả khi hôm nay
        // là ngày cuối tháng.
        long lastOfFebruary = 1709164800000L;  // 2024-02-29 00:00 GMT
        eq("2024-3-1", session.vm().stringOf(session.vm().callVirtual(game, "tomorrow",
                        "(J)Ljava/lang/String;", Long.valueOf(lastOfFebruary - 420L * 60000))),
                "ngày 29 tháng hai cộng một ra mồng một tháng ba");
    }

    private int sane(int field, Random rng) {
        if (field == Calendar.YEAR) {
            return 1971 + rng.nextInt(120);
        }
        if (field == Calendar.MONTH) {
            return rng.nextInt(12);
        }
        if (field == Calendar.DAY_OF_MONTH) {
            return 1 + rng.nextInt(28);
        }
        if (field == Calendar.DAY_OF_WEEK) {
            return 1 + rng.nextInt(7);
        }
        if (field == Calendar.DAY_OF_YEAR) {
            return 1 + rng.nextInt(365);
        }
        if (field == Calendar.HOUR_OF_DAY) {
            return rng.nextInt(24);
        }
        if (field == Calendar.HOUR) {
            return rng.nextInt(12);
        }
        if (field == Calendar.AM_PM) {
            return rng.nextInt(2);
        }
        if (field == Calendar.MILLISECOND) {
            return rng.nextInt(1000);
        }
        return rng.nextInt(60);
    }

    private void theZone() throws Exception {
        EmulatorSession session = boot(420);
        VmObject game = session.context().midlet();
        eq("Thử|25200000|false", session.vm().stringOf(session.vm().callVirtual(game, "zone",
                        "()Ljava/lang/String;")),
                "game hỏi múi giờ thì nghe đúng cái máy đang chạy");
    }

    // ------------------------------------------------ đọc chữ trong gói

    /**
     * Game đọc tệp chữ của chính nó bằng {@code InputStreamReader}.
     *
     * <p>Màn chơi, lời thoại, bảng chữ — game nào cũng mang theo vài tệp như
     * vậy và đọc theo đúng một lối này. Thiếu lớp ấy thì game chết trước khi
     * kịp vẽ gì lên màn hình.</p>
     */
    private void readingTextFromTheJar() throws Exception {
        EmulatorSession session = boot(0);
        VmObject game = session.context().midlet();
        eq("Chúc một ngày lành", session.vm().stringOf(session.vm().callVirtual(game, "readText",
                        "(Ljava/lang/String;)Ljava/lang/String;",
                        session.vm().newString("/message.txt"))),
                "đọc được tệp chữ trong gói, dấu tiếng Việt còn nguyên");
        eq("<không có tệp>", session.vm().stringOf(session.vm().callVirtual(game, "readText",
                        "(Ljava/lang/String;)Ljava/lang/String;",
                        session.vm().newString("/không-có.txt"))),
                "tệp không có thì trả về null chứ không ném");
    }
}
