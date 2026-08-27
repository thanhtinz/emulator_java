package com.mobicore.tests;

import java.util.Base64;

/**
 * Vài tấm JPEG thật, để bài kiểm tra có cái mà đọc.
 *
 * <p>Nằm ở đây dưới dạng chữ chứ không phải tệp rời: kho mã này không giữ tệp
 * nhị phân, và một bộ đọc ảnh thì phải được thử bằng ảnh thật — tự dựng lấy
 * một tệp JPEG trong bài kiểm tra thì bộ dựng và bộ đọc rất dễ sai giống nhau
 * và cùng gật đầu với nhau.</p>
 *
 * <p>Mỗi tấm nhắm vào một chuyện khác nhau: lấy mẫu màu đầy đủ, lấy mẫu màu
 * thưa (kiểu hay gặp nhất), ảnh xám một thành phần, và một tấm progressive để
 * xem bộ đọc có nói thẳng là chưa đọc được không.</p>
 */
public final class JpegSamples {

    private JpegSamples() {
    }

    /** 48×32, lấy mẫu màu đầy đủ (4:4:4). */
    private static final String FULL_COLOUR =
            "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAIBAQEBAQIBAQECAgICAgQDAgICAgUEBAMEBgUG"
            + "BgYFBgYGBwkIBgcJBwYGCAsICQoKCgoKBggLDAsKDAkKCgr/2wBDAQICAgICAgUDAwUKBwYH"
            + "CgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgr/wAAR"
            + "CAAgADADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAA"
            + "AgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkK"
            + "FhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWG"
            + "h4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl"
            + "5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREA"
            + "AgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYk"
            + "NOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOE"
            + "hYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk"
            + "5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD8ddC+E54/0b9K7TQfhR93/Rv0r2bQfhP0"
            + "xa/+O12mg/Cf7v8Aov6V6mYcZb+8fKcHcf6x988Z0L4Tn5f9G/Su00H4T/d/0b9K9m0H4T/d"
            + "/wBF/Su00H4Tn5f9F/SvhMx4y394/q7g3j/4ffPGdC+E5+X/AEf9K7TQfhOcr/o36V7NoPwn"
            + "+7/ov6V2mg/Cflf9F/SvhMx4y394/q3g3j/4ffPFtC+E44/0X/x2u00L4T8D/Rf0r2bQfhP0"
            + "/wBGP5V2mg/Cf7ubU/lXw2Y8Zb+8f84/B3H/AMPvnjOhfCf7v+i/pXZ6F8Jx8v8Aov6V7PoP"
            + "wn+7/ox/75rtNB+E/wB3Nqfyr4XMeMt/eP6u4O4/+H3zxjQvhP0/0X9K7TQvhOPlza/pXs+g"
            + "/Cb7v+jH/vmu00H4T8rm2/8AHa+EzHjLf3j+ruDeP/h98//Z";

    /** 96×64, lấy mẫu màu thưa (4:2:0) — kiểu hay gặp nhất. */
    private static final String SUBSAMPLED =
            "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAA0JCgsKCA0LCgsODg0PEyAVExISEyccHhcgLikx"
            + "MC4pLSwzOko+MzZGNywtQFdBRkxOUlNSMj5aYVpQYEpRUk//2wBDAQ4ODhMREyYVFSZPNS01"
            + "T09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT0//wAAR"
            + "CABAAGADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAA"
            + "AgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkK"
            + "FhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWG"
            + "h4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl"
            + "5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREA"
            + "AgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYk"
            + "NOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOE"
            + "hYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk"
            + "5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDztY6kWOrCx1IsddbmZQqFdY6kWOrCx1Is"
            + "dZuZ2wqFdY6kWOrCx1IsdZOZ2QqFdY6kWOrCx1IsdZuZ2QqFdY6kWOrCx1IsdZuZ2wqFdY6k"
            + "WOp1jqRY6zczshUMBY6kWOp1jqRY66HUPzKFQgWOpFjqdY6kWOs3UOyFQrrHUix1YWOpFjrN"
            + "1DthUK6x1IsdWFjqRY6zdQ7IVCusdSLHVhY6kWOs3UOyFQrrHUix1YWOpFjrN1DshUOfWOpF"
            + "jqwsdSLHXQ6h+ZQqFdY6kWOrCx1IsdZuodsKhXWOpFjqwsdSLHWbqHZCoV1jqRY6sLHUix1m"
            + "6h2QqFdY6kWOp1jqRY6zdQ7YVCBY6esdWFjqRY6zdQ7IVDAWOnrHVhY6kWOuhzPzKFQrrHUi"
            + "x1YWOpFjrNzOyFQrrHUix1YWOpFjrNzO2FQrrHUix1YWOpFjrNzOyFQrrHUix1YWOpFjrJzO"
            + "yFQrrHUix1YWOpFjrNzOyFQ//9k=";

    /** 32×32, ảnh xám, chỉ một thành phần. */
    private static final String GREY =
            "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQFxQY"
            + "GBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/wAALCAAgACABAREA/8QAHwAA"
            + "AQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQR"
            + "BRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RF"
            + "RkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ip"
            + "qrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/9oACAEB"
            + "AAA/APllEzVyCHJHFa9na5I4rotOsckcVw0EOSOK17O1yRxXRadY5I4rr9I03JX5a8us7XJH"
            + "FdFp1jkjiuv0jTclflrvtB0fJX5a8S06xyRxXX6RpuSvy132g6Pkr8tem+G9CJKfJX//2Q==";

    /** 32×32 kiểu progressive: bộ đọc phải từ chối. */
    private static final String PROGRESSIVE =
            "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRof"
            + "Hh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwh"
            + "MjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wgAR"
            + "CAAgACADASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAABAb/xAAXAQEBAQEAAAAAAAAA"
            + "AAAAAAAEBQIG/9oADAMBAAIQAxAAAAGSUlT8GSlRbpVJSbijKSot3//EABYQAAMAAAAAAAAA"
            + "AAAAAAAAAAABAv/aAAgBAQABBQJQKBQKBQKBQKBQKBQKBQKBQKD/xAAWEQADAAAAAAAAAAAA"
            + "AAAAAAAAAwT/2gAIAQMBAT8BneTvJ3k7z//EABYRAAMAAAAAAAAAAAAAAAAAAAACA//aAAgB"
            + "AgEBPwFqjVGqNU//xAAUEAEAAAAAAAAAAAAAAAAAAABA/9oACAEBAAY/Agf/xAAUEAEAAAAA"
            + "AAAAAAAAAAAAAABA/9oACAEBAAE/IQf/AP8A/wD/AP/aAAwDAQACAAMAAAAQ5ali/8QAFhEA"
            + "AwAAAAAAAAAAAAAAAAAAACEx/9oACAEDAQE/EIMgyDIM/8QAFBEBAAAAAAAAAAAAAAAAAAAA"
            + "IP/aAAgBAgEBPxAf/wD/xAAWEAADAAAAAAAAAAAAAAAAAAAAITH/2gAIAQEAAT8QkiSJIkiS"
            + "JIkiSJIkiSJIkiSJIkj/2Q==";

    public static byte[] fullColour() {
        return Base64.getDecoder().decode(FULL_COLOUR);
    }

    public static byte[] subsampled() {
        return Base64.getDecoder().decode(SUBSAMPLED);
    }

    public static byte[] grey() {
        return Base64.getDecoder().decode(GREY);
    }

    public static byte[] progressive() {
        return Base64.getDecoder().decode(PROGRESSIVE);
    }
}
