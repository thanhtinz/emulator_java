package com.mobicore.tests;

import com.mobicore.core.emu.EmulatorSession;
import com.mobicore.core.gfx.Transforms;
import com.mobicore.core.jar.SuiteLoader;
import com.mobicore.tools.EmulatorScreen;
import com.mobicore.tools.SampleSuite;

/**
 * Tám phép lật xoay của sprite.
 *
 * <p>Game J2ME nào cũng dựa vào chúng: một chu kỳ đi bộ vẽ một lần quay mặt
 * sang phải, còn quay sang trái thì lật lại. Bốn phép có chữ MIRROR là lật
 * <em>trước</em> rồi mới xoay — và thứ tự ấy có thật, vì lật rồi xoay chín
 * mươi độ không ra cùng kết quả với xoay rồi lật.</p>
 *
 * <p>Hai phép {@code MIRROR_ROT90} và {@code MIRROR_ROT270} từng làm ngược thứ
 * tự, và hậu quả là chúng đổi chỗ cho nhau: game xin phép này thì nhận phép
 * kia. Bộ kiểm cũ chỉ soi {@code MIRROR} và {@code ROT90} — đúng hai phép
 * không sai — nên lỗi nằm đó mà không ai thấy.</p>
 *
 * <p>Nên chỗ này không chép sẵn kết quả của tám phép. Nó dựng lại phép lật và
 * phép xoay chín mươi độ bằng hai hàm nhỏ độc lập, rồi ghép chúng theo đúng
 * cái tên MIDP đặt. Ghép sai thì lộ ra ngay, vì hai đường tính không thể sai
 * giống nhau.</p>
 */
public final class TransformTest extends Test {

    private final String fixtureDir;

