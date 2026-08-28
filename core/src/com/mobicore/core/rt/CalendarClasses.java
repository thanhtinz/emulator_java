package com.mobicore.core.rt;

import com.mobicore.core.vm.NativeMethod;
import com.mobicore.core.vm.Vm;
import com.mobicore.core.vm.VmObject;

/**
 * {@code java.util.Calendar} và {@code java.util.TimeZone} của CLDC 1.1.
 *
 * <p>Hai lớp này thiếu hẳn cho tới giờ, và một game chạm vào chúng thì chết
 * ngay tại dòng đó. Chúng không hiếm: phần thưởng mỗi ngày, cái đồng hồ trong
 * góc màn hình, dấu thời gian trên ô lưu, mầm cho bộ sinh số ngẫu nhiên — tất
 * cả đều đi qua {@code Calendar.getInstance()}.</p>
 *
 * <p>Phép tính lịch làm bằng {@link CivilTime}, không mượn lịch của máy chủ,
 * để phần lõi vẫn dịch được sang iOS.</p>
 */
public final class CalendarClasses {

    public static final String CALENDAR = "java/util/Calendar";
    public static final String TIME_ZONE = "java/util/TimeZone";

    // Số hiệu các trường, đúng như java.util.Calendar đặt: chương trình game
    // đã dịch sẵn hằng số này vào mã của nó, nên chúng không được khác.
    static final int ERA = 0, YEAR = 1, MONTH = 2, WEEK_OF_YEAR = 3, WEEK_OF_MONTH = 4,
            DATE = 5, DAY_OF_YEAR = 6, DAY_OF_WEEK = 7, DAY_OF_WEEK_IN_MONTH = 8,
            AM_PM = 9, HOUR = 10, HOUR_OF_DAY = 11, MINUTE = 12, SECOND = 13,
            MILLISECOND = 14, ZONE_OFFSET = 15, DST_OFFSET = 16;

    private CalendarClasses() {
    }

    public static void install(final Vm vm) {
        timeZone(vm);
        calendar(vm);
    }

    // ---------------------------------------------------------- TimeZone

