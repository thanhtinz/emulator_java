# Roadmap

| Giai đoạn | Nội dung | Trạng thái |
| --- | --- | --- |
| 1 | JAR/JAD parser, metadata suite, bố cục lưu trữ, framebuffer + PNG | Xong |
| 2 | JVM bytecode interpreter (CLDC runtime) | Xong |
| 3 | Thư viện MIDP: Display, Canvas, Graphics, Image, Sprite | Xong |
| 4 | Device profile, input mapping, RMS + backup/restore | Xong |
| 5 | Ứng dụng Android (Kotlin) | Xong (chưa biên dịch được ở CI) |
| 6 | Ứng dụng iOS (SwiftUI + J2ObjC) | Xong (chưa biên dịch được ở CI) |
| 7 | Developer tools, network layer, modding | Xong |
| 8 | Tối ưu tương thích và hiệu năng | Kế tiếp |

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

## Giai đoạn 5 — đã hoàn thành

Module `android/`: Kotlin + Jetpack Compose, dùng trực tiếp core.

- `EmulatorEngine` chạy MIDlet trên thread riêng, đẩy framebuffer vào Bitmap
  và đánh thức Compose bằng bộ đếm khung hình.
- Màn hình emulator vẽ không lọc pixel, integer scaling, chuyển tọa độ chạm
  ngược về hệ tọa độ màn hình giả lập.
- Keypad ảo phát riêng press/release để `getKeyStates` hoạt động đúng.
- Home / Library / Game Detail / Game settings / Saves / Tools / Settings.
- Import qua Storage Access Framework, không xin quyền filesystem rộng.

Xem `docs/ANDROID.md`. Lưu ý: môi trường dựng repo này chặn `dl.google.com`
nên chưa chạy được `gradlew` để biên dịch module Android.

## Giai đoạn 6 — đã hoàn thành

- `MobiCoreFacade`: API phẳng chỉ dùng `String`/`byte[]`/`int[]`/số nguyên,
  mọi cấu trúc đi qua JSON. Đây là toàn bộ bề mặt mà bản dịch J2ObjC phải
  phơi ra, nên cầu nối nhỏ và ổn định. Có bộ test riêng (47 checks).
- `ios/Bridge/MobiCoreBridge.[hm]`: lớp Objective-C duy nhất chạm vào kiểu do
  J2ObjC sinh ra; đổi `int[]` ARGB thành `CGImage` bằng bộ đệm dùng lại.
- Ứng dụng SwiftUI: Home, Library, Game Detail, Emulator (keypad ảo,
  integer scaling, không lọc pixel), Game settings, Saves, Tools, Settings.
- `ios/build-core.sh` dịch `core/src`; `ios/project.yml` sinh project bằng
  XcodeGen.

Xem `docs/IOS.md`.

## Giai đoạn 7 — đã hoàn thành

**Lớp mạng.** Mọi kết nối đều đi qua `NetworkStack`:

- `NetworkPolicy`: chế độ chặn/hỏi/cho phép, allowlist và denylist theo host.
  Mặc định là **hỏi** — đúng yêu cầu "cảnh báo khi game yêu cầu network".
  Nếu không có ai để hỏi, lần gọi đó bị từ chối nhưng **không** ghi nhớ là
  cấm: người dùng chưa nói không.
- `NetworkMonitor`: ghi lại từng request/response, kể cả lần bị chặn, kèm
  preview nội dung (payload nhị phân chỉ tóm tắt, không đổ ra).
- `NetworkTransport`: `HttpTransport` (dùng `HttpURLConnection`, chạy được cả
  Android lẫn iOS sau khi dịch), `BlockedTransport` (mặc định),
  `LoopbackTransport` (local server testing và là nửa đầu của server bridge —
  game có backend đã chết vẫn chơi được).
- `javax.microedition.io`: `Connector`, `HttpConnection`, `ContentConnection`.
  Request được gửi trễ, đúng lúc game cần câu trả lời — vừa đúng đặc tả MIDP
  vừa cho phép lớp policy nhìn thấy trọn vẹn request trước khi nó rời máy.

**Modding.**

- `ModPackage`: manifest `mod.json`, danh sách resource thay thế, cờ cảnh báo
  khi mod mang theo `.class`.
- `ModManager`: cài (luôn backup game trước), bật/tắt, gỡ, và xếp lớp mod lên
  phiên chạy. **File JAR gốc không bao giờ bị sửa** — tắt mod là game trở lại
  nguyên trạng chính xác.

**Developer tools.**

- `JadEditor`: sửa thuộc tính, round-trip nguyên vẹn, và validate (thiếu
  vendor là lỗi, version vô nghĩa là cảnh báo, `MIDlet-n` thiếu tên lớp là lỗi).
- `RmsEditor`: xem/sửa/xóa record dạng hex và text, giải mã int big-endian
  như cách game ghi; mọi thay đổi được flush xuống đĩa ngay.
- `CrashReport`: gộp loại exception, stack trace giả lập, log và metadata
  suite. Cố tình không kèm bất kỳ thông tin nào về thiết bị hay người dùng.