    public TransformTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Tám phép lật xoay của sprite";
    }

    @Override
    public String toString() {
        return name();
    }

    /** Tên MIDP, và số lần xoay chín mươi độ theo chiều kim đồng hồ sau khi lật. */
    private static final int[][] SPEC = {
            {Transforms.NONE, 0, 0},
            {Transforms.MIRROR, 1, 0},
            {Transforms.ROT90, 0, 1},
            {Transforms.ROT180, 0, 2},
            {Transforms.ROT270, 0, 3},
            {Transforms.MIRROR_ROT90, 1, 1},
            {Transforms.MIRROR_ROT180, 1, 2},
            {Transforms.MIRROR_ROT270, 1, 3},
    };

    private static final String[] LABELS = {
            "NONE", "MIRROR", "ROT90", "ROT180", "ROT270",
            "MIRROR_ROT90", "MIRROR_ROT180", "MIRROR_ROT270",
    };

    @Override
    public void run() throws Exception {
        everyTransformAgainstItsDefinition();
        theSizeAfterTurning();
        throughTheEmulatedApi();
        theFourThatWereMissing();
    }

    /**
     * Bốn hàm MIDP máy ảo từng thiếu hẳn.
     *
     * <p>Thiếu hẳn nghĩa là game gọi tới thì dừng ngay tại dòng đó. Ba hàm
     * trên {@code Graphics} và một trên {@code Font}; hàm nặng nhất là
     * {@code copyArea}, thứ game dùng để cuộn nền mà không phải vẽ lại.</p>
     */
    private void theFourThatWereMissing() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, 240, 320,
                new EmulatorScreen.FixedClock());
        session.start("demo.PixelProbe");
        eq("128|28|200,c8c8c8|445566|truefalse",
                session.vm().stringOf(session.vm().callVirtual(session.context().midlet(),
                        "latecomers", "()Ljava/lang/String;")),
                "mức xám, màu hiện ra và kiểu chữ trơn trả lời đúng");

        // Chép sang chỗ rời hẳn: hai điểm ở chỗ cũ và hai điểm ở chỗ mới.
        String apart = session.vm().stringOf(session.vm().callVirtual(session.context().midlet(),
                "copied", "(II)Ljava/lang/String;", Integer.valueOf(6), Integer.valueOf(6)));
        eq(4, marks(apart), "chép sang chỗ rời thì cả hai chỗ đều có hình");
        check(apart.split("\n")[6].indexOf("######".substring(0, 2)) == 6,
                "hình mới nằm đúng chỗ được chỉ");

        // Chép chồng lên chính nó: phần chồng không được bôi mất giữa chừng.
        String overlapped = session.vm().stringOf(session.vm().callVirtual(
                session.context().midlet(), "copied", "(II)Ljava/lang/String;",
                Integer.valueOf(2), Integer.valueOf(1)));
        eq(3, marks(overlapped), "chép chồng lên chính nó không bôi mất phần chồng");

        eq("kêu", session.vm().stringOf(session.vm().callVirtual(session.context().midlet(),
                        "copyOutside", "()Ljava/lang/String;")),
                "chép ra ngoài mép tấm vẽ thì kêu, không lặng lẽ bỏ qua");
    }

    /** Bao nhiêu điểm đã vẽ trong một bản đồ điểm. */
    private int marks(String map) {
        int count = 0;
        for (int i = 0; i < map.length(); i++) {
            if (map.charAt(i) == '#') {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------ so với chính định nghĩa

    private void everyTransformAgainstItsDefinition() {
        // Một mảnh không vuông và không đối xứng: mảnh vuông thì bốn phép xoay
        // trông giống nhau, và mảnh đối xứng thì phép lật không hiện ra.
        int width = 5, height = 3;
        int[] block = new int[width * height];
        for (int i = 0; i < block.length; i++) {
            block[i] = 100 + i;
        }
        for (int i = 0; i < SPEC.length; i++) {
            int transform = SPEC[i][0];
            int[] wanted = compose(block, width, height, SPEC[i][1] != 0, SPEC[i][2]);
            int[] got = Transforms.apply(block, width, height, 0, 0, width, height, transform);
            eq(wanted.length, got.length, LABELS[i] + " giữ nguyên số điểm ảnh");
            int wrong = 0;
            for (int p = 0; p < wanted.length && p < got.length; p++) {
                if (wanted[p] != got[p]) {
                    wrong++;
                }
            }
            eq(0, wrong, LABELS[i] + " đặt từng điểm đúng chỗ định nghĩa của nó nói");
        }
    }

    /** Lật quanh trục dọc, rồi xoay theo chiều kim đồng hồ bấy nhiêu lần. */
    private int[] compose(int[] block, int width, int height, boolean mirror, int quarters) {
        int[] current = block;
        int w = width, h = height;
        if (mirror) {
            int[] next = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    next[y * w + (w - 1 - x)] = current[y * w + x];
                }
            }
            current = next;
        }
        for (int turn = 0; turn < quarters; turn++) {
            int[] next = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    // Kim đồng hồ: hàng trên cùng thành cột bên phải.
                    next[x * h + (h - 1 - y)] = current[y * w + x];
                }
            }
            current = next;
            int swap = w;
            w = h;
            h = swap;
        }
        return current;
    }

    private void theSizeAfterTurning() {
        for (int i = 0; i < SPEC.length; i++) {
            boolean turned = SPEC[i][2] % 2 == 1;
            eq(turned ? 3 : 5, Transforms.resultWidth(SPEC[i][0], 5, 3),
                    LABELS[i] + " cho ra bề rộng đúng");
            eq(turned ? 5 : 3, Transforms.resultHeight(SPEC[i][0], 5, 3),
                    LABELS[i] + " cho ra chiều cao đúng");
        }
    }

    // --------------------------------------------- qua đúng lối game gọi

    /**
     * Cùng tám phép ấy, đi qua {@code Graphics.drawRegion} của game.
     *
     * <p>Sửa đúng bảng phép mà nối dây sai thì game vẫn nhận nhầm; chỗ này đi
     * đúng lối game đi.</p>
     */
    private void throughTheEmulatedApi() throws Exception {
        SuiteLoader suite = SuiteLoader.load(SampleSuite.jar(fixtureDir), SampleSuite.jad());
        EmulatorSession session = EmulatorSession.create(suite, 240, 320,
                new EmulatorScreen.FixedClock());
        session.start("demo.PixelProbe");
        for (int i = 0; i < SPEC.length; i++) {
            String drawn = session.vm().stringOf(session.vm().callVirtual(
                    session.context().midlet(), "region", "(I)Ljava/lang/String;",
                    Integer.valueOf(SPEC[i][0])));
            eq(expected(SPEC[i][1] != 0, SPEC[i][2]), spots(drawn),
                    "drawRegion với " + LABELS[i] + " đặt ba điểm đúng chỗ");
        }
    }

    /**
     * Ba điểm đánh dấu của mảnh nguồn, sau khi lật xoay, tính từ định nghĩa.
     *
     * <p>Mảnh nguồn rộng 4 cao 2, có ba điểm: đen ở (0,0), xám ở (1,0) và xám
     * đậm ở (0,1); vẽ vào (2,2).</p>
     */
    private String expected(boolean mirror, int quarters) {
        int width = 4, height = 2;
        int[] block = new int[width * height];
        block[0] = 1;
        block[1] = 2;
        block[width] = 3;
        int[] turned = compose(block, width, height, mirror, quarters);
        int w = quarters % 2 == 1 ? height : width;
        StringBuilder out = new StringBuilder();
        for (int mark = 1; mark <= 3; mark++) {
            for (int p = 0; p < turned.length; p++) {
                if (turned[p] == mark) {
                    out.append(mark).append('@').append(2 + p % w)
                            .append(',').append(2 + p / w).append(' ');
                }
            }
        }
        return out.toString().trim();
    }

    /** Vị trí ba dấu trong bản đồ điểm mà game vẽ ra. */
    private String spots(String map) {
        String[] rows = map.split("\n");
        StringBuilder out = new StringBuilder();
        char[] marks = {'#', 'o', 'x'};
        for (int mark = 0; mark < marks.length; mark++) {
            for (int y = 0; y < rows.length; y++) {
                int at = rows[y].indexOf(marks[mark]);
                if (at >= 0) {
                    out.append(mark + 1).append('@').append(at).append(',').append(y).append(' ');
                }
            }
        }
        return out.toString().trim();
    }
}