    private static void timeZone(final Vm vm) {
        vm.builtin(TIME_ZONE, Vm.OBJECT)
                .field("id", "Ljava/lang/String;")
                .field("offset", "I")
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return null;
                    }
                })
                .method("getID", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("id");
                    }
                })
                .method("getRawOffset", "()I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("offset");
                    }
                })
                .method("getOffset", "(IIIIII)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // Một múi, một độ lệch: xem chú thích ở useDaylightTime.
                        return self.get("offset");
                    }
                })
                .method("useDaylightTime", "()Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        // Máy ảo không mang theo bảng múi giờ của thế giới.
                        // Nó mang đúng một con số: độ lệch mà chiếc điện thoại
                        // đang chạy ngay lúc này, giờ mùa hè đã tính trong đó.
                        // Câu hỏi game hay hỏi là "bây giờ là mấy giờ", và câu
                        // ấy trả lời đúng; câu "tháng bảy năm sau lệch bao
                        // nhiêu" thì không, và nói thẳng là không.
                        return Rt.box(false);
                    }
                })
                .method("toString", "()Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("id");
                    }
                })
                .staticMethod("getDefault", "()Ljava/util/TimeZone;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return zone(vm, vm.timeZoneId(), vm.timeZoneOffsetMillis());
                    }
                })
                .staticMethod("getTimeZone", "(Ljava/lang/String;)Ljava/util/TimeZone;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                String asked = Rt.s(vm, args, 0);
                                if (asked.equals(vm.timeZoneId())) {
                                    return zone(vm, asked, vm.timeZoneOffsetMillis());
                                }
                                // Múi giờ nào máy ảo không biết thì trả về GMT,
                                // đúng như java.util.TimeZone vẫn làm, chứ
                                // không dựng một múi đoán bừa.
                                int offset = fixedOffset(asked);
                                return zone(vm, offset == 0 ? "GMT" : asked, offset);
                            }
                        })
                .staticMethod("getAvailableIDs", "()[Ljava/lang/String;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        String[] ids = {"GMT", vm.timeZoneId()};
                        int count = ids[0].equals(ids[1]) ? 1 : 2;
                        com.mobicore.core.vm.VmArray out =
                                vm.newArray("Ljava/lang/String;", count);
                        for (int i = 0; i < count; i++) {
                            out.objects()[i] = vm.newString(ids[i]);
                        }
                        return out;
                    }
                })
                .define();
    }

    /**
     * Độ lệch của những cái tên tự nó đã nói ra độ lệch.
     *
     * <p>{@code GMT+7}, {@code GMT-03:30} — đây là những tên game tự ghép ra,
     * và đọc được chúng thì không cần bảng múi giờ nào cả.</p>
     */
    static int fixedOffset(String id) {
        if (id == null) {
            return 0;
        }
        String text = id.trim();
        if (text.startsWith("GMT") || text.startsWith("UTC")) {
            text = text.substring(3);
        }
        if (text.length() < 2 || (text.charAt(0) != '+' && text.charAt(0) != '-')) {
            return 0;
        }
        int sign = text.charAt(0) == '-' ? -1 : 1;
        String body = text.substring(1);
        int colon = body.indexOf(':');
        String hours = colon < 0 ? body : body.substring(0, colon);
        String minutes = colon < 0 ? "0" : body.substring(colon + 1);
        try {
            int h = Integer.parseInt(hours);
            int m = Integer.parseInt(minutes);
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                return 0;
            }
            return sign * (h * 3600000 + m * 60000);
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    private static VmObject zone(Vm vm, String id, int offsetMillis) {
        VmObject made = vm.newInstance(TIME_ZONE);
        made.set("id", vm.newString(id));
        made.set("offset", Integer.valueOf(offsetMillis));
        return made;
    }

    // ---------------------------------------------------------- Calendar

    private static void calendar(final Vm vm) {
        vm.builtin(CALENDAR, Vm.OBJECT)
                .field("millis", "J")
                .field("zone", "Ljava/util/TimeZone;")
                .method("<init>", "()V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("millis", Long.valueOf(vm.host().currentTimeMillis()));
                        self.set("zone", zone(vm, vm.timeZoneId(), vm.timeZoneOffsetMillis()));
                        return null;
                    }
                })
                .staticMethod("getInstance", "()Ljava/util/Calendar;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return newCalendar(vm);
                    }
                })
                .staticMethod("getInstance", "(Ljava/util/TimeZone;)Ljava/util/Calendar;",
                        new NativeMethod() {
                            public Object invoke(Vm vm, VmObject self, Object[] args) {
                                VmObject made = newCalendar(vm);
                                if (Rt.obj(args, 0) != null) {
                                    made.set("zone", Rt.obj(args, 0));
                                }
                                return made;
                            }
                        })
                .method("getTime", "()Ljava/util/Date;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject date = vm.newInstance("java/util/Date");
                        date.host = Long.valueOf(millisOf(self));
                        return date;
                    }
                })
                .method("setTime", "(Ljava/util/Date;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject date = Rt.obj(args, 0);
                        if (date == null) {
                            throw vm.raise("java/lang/NullPointerException", "setTime(null)");
                        }
                        self.set("millis", Long.valueOf(((Number) date.host).longValue()));
                        return null;
                    }
                })
                .method("getTimeInMillis", "()J", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Long.valueOf(millisOf(self));
                    }
                })
                .method("setTimeInMillis", "(J)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        self.set("millis", Long.valueOf(Rt.l(args, 0)));
                        return null;
                    }
                })
                .method("getTimeZone", "()Ljava/util/TimeZone;", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return self.get("zone");
                    }
                })
                .method("setTimeZone", "(Ljava/util/TimeZone;)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        if (Rt.obj(args, 0) != null) {
                            self.set("zone", Rt.obj(args, 0));
                        }
                        return null;
                    }
                })
                .method("get", "(I)I", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        return Integer.valueOf(field(vm, self, Rt.i(args, 0)));
                    }
                })
                .method("set", "(II)V", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        set(vm, self, Rt.i(args, 0), Rt.i(args, 1));
                        return null;
                    }
                })
                .method("before", "(Ljava/lang/Object;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject other = Rt.obj(args, 0);
                        return Rt.box(other != null
                                && millisOf(self) < millisOf(other));
                    }
                })
                .method("after", "(Ljava/lang/Object;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject other = Rt.obj(args, 0);
                        return Rt.box(other != null
                                && millisOf(self) > millisOf(other));
                    }
                })
                .method("equals", "(Ljava/lang/Object;)Z", new NativeMethod() {
                    public Object invoke(Vm vm, VmObject self, Object[] args) {
                        VmObject other = Rt.obj(args, 0);
                        return Rt.box(other != null && other.type() == self.type()
                                && millisOf(self) == millisOf(other));
                    }
                })
                .define();
    }

    static long millisOf(VmObject calendar) {
        return ((Number) calendar.get("millis")).longValue();
    }

    /** Một cuốn lịch mới, đặt vào giờ hiện tại và múi giờ của máy. */
    private static VmObject newCalendar(Vm vm) {
        VmObject made = vm.newInstance(CALENDAR);
        made.set("millis", Long.valueOf(vm.host().currentTimeMillis()));
        made.set("zone", zone(vm, vm.timeZoneId(), vm.timeZoneOffsetMillis()));
        return made;
    }

    /** Giờ địa phương của một lịch, tính bằng mili giây kể từ mốc. */
    private static long localMillis(VmObject self) {
        VmObject zone = (VmObject) self.get("zone");
        int offset = zone == null ? 0 : ((Integer) zone.get("offset")).intValue();
        return millisOf(self) + offset;
    }

    static int field(Vm vm, VmObject self, int which) {
        long local = localMillis(self);
        long days = CivilTime.floorDiv(local, CivilTime.DAY);
        int inDay = (int) CivilTime.floorMod(local, CivilTime.DAY);
        long[] civil = CivilTime.civilFromDays(days);
        switch (which) {
            case ERA:
                return civil[0] > 0 ? 1 : 0;
            case YEAR:
                return (int) (civil[0] > 0 ? civil[0] : 1 - civil[0]);
            case MONTH:
                return (int) civil[1] - 1;
            case DATE:
                return (int) civil[2];
            case DAY_OF_YEAR:
                return (int) (days - CivilTime.daysFromCivil(civil[0], 1, 1)) + 1;
            case DAY_OF_WEEK:
                return (int) CivilTime.floorMod(days + 4, 7) + 1;
            case DAY_OF_WEEK_IN_MONTH:
                return (int) ((civil[2] - 1) / 7) + 1;
            case WEEK_OF_MONTH:
                return (int) ((civil[2] - 1) / 7) + 1;
            case WEEK_OF_YEAR:
                return (int) ((days - CivilTime.daysFromCivil(civil[0], 1, 1)) / 7) + 1;
            case AM_PM:
                return inDay >= 12 * 3600000 ? 1 : 0;
            case HOUR:
                return (inDay / 3600000) % 12;
            case HOUR_OF_DAY:
                return inDay / 3600000;
            case MINUTE:
                return (inDay / 60000) % 60;
            case SECOND:
                return (inDay / 1000) % 60;
            case MILLISECOND:
                return inDay % 1000;
            case ZONE_OFFSET:
                return (int) (local - millisOf(self));
            case DST_OFFSET:
                return 0;
            default:
                throw vm.raise("java/lang/IllegalArgumentException", "Trường lịch " + which);
        }
    }

    /**
     * Đặt một trường, giữ nguyên mọi trường khác.
     *
     * <p>Đặt ngày 31 vào tháng chỉ có 30 ngày thì tràn sang tháng sau, đúng
     * như lịch thật vẫn làm: game đếm ngày bằng cách cộng dồn vào DATE dựa
     * vào chỗ đó.</p>
     */
    static void set(Vm vm, VmObject self, int which, int value) {
        long local = localMillis(self);
        long days = CivilTime.floorDiv(local, CivilTime.DAY);
        int inDay = (int) CivilTime.floorMod(local, CivilTime.DAY);
        long[] civil = CivilTime.civilFromDays(days);
        long year = civil[0];
        int month = (int) civil[1];
        int day = (int) civil[2];
        int extra = 0;
        switch (which) {
            case YEAR:
                year = value;
                break;
            case MONTH:
                // Tháng ngoài 0..11 dồn sang năm, như java.util.Calendar.
                year += CivilTime.floorDiv(value, 12);
                month = (int) CivilTime.floorMod(value, 12) + 1;
                break;
            case DATE:
                day = 1;
                extra = value - 1;
                break;
            case DAY_OF_YEAR:
                month = 1;
                day = 1;
                extra = value - 1;
                break;
            case DAY_OF_WEEK: {
                int now = (int) CivilTime.floorMod(days + 4, 7) + 1;
                extra = value - now;
                break;
            }
            case HOUR_OF_DAY:
                inDay = inDay % 3600000 + value * 3600000;
                break;
            case HOUR:
                inDay = inDay % 3600000 + (inDay / 3600000 / 12) * 12 * 3600000
                        + value * 3600000;
                break;
            case AM_PM:
                inDay = inDay % (12 * 3600000) + value * 12 * 3600000;
                break;
            case MINUTE:
                inDay = inDay - (inDay / 60000 % 60) * 60000 + value * 60000;
                break;
            case SECOND:
                inDay = inDay - (inDay / 1000 % 60) * 1000 + value * 1000;
                break;
            case MILLISECOND:
                inDay = inDay - inDay % 1000 + value;
                break;
            default:
                throw vm.raise("java/lang/IllegalArgumentException", "Trường lịch " + which);
        }
        long newDays = CivilTime.daysFromCivil(year, month, day) + extra;
        long newLocal = newDays * CivilTime.DAY + inDay;
        VmObject zone = (VmObject) self.get("zone");
        int offset = zone == null ? 0 : ((Integer) zone.get("offset")).intValue();
        self.set("millis", Long.valueOf(newLocal - offset));
    }
}
