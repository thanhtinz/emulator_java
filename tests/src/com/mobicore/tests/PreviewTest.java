package com.mobicore.tests;

import com.mobicore.core.gfx.Framebuffer;
import com.mobicore.tools.CrashScreen;
import com.mobicore.tools.DetailScreen;
import com.mobicore.tools.ImportScreen;
import com.mobicore.tools.ProfileScreen;
import com.mobicore.tools.SlotsScreen;
import com.mobicore.tools.VmScreen;
import com.mobicore.tools.ui.Theme;

/**
 * Không trang nào bị cắt ngang.
 *
 * <p>Ảnh xem trước không cuộn được, nên một trang dài hơn tấm vẽ bị cắt — và
 * chỗ bị cắt luôn rơi vào giữa một cái thẻ, tức là chữ lọt ra khỏi khung. Nó
 * đã xảy ra hơn một lần, và lần nào cũng vì cùng một chuyện: nội dung dài
 * thêm mà con số chiều cao thì đứng yên — thêm một mục vào danh sách ứng dụng
 * trong bộ cài là đủ.</p>
 *
 * <p>Nên chỗ này không kiểm tra một con số nào cả. Nó vẽ từng trang rồi nhìn
 * mấy hàng cuối: còn thấy gì khác nền tức là trang vẫn còn nội dung ở chỗ nó
 * đã hết chỗ.</p>
 */
public final class PreviewTest extends Test {

    /** Bao nhiêu hàng cuối phải là nền trơn. */
    private static final int CLEAR_ROWS = 8;

    private final String fixtureDir;

    public PreviewTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Trang xem trước không bị cắt";
    }

    @Override
    public void run() throws Exception {
        Theme.setMode(Theme.LIGHT);
        clear("Nhập từ tệp", new ImportScreen().render());
        clear("Chi tiết game", new DetailScreen(fixtureDir).render());
        clear("Cài đặt game", new ProfileScreen(fixtureDir).render());
        clear("Máy ảo", new VmScreen(fixtureDir).render());
        clear("Chỗ lưu", new SlotsScreen(fixtureDir).render());
        clear("Game hỏng", new CrashScreen(fixtureDir).render());
        clear("Game treo", new CrashScreen(fixtureDir, "demo.HangDemo").render());
    }

    /**
     * Mấy hàng cuối của một trang phải là nền trơn.
     *
     * <p>Kiểm ở đáy chứ không kiểm chiều cao: chiều cao đúng hôm nay có thể
     * sai vào ngày ai đó thêm một dòng, còn "dưới cùng phải trống" thì đúng
     * mãi.</p>
     */
    private void clear(String what, Framebuffer page) {
        int[] pixels = page.pixels();
        int ink = 0;
        for (int y = page.height() - CLEAR_ROWS; y < page.height(); y++) {
            for (int x = 0; x < page.width(); x++) {
                if (pixels[y * page.width() + x] != Theme.BG) {
                    ink++;
                }
            }
        }
        eq(0, ink, "trang \"" + what + "\" hết ở đúng chỗ nó hết, không bị cắt ngang");
    }
}
