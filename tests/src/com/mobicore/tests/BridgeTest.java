package com.mobicore.tests;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mỗi lời gọi từ Swift phải có một hàm thật ở cầu nối.
 *
 * <p>Ứng dụng iOS không được biên dịch ở đây — không có Xcode — nên một cái
 * tên gõ sai nằm im mãi. Đã nằm im thật: {@code setTimeZone} được gọi từ Swift
 * suốt một giai đoạn mà cầu nối chưa hề có hàm ấy, và {@code bridge.press(...)}
 * gọi vào một hàm tên thật là {@code pressButton:}. Cả hai đều là lỗi biên
 * dịch, và cả hai đều không ai thấy.</p>
 *
 * <p>Phép kiểm này không cần trình biên dịch: nó đọc tên hàm khai báo trong
 * {@code MobiCoreBridge.h}, đọc mọi chỗ Swift gọi {@code bridge.…}, rồi đối
 * chiếu — có tính đến lối Swift tự tách giới từ ở cuối tên hàm Objective-C
 * ({@code openAtPath:} thành {@code open(atPath:)}).</p>
 */
public final class BridgeTest extends Test {

    /**
     * Những giới từ Swift tách ra làm nhãn cho tham số đầu.
     *
     * <p>Đây là quy tắc "bỏ chữ thừa" của trình nhập Objective-C: tên hàm kết
     * thúc bằng một giới từ thì phần từ giới từ trở đi thành nhãn tham số.</p>
     */
    private static final String[] PREPOSITIONS = {
            "With", "At", "For", "From", "By", "In", "To", "On", "Of", "Into",
            "Using", "About", "Over", "Between", "After", "Before", "Against",
            "During", "Through", "Without", "Within", "Around", "As",
    };

    @Override
    public String name() {
        return "Cầu nối iOS khớp với Swift";
    }

    @Override
    public void run() throws Exception {
        File header = new File("ios/Bridge/MobiCoreBridge.h");
        File views = new File("ios/MobiCore");
        if (!header.exists() || !views.isDirectory()) {
            // Chạy từ nơi khác gốc kho mã: không có gì để soi, và nói dối rằng
            // mọi thứ ổn thì tệ hơn là im lặng.
            check(true, "không tìm thấy nguồn iOS, bỏ qua phép kiểm cầu nối");
            return;
        }
        String declarations = read(header);
        Set<String> known = swiftNames(declarations);
        check(known.size() > 100,
                "đọc được tên hàm của cầu nối: " + known.size() + " tên");

        List<String[]> calls = new ArrayList<String[]>();
        collectCalls(views, calls);
        check(calls.size() > 100, "đọc được lời gọi từ Swift: " + calls.size() + " chỗ");

        List<String> missing = new ArrayList<String>();
        for (int i = 0; i < calls.size(); i++) {
            String name = calls.get(i)[0];
            if (!known.contains(name)) {
                missing.add("bridge." + name + " (" + calls.get(i)[1] + ")");
            }
        }
        eq("", join(missing), "mọi lời gọi bridge.… đều có hàm thật ở cầu nối");
    }

    // ------------------------------------------------ đọc phía Objective-C

    /**
     * Tên mà Swift sẽ thấy, cho mỗi hàm và mỗi thuộc tính của cầu nối.
     *
     * <p>Một hàm cho ra tối đa hai tên: nguyên vẹn, và bản đã tách giới từ.</p>
     */
    private Set<String> swiftNames(String header) {
        Set<String> names = new HashSet<String>();
        String[] lines = header.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("@property")) {
                String name = lastWord(line);
                if (name.length() > 0) {
                    names.add(name);
                }
                continue;
            }
            if (!line.startsWith("- (") && !line.startsWith("+ (")) {
                continue;
            }
            int close = matchingParen(line, 2);
            if (close < 0) {
                continue;
            }
            String first = firstSelectorPiece(line.substring(close + 1));
            if (first.length() == 0) {
                continue;
            }
            names.add(first);
            String split = beforePreposition(first);
            if (split != null) {
                names.add(split);
            }
        }
        return names;
    }

    /** Phần tên đứng trước dấu hai chấm đầu tiên. */
    private String firstSelectorPiece(String rest) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                out.append(c);
            } else {
                break;
            }
        }
        return out.toString();
    }

    /** Tên còn lại sau khi cắt giới từ cuối cùng đi, hoặc null. */
    private String beforePreposition(String name) {
        int cut = -1;
        for (int i = 0; i < PREPOSITIONS.length; i++) {
            String word = PREPOSITIONS[i];
            int at = name.lastIndexOf(word);
            // Phải là một từ trọn vẹn trong lối viết lạc đà, và không phải
            // ngay đầu tên: "Into" trong "IntoSlot" thì được, "At" trong
            // "Attribute" thì không.
            if (at <= 0 || at + word.length() >= name.length()) {
                continue;
            }
            if (!Character.isUpperCase(name.charAt(at + word.length()))) {
                continue;
            }
            if (at > cut) {
                cut = at;
            }
        }
        return cut > 0 ? name.substring(0, cut) : null;
    }

    private int matchingParen(String line, int open) {
        int depth = 0;
        for (int i = open; i < line.length(); i++) {
            if (line.charAt(i) == '(') {
                depth++;
            } else if (line.charAt(i) == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String lastWord(String line) {
        StringBuilder out = new StringBuilder();
        for (int i = line.length() - 1; i >= 0; i--) {
            char c = line.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                out.insert(0, c);
            } else if (out.length() > 0) {
                break;
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------- đọc phía Swift

    private void collectCalls(File directory, List<String[]> into) throws Exception {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) {
                collectCalls(children[i], into);
            } else if (children[i].getName().endsWith(".swift")) {
                String[] lines = read(children[i]).split("\n");
                for (int line = 0; line < lines.length; line++) {
                    int at = 0;
                    while ((at = lines[line].indexOf("bridge.", at)) >= 0) {
                        String name = firstSelectorPiece(lines[line].substring(at + 7));
                        at += 7;
                        if (name.length() > 0) {
                            into.add(new String[]{name,
                                    children[i].getName() + ":" + (line + 1)});
                        }
                    }
                }
            }
        }
    }

    private String read(File file) throws Exception {
        byte[] raw = new byte[(int) file.length()];
        java.io.InputStream in = new java.io.FileInputStream(file);
        try {
            int read = 0;
            while (read < raw.length) {
                int step = in.read(raw, read, raw.length - read);
                if (step < 0) {
                    break;
                }
                read += step;
            }
        } finally {
            in.close();
        }
        return new String(raw, "UTF-8");
    }

    private String join(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(lines.get(i));
        }
        return out.toString();
    }
}
