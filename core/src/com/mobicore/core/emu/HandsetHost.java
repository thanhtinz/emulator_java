package com.mobicore.core.emu;

import com.mobicore.core.model.HandsetIdentity;
import com.mobicore.core.vm.VmHost;

/**
 * Cái máy trả lời khi game hỏi nó đang chạy trên máy nào.
 *
 * <p>Chỉ chen vào đúng một câu hỏi — {@code System.getProperty} — và chuyển
 * mọi thứ còn lại cho máy thật. Đứng riêng chứ không nằm trong nhật ký như
 * trước, vì câu trả lời nay đọc từ hồ sơ của từng game, và hồ sơ ấy có thể
 * đổi giữa hai lần game hỏi.</p>
 */
public final class HandsetHost implements VmHost {

    private final VmHost delegate;
    private final HandsetIdentity identity;

    public HandsetHost(VmHost delegate, HandsetIdentity identity) {
        this.delegate = delegate;
        this.identity = identity;
    }

    @Override
    public String property(String name) {
        String answer = identity.value(name);
        // Không biết thì hỏi tiếp máy thật, và máy thật không biết thì trả
        // null — đúng cách một chiếc điện thoại không có phần đó trả lời.
        return answer != null ? answer : delegate.property(name);
    }

    @Override
    public long currentTimeMillis() {
        return delegate.currentTimeMillis();
    }

    @Override
    public void print(boolean error, String text) {
        delegate.print(error, text);
    }

    @Override
    public void exit(int code) {
        delegate.exit(code);
    }

    @Override
    public void sleep(long millis) throws InterruptedException {
        delegate.sleep(millis);
    }
}
