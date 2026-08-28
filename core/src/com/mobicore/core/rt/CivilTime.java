package com.mobicore.core.rt;

/**
 * Lịch dương, tính bằng số học thuần.
 *
 * <p>Máy ảo không mượn được {@code java.util.Calendar} của máy chủ: phần lõi
 * phải dịch sang được cho iOS và không dựa vào thư viện nào ngoài chính nó.
 * Nên phép đổi giữa "mốc thời gian" và "ngày tháng" làm ở đây, bằng thuật
 * toán đếm ngày từ một mốc dời về đầu tháng ba — cách này đúng cho cả năm âm
 * và không có chỗ nào phải chia trường hợp năm nhuận.</p>
 */
public final class CivilTime {

    /** Một ngày, tính bằng mili giây. */
    public static final long DAY = 86400000L;

    private CivilTime() {
    }

    /**
     * Số ngày từ 1970-01-01 tới ngày đã cho.
     *
     * @param month 1 là tháng giêng
     */
    public static long daysFromCivil(long year, int month, int day) {
        long y = year - (month <= 2 ? 1 : 0);
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    /** Ngày tháng của một số ngày kể từ 1970-01-01, trả về {năm, tháng, ngày}. */
    public static long[] civilFromDays(long days) {
        long z = days + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp + (mp < 10 ? 3 : -9);
        return new long[]{y + (m <= 2 ? 1 : 0), m, d};
    }

    /**
     * Chia lấy nguyên làm tròn xuống.
     *
     * <p>Phép chia của Java làm tròn về không, nên một mốc trước 1970 rơi vào
     * đúng nửa ngày sẽ ra sai một ngày. Lịch thì không có chỗ cho "sai một
     * ngày".</p>
     */
    public static long floorDiv(long value, long by) {
        long quotient = value / by;
        return (value % by != 0 && ((value < 0) != (by < 0))) ? quotient - 1 : quotient;
    }

    public static long floorMod(long value, long by) {
        return value - floorDiv(value, by) * by;
    }

    /** Số ngày trong một tháng, tháng 1 là tháng giêng. */
    public static int daysInMonth(long year, int month) {
        return (int) (daysFromCivil(month == 12 ? year + 1 : year, month == 12 ? 1 : month + 1, 1)
                - daysFromCivil(year, month, 1));
    }
}
