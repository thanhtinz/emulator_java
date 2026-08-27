package com.mobicore.core.emu;

import com.mobicore.core.vm.VmHost;

/**
 * Cái máy trả lời khi game hỏi nó đang chạy trên máy nào.
 *
 * <p>Chỉ chen vào đúng một câu hỏi — {@code System.getProperty} — và chuyển
 * mọi thứ còn lại cho máy thật. Đứng riêng chứ không nằm trong nhật ký như
 * trước, vì trả lời game đang chạy trên máy nào không phải việc của nhật
 * ký.</p>
 */
public final class PropertiesHost implements VmHost {

    private final VmHost delegate;

    public PropertiesHost(VmHost delegate) {
        this.delegate = delegate;
    }

    @Override
    public String property(String name) {
        String answer = SystemProperties.value(name);
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
