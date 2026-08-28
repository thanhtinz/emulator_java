package com.mobicore.tests;

import com.mobicore.tools.Preview;

/**
 * Không dòng chữ nào chạy ra khỏi khung của nó.
 *
 * <p>Chữ tràn khung là lỗi đã xảy ra nhiều lần, và lần nào cũng chỉ lộ ra khi
 * có người phóng to tấm ảnh chụp lên xem. Nguyên do thì luôn giống nhau: một
 * chiều cao khung gõ tay không theo kịp nội dung bên trong, một cái nhãn dài
 * hơn hôm trước, một hàng chip không đủ chỗ.</p>
 *
 * <p>Nên chỗ vẽ tự ghi lấy: mỗi khung {@code Ui} vẽ ra và mỗi dòng chữ nó đặt
 * xuống đều được nhớ lại, rồi so với nhau. Chỗ này chỉ việc vẽ hết mọi màn
 * hình xem trước — cả nền sáng lẫn nền tối — và nghe xem có tiếng kêu nào
 * không. Màn hình mới thêm vào sau này cũng được soi, không ai phải nhớ khai
 * báo thêm.</p>
 */
public final class OverflowTest extends Test {

    private final String fixtureDir;

    public OverflowTest(String fixtureDir) {
        this.fixtureDir = fixtureDir;
    }

    @Override
    public String name() {
        return "Chữ nằm gọn trong khung";
    }

    @Override
    public void run() throws Exception {
        String out = "build/overflow-check";
        String why = "";
        try {
            Preview.main(new String[]{out, fixtureDir});
        } catch (IllegalStateException spill) {
            why = spill.getMessage();
        }
        eq("", why, "mọi màn hình xem trước vẽ chữ nằm gọn trong khung của nó");
    }
}
