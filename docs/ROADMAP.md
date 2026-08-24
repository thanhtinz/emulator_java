# Roadmap

| Giai đoạn | Nội dung | Trạng thái |
| --- | --- | --- |
| 1 | JAR/JAD parser, metadata suite, bố cục lưu trữ, framebuffer + PNG | Xong |
| 2 | JVM bytecode interpreter (CLDC runtime) | Xong |
| 3 | Thư viện MIDP: Display, Canvas, Graphics, Image, Sprite | Xong |
| 4 | Device profile, input mapping, RMS + backup/restore | Xong |
| 5 | Ứng dụng Android (Kotlin) | Đang làm |
| 6 | Ứng dụng iOS (SwiftUI + J2ObjC) | |
| 7 | Developer tools, network layer, modding | |
| 8 | Tối ưu tương thích và hiệu năng | |

## Giai đoạn 1 — đã hoàn thành

- `JarArchive`: đọc toàn bộ suite vào bộ nhớ, chuẩn hóa tên entry.
- `AttributeSet`: bảng thuộc tính dùng chung cho `MANIFEST.MF` và `.jad`,
  tra cứu không phân biệt hoa thường, giữ nguyên thứ tự để JAD editor
  round-trip được, nối dòng gấp khúc của manifest.
- `MidletSuiteInfo`: gộp manifest + JAD (JAD ưu tiên), phân tích `MIDlet-<n>`,
  sinh `suiteId` an toàn cho tên thư mục.
- `StorageLayout` + `Vfs`: bố cục thư mục và lớp trừu tượng filesystem
  (bản local và bản in-memory cho test).
- `Framebuffer`: bề mặt ARGB với đầy đủ primitive mà MIDP `Graphics` cần,
  clip, translate, alpha blend, scale nearest-neighbour và integer scaling.
- `PngWriter`: encoder PNG tự viết để screenshot hoạt động giống nhau trên
  mọi nền tảng.
- `BitmapFont` + `FontData`: ba cỡ font MIDP, bold/italic/underline suy ra
  lúc vẽ.

## Giai đoạn 2 — đã hoàn thành

- `ClassFileParser`: đọc class file tới version 68, constant pool, field,
  method, `Code`, `ConstantValue`, `LineNumberTable`, `SourceFile`.
- `Interpreter`: toàn bộ opcode CLDC (số học int/long/float/double, mảng,
  rẽ nhánh, `tableswitch`/`lookupswitch`, `jsr`/`ret`, nhóm `dup`, `wide`,
  bốn dạng `invoke`, `new`/`newarray`/`multianewarray`, `checkcast`,
  `instanceof`, `athrow` + bảng exception, `monitorenter`/`monitorexit`).
- `Vm`: nạp lớp lười, link + gán slot, chạy `<clinit>` một lần, heap,
  intern string, mirror `java.lang.Class`, ngân sách lệnh để một game treo
  không kéo theo cả ứng dụng.
- Thư viện CLDC viết bằng native: `java.lang` (Object, Class, String,
  StringBuffer/StringBuilder, System, Math, wrapper, 33 lớp throwable,
  Thread + Runnable), `java.io` (InputStream, ByteArrayInputStream,
  DataInputStream, OutputStream, ByteArrayOutputStream, DataOutputStream,
  PrintStream), `java.util` (Vector, Stack, Hashtable, Enumeration, Random,
  Date).
- Kiểm thử vi sai: chương trình `fixtures/demo/VmProbe.java` được biên dịch
  thật rồi chạy song song trên JVM host và trên interpreter; mọi kết quả
  phải trùng khớp.

## Giai đoạn 3 — đã hoàn thành

- `PngReader`: giải mã PNG greyscale/truecolour/palette/alpha ở 1/2/4/8 bit,
  đủ năm bộ lọc scanline và chunk `tRNS`.
- `Transforms`: tám phép biến đổi sprite của MIDP.
- `javax.microedition.lcdui`: `Graphics` (toàn bộ primitive, clip, translate,
  anchor, `drawRegion`, `drawRGB`), `Image` (mutable/immutable, tạo từ PNG,
  từ resource, từ mảng RGB, cắt + transform), `Font` ba cỡ với
  bold/italic/underline.
- `javax.microedition.lcdui.game`: `Layer`, `Sprite` (frame sequence,
  transform, reference pixel, collision rectangle), `TiledLayer` (kể cả
  animated tile), `LayerManager` (view window, thứ tự vẽ).
- Vòng đời MIDlet: `MIDlet`, `Display`, `Displayable`, `Canvas`,
  `GameCanvas` (back buffer + `flushGraphics` + `getKeyStates`), `Command`,
  `CommandListener`.
- `EmulatorSession`: khởi động suite, vẽ từng khung, đưa phím/chạm vào game,
  chụp màn hình PNG, pause/resume/destroy. Đây là toàn bộ API mà lớp UI của
  Android/iOS cần.
- `EmulatorLog`: ring buffer cho console và crash report.
- MIDlet demo `demo/SkyRunner.java` là chương trình J2ME thật, được biên dịch
  ra bytecode và chạy bằng chính interpreter.

## Giai đoạn 4 — đã hoàn thành

- `Json`: bộ đọc/ghi JSON tự viết (giữ thứ tự khóa để file dễ đọc và dễ so
  sánh), dùng cho profile và library index.
- `DeviceProfile`: catalog 128×128, 128×160, 176×208, 176×220, 240×320,
  320×240, 240×400 cảm ứng, cộng độ phân giải tùy chỉnh; tự đoán profile từ
  thuộc tính JAD nếu suite có khai báo.
- `InputProfile`: remap từng phím, preset Nokia/Sony Ericsson/Samsung,
  turbo (auto-repeat) và macro theo từng game.
- `GameProfile`: scale (fit/integer/stretch/original), orientation, giới hạn
  FPS, âm lượng, chế độ mạng, skin, favourite, số lần chơi; hàm `viewport`
  tính khung hiển thị chính xác cho từng chế độ scale.
- `RecordStoreManager`: RMS lưu vĩnh viễn theo sandbox riêng từng suite,
  định dạng nhị phân có magic + version, ghi ngay khi game ghi record.
- `javax.microedition.rms`: `RecordStore`, `RecordEnumeration`,
  `RecordFilter`, `RecordComparator` (filter và comparator của game được gọi
  ngược lại bằng bytecode) cùng đủ bộ exception.
- `GameLibrary`: import/cài đặt vào sandbox, index bền vững, tìm kiếm, sắp
  xếp, favourite, hồ sơ theo game, gỡ cài đặt, backup/restore trọn gói và
  `resetGameData` luôn sao lưu trước khi xóa.
