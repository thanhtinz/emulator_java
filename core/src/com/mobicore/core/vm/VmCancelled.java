package com.mobicore.core.vm;

/**
 * Game bị dừng theo yêu cầu, chứ không phải hỏng.
 *
 * <p>Người chơi bấm thoát trong lúc game đang chạy dở một khung hình: máy ảo
 * phải bỏ dở khung ấy giữa chừng, và cách duy nhất bỏ dở là ném ra. Nhưng đây
 * không phải một lần hỏng — không có gì để báo, không có gì để giải thích —
 * nên nó mang một tên riêng để chỗ bắt được phân biệt.</p>
 */
public final class VmCancelled extends VmError {

    private static final long serialVersionUID = 1L;

    public VmCancelled(String message) {
        super(message);
    }
}
