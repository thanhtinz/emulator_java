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
| 8 | Âm thanh: javax.microedition.media | Xong |
| 9 | Tương thích: Timer và kiểm tra trước khi chơi | Xong |
| 10 | Tối ưu hiệu năng | Xong |
| 11 | Lưu trạng thái, chơi tiếp | Xong |
| 12 | Giao diện sáng / tối | Xong |
| 13 | Tìm game không dấu | Xong |
| 14 | Đánh dấu phím mềm L/R và màn hình ngang | Xong |
| 15 | Menu trong game và bàn phím đổi kiểu | Xong |
| 16 | Chạm thẳng vào thanh lệnh của game | Xong |
| 17 | Phím hướng to bằng phím số, trang chủ theo kiểu J2ME Loader | Xong |
| 18 | Thư viện ảnh chụp | Xong |
| 19 | Bộ cấu hình dùng lại | Xong |
| 20 | Nhiều ô lưu trạng thái | Xong |
| 21 | Chỉnh tốc độ chạy game | Xong |
| 22 | Liên thanh (turbo) | Xong |
| 23 | Đổi gán phím từng nút | Xong |
| 24 | Sao lưu toàn bộ thư viện | Xong |
| 25 | Tua lại vài giây | Xong |
| 26 | Chọn MIDlet trong gói | Xong |
| 27 | Thống kê thời gian chơi | Xong |
| 28 | Phát nhạc MIDI | Xong |
| 29 | API riêng của Nokia | Xong |
| 30 | Rung thật | Xong |
| 31 | API Siemens, Samsung, Motorola | Xong |
| 32 | Một loại màn hình duy nhất | Xong |
| 33 | Chỉnh bàn phím ảo: độ rõ, hình phím, tự mờ | Xong |
| 34 | Quay màn chơi thành ảnh động GIF | Xong |
| 35 | Tự sắp xếp bàn phím ảo | Xong |
| 36 | Tay cầm và bàn phím ngoài | Xong |
| 37 | Tệp riêng của game (JSR-75) | Xong |
| 38 | Cài game từ liên kết | Xong |
| 39 | Bộ sưu tập trong thư viện | Xong |
| 40 | Chia sẻ ảnh chụp và đoạn quay | Xong |
| 41 | Nghiêng máy để lái | Xong |
| 42 | Chơi tiếp ngay ở màn hình chính | Xong |
| 43 | Game hỏng thì nói vì sao | Xong |
| 44 | Máy ảo khai nó là máy gì | Xong |
| 45 | Game treo thì vẫn thoát được | Xong |
| 46 | Đọc được ảnh JPEG | Xong |
| 47 | Nối thẳng bằng socket | Xong |

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
- `DeviceProfile`: một màn hình 240×320 duy nhất, cùng chính nó xoay ngang
  320×240; chiều màn hình lấy theo khai báo trong JAD nếu game có nói.
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
- `SocketTransport`: `RealSockets` (dùng `java.net`, chạy được cả Android lẫn
  iOS sau khi dịch), `BlockedSockets` (mặc định), `LoopbackSockets` (cả một
  cuộc trò chuyện chạy trong bộ nhớ — nửa sau của server bridge).
- `javax.microedition.io`: `Connector`, `HttpConnection`, `ContentConnection`.
  Request được gửi trễ, đúng lúc game cần câu trả lời — vừa đúng đặc tả MIDP
  vừa cho phép lớp policy nhìn thấy trọn vẹn request trước khi nó rời máy.
  Từ giai đoạn 47 còn có `SocketConnection`, `ServerSocketConnection` và
  `UDPDatagramConnection` cho những kết nối mở suốt.

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

## Bản địa hoá và giao diện toàn màn hình

- Font bitmap phủ ASCII + toàn bộ tiếng Việt (kể cả dấu chồng như Ẫ, Ẵ, Ỡ),
  bốn cỡ 17/21/26/37px thay cho 13/17/21px trước đây.
- Chiều cao mỗi cỡ chữ được đo từ phạm vi mực thật; metrics của font báo
  thiếu nên dấu ngã trên chữ hoa từng bị cắt mất.
- Mọi chữ trong giao diện Android, iOS và bộ xem trước đều là tiếng Việt,
  kể cả tên phím `Canvas.getKeyName()` mà trò chơi in ra màn hình.
- Màn hình chơi chạy toàn màn hình: ẩn thanh hệ thống, khung hình phóng theo
  bội số nguyên, không làm mượt.
- Bàn phím ảo: cụm mũi tên bên phải, bàn số 3×4 bên trái, hàng phím mềm trên
  cùng — đúng cách cầm một chiếc máy J2ME.

## Sửa lỗi hiển thị bị vỡ hạt

Ba nguyên nhân, sửa cả ba:

- **Phóng ảnh cứng.** Trước đây dùng nearest-neighbour với lý do "giữ pixel
  art". Sai: máy J2ME thật nhét 240×320 vào chừng 2 inch nên điểm ảnh bé li
  ti; phóng 2× cứng trên màn hình hiện đại biến mỗi điểm thành một ô vuông
  nhìn thấy rõ. Thêm `Framebuffer.scaleSmooth` (bilinear) và tuỳ chọn
  `GameProfile.smoothing`, **mặc định bật**.
- **Chính hình vẽ của game bị răng cưa.** MIDP không có khử răng cưa, và ở 2
  inch thì không cần. Phóng to lên mới lộ. `Framebuffer.setAntialias` khử
  răng cưa cho đường chéo, tam giác, cung tròn và góc bo bằng cách lấy mẫu
  4×4 mỗi điểm ảnh. Hình chữ nhật thẳng trục thì vẽ chính xác — không có gì
  để làm mượt. **Cố ý không áp dụng cho ảnh off-screen**: rất nhiều game vẽ
  sprite trên một màu khoá rồi lấy đúng màu đó làm trong suốt, cạnh bị pha
  màu sẽ để lại viền.
- **Font trong game quá to.** Lần trước phóng to font cho giao diện dễ đọc,
  nhưng nó cũng phóng luôn font mà game nhìn thấy — to hơn thật khoảng 40%,
  làm bảng điểm và menu của game vỡ bố cục. Nay tách hẳn:
  `core` giữ ba cỡ MIDP 14/17/20px, 1 bit mỗi điểm ảnh, đúng như máy thật;
  `tools` có bộ font riêng cho giao diện, 2 bit alpha nên chữ mượt, và chỉ
  nằm ở module desktop nên không lên máy.

Cũng sửa một lỗi trong `build.sh`: lỗi biên dịch từng bị nuốt mất và bộ test
chạy lại class cũ. Giờ biên dịch hỏng là dừng hẳn.

## Màn hình chơi đúng mô hình MIDP

Trước đây màn chơi chỉ là "khung hình game + bàn phím", thiếu hẳn phần hệ
thống mà mọi máy J2ME đều có. Hai chỗ sai về **chức năng**, không phải thẩm mỹ:

- **Lệnh của game không hiển thị ở đâu.** MIDP không cho MIDlet tự vẽ
  `Command` của nó — thiết bị bắt buộc phải vẽ. Game demo đăng ký "Tạm dừng"
  và "Thoát", emulator đọc và lưu lại rồi… không hiện ra, nên người chơi
  không có cách nào bấm được. Nay `SystemChrome` vẽ nhãn phím mềm ở đáy màn
  hình, và hai nút phím mềm dưới bàn phím hiện đúng nhãn đó, bấm là chạy lệnh.
  Quy ước đặt phím theo đúng thói quen máy thật: lệnh BACK/CANCEL/EXIT/STOP
  nằm bên phải, còn lại bên trái; nhiều hơn một lệnh bên trái thì gộp thành
  "Tuỳ chọn".
- **`setFullScreenMode()` bị bỏ qua.** `getWidth()/getHeight()` luôn trả về
  nguyên màn hình. Máy thật ở chế độ thường thu nhỏ canvas để nhường chỗ cho
  thanh tiêu đề và thanh phím mềm. Nay canvas đúng kích thước, `sizeChanged`
  được gọi khi đổi, toạ độ chạm quy đổi về hệ toạ độ canvas, và vùng vẽ bị
  clip nên game không vẽ đè lên phần của hệ thống.

Ngoài ra: thanh của emulator không còn lặp lại tên game hay chữ "Tạm dừng" —
trùng chữ với lệnh của chính game là cách nhanh nhất khiến người chơi bấm nhầm.
Bàn phím ảo chỉ giữ những phím game thật sự bấm tới: hai phím mềm, bàn số và
phím hướng. Gọi, Kết thúc và Xóa từng có mặt vì máy là một cái điện thoại,
không phải vì MIDlet cần chúng, và trên màn hình chúng chỉ chiếm chỗ của những
phím có tác dụng. `KEY_CLEAR` vẫn còn trong bảng ánh xạ, chỉ là không còn nút
nào trên bàn phím ảo gửi nó.

## LCDUI cấp cao

Trước đây emulator chỉ có nửa dưới của LCDUI: `Canvas`, `Graphics`, `Image`,
`Font` và bộ `game`. Đủ cho một game tự vẽ từng điểm ảnh, nhưng gần như mọi
MIDlet thương mại vẫn dựng menu, ô nhập tên và hộp thoại bằng nửa còn lại —
và ở đây, một lớp thiếu không phải là thiếu tính năng: nó là
`NoClassDefFoundError` ngay khi game được nạp.

Nay có đủ: `Screen`, `Form`, `List`, `TextBox`, `Alert`, `AlertType`,
`Ticker`, `Item`, `StringItem`, `ImageItem`, `TextField`, `Gauge`,
`DateField`, `Choice`, `ChoiceGroup`, `ItemStateListener`.

- **Máy vẽ, không phải game vẽ.** Đúng như MIDP quy định: đặc tả mô tả một
  `List` *là gì*, không bao giờ mô tả nó *trông ra sao*, vì đó là việc của
  thiết bị. `ScreenRenderer` vẽ chúng bằng chính framebuffer của core, nên
  Android, iOS và bản xem trước desktop nhận cùng một giao diện.
- **Bàn phím điều khiển màn hình.** `ScreenInput` xử lý lên/xuống, chọn, bật
  tắt ô đánh dấu, kéo `Gauge` và gõ chữ. Gõ theo kiểu đa chạm — cách duy nhất
  một bàn phím số từng nhập được chữ, và là thứ một game hỏi tên người chơi
  mong đợi. Khi con trỏ đang ở một `TextField`, các phím số thuộc về nó: số 2
  gõ ra "a" chứ không đi lên, đúng như máy thật.
- **Cảm ứng.** Không có trong đặc tả, vì máy dùng LCDUI cấp cao đều là máy
  bàn phím. Có ở đây vì emulator chạy trên điện thoại không có bàn phím nào
  cả: chạm lần đầu chọn dòng, chạm lại mới chạy.

## Menu "Tuỳ chọn"

Nhãn phím mềm trái đổi thành "Tuỳ chọn" khi có nhiều hơn một lệnh muốn nó —
nhưng danh sách phía sau chưa từng được vẽ, nên lệnh thứ ba trở đi được đọc,
được đếm, và không bao giờ bấm tới được. Nay bấm phím mềm trái mở đúng danh
sách đó: lên/xuống chọn, phím giữa chạy, phím mềm phải thoát ra. Áp dụng cho
cả `Canvas` lẫn màn hình cấp cao.

MIDlet `demo/MenuDemo.java` là chương trình J2ME thật dựng hoàn toàn bằng
`List`, `Form`, `TextBox` và `Alert` — bộ test lái nó bằng bàn phím, và
`./build.sh run com.mobicore.tools.Preview` chụp lại từng màn hình.

## Biểu tượng lấy sẵn, không tự vẽ

Trước đây các biểu tượng trong bản xem trước desktop được vẽ tay bằng hình
học: một tam giác làm mái nhà, hai hình tròn làm nút chơi, mấy đường chéo làm
ảnh bìa. Chúng không giống bộ biểu tượng nào, và mỗi lần Android hay iOS đổi
là bản xem trước lệch theo.

Nay cả ba nền tảng dùng chung một bộ **Material Symbols** (Apache 2.0):

- Android vẽ thẳng qua `Icons.Filled`.
- iOS dùng SF Symbols tương ứng.
- Bản xem trước không có sẵn bộ nào, nên `codegen/IconGen.java` đọc các tệp
  SVG trong `assets/icons`, tự phân tích đường dẫn SVG, tô ở 64×64 có khử răng
  cưa rồi sinh `IconData.java`. Khi vẽ, biểu tượng luôn được thu nhỏ chứ không
  phóng to, nên không bao giờ vỡ hạt.

Ảnh bìa mặc định của trò chơi cũng vậy: nền màu suy ra từ tên, và dấu ở giữa
là chính biểu tượng tay cầm của bộ Material, không còn hình tự chế.

## Nút nhập trò chơi ở trang chủ

Nhập game trước đây chỉ là một dòng chữ nhỏ ở góc tiêu đề. Đó là việc đầu tiên
một máy mới cài phải làm và là lý do quay lại thường xuyên nhất, nên nay nó là
một nút tròn nổi, có biểu tượng tải về, nằm ở góc dưới bên phải ngay trên
thanh tab — ở cả bản xem trước, Android và iOS. Nút tròn nhỏ vì nó phải luôn
ở đó khi danh sách cuộn, mà không lấy mất một dải màn hình của chính các trò
chơi.

## Đặt tên và đổi ảnh bìa cho game

Tên và ảnh của một game trước đây là bất biến: lấy từ manifest trong JAR, và
không ai sửa được. Với game lậu hoặc bản chép tay — thứ chiếm phần lớn kho
J2ME — tên trong manifest thường vô nghĩa, còn ảnh bìa thì không có.

Nay đổi được cả hai:

- **Đổi tên** chỉ đổi tên hiển thị. Tên trong manifest được giữ nguyên trong
  `LibraryEntry.originalTitle`, nên luôn hoàn tác được, và cài lại đúng bộ đó
  vẫn được nhận ra là cùng một game. Manifest bên trong JAR không bao giờ bị
  ghi đè: một game tự đọc tên của chính nó sẽ lệch với thư viện.
  Tên trống bị từ chối ngay, không lưu.
- **Đổi ảnh bìa** ghi đè `artwork.png` trong hộp cát của game. Chỉ nhận PNG —
  đó là định dạng duy nhất emulator giải mã được ở mọi nơi, kể cả phía MIDP.
  Ảnh người dùng chọn (thường là JPEG/HEIC) được cắt vuông, thu về tối đa 256
  px rồi mã hoá lại thành PNG ngay trên máy: Android dùng `Artwork.pngFrom`,
  iOS dùng `Artwork.png(from:)`.
- **Trả về mặc định** lấy lại tên trong manifest và icon gốc trong JAR. JAR
  vẫn là nguồn thật, nên không có gì mất đi khi thay ảnh.

Nút "+" ở trang chủ nhập game; trang chi tiết có mục "Tên và ảnh bìa", còn
ảnh bìa có sẵn nút sửa ngay ở góc — chỗ người ta nhìn vào khi muốn đổi ảnh.


## Âm thanh

`javax.microedition.media` trước đây không có. Với một game có tiếng, đó không
phải là thiếu tính năng: `Manager` không nằm trên class path nên trình nạp lớp
hỏng ngay trước khung hình đầu tiên — game không chạy được, chứ không phải
chạy mà im lặng.

Nay có đủ `Manager`, `Player`, `PlayerListener`, `Control`, `MediaException`,
`VolumeControl` và `ToneControl`.

- **Phát được:** `Manager.playTone` (một nốt), chuỗi nốt của `ToneControl`
  (kể cả block, repeat, set volume, tempo, resolution) và WAV không nén —
  8/16 bit, mono hoặc stereo, tự trộn về mono 16-bit.
- **Không phát được:** MIDI và MP3. Máy thật giải mã bằng phần cứng; ở đây
  chúng bị **từ chối một cách trung thực**: `Player` vẫn tạo được, đến khi
  `realize()` thì ném `MediaException`, và game chạy tiếp không tiếng thay vì
  chết. Đa số game bắt và bỏ qua ngoại lệ này.
- **Tách lõi khỏi loa.** Lõi chỉ tổng hợp và giải mã, rồi đưa `AudioClip`
  (PCM 16-bit mono) cho `AudioSink`. Android nối vào `AudioTrack`, iOS nối
  vào `AVAudioPlayer`. Không có loa thì sink mặc định là `AudioLog` — ghi lại
  thay vì phát, vẫn đếm thời gian đúng, nên game chờ một tiếng kết thúc vẫn
  chờ đúng chừng ấy.
- **Âm lượng là của người dùng.** Game xin bao nhiêu cũng bị nhân với mức
  người dùng đặt cho game đó; tắt tiếng là tắt hẳn. Đổi âm lượng có hiệu lực
  ngay giữa lúc chơi, không phải đợi khởi động lại.

MIDlet `demo/SoundDemo.java` chạy bằng bytecode trong bộ test: bíp, một bản
nhạc dựng bằng tone sequence có block lặp, một hiệu ứng WAV, và một player
MIDI bị từ chối. `build/screenshots/13-sound.png` chụp màn hình đó kèm danh
sách những gì game đã phát.


## Không bắt người dùng cấu hình

Người chơi chỉ muốn chơi. Những thứ màn hình cài đặt từng hỏi — máy giả lập cỡ
bao nhiêu, bàn phím hãng nào, có cho vào mạng không — đều đã nằm sẵn trong bộ
cài, nên `AutoSetup` **đọc ra từ game** thay vì hỏi, ngay lúc nhập:

- **Kích thước màn hình:** ưu tiên khai báo `Nokia-MIDlet-Original-Display-Size`
  hoặc `MIDlet-Screen-Size`; không có thì suy từ ảnh lớn nhất trong JAR — ảnh
  nền của game được vẽ đúng cỡ máy. Ảnh tên `icon`, `logo`, `thumb` bị loại, và
  ảnh trùng đúng một máy trong danh mục được ưu tiên hơn ảnh chỉ "to nhất".
- **Bàn phím:** theo thuộc tính `Nokia-`, `SonyEricsson-`/`SEMC-`, `Samsung-`
  hoặc theo tên nhà phát hành. Sai chỗ này là cả cụm phím không ăn — đúng loại
  lỗi mà người chơi không bao giờ nên phải tự chẩn đoán.
- **Mạng:** quét bytecode xem game có dùng `Connector`/`HttpConnection` không.
  Không dùng thì **tắt hẳn**, và người dùng không bị hỏi lần nào.
- **Âm thanh, giới hạn khung hình:** có `Manager` thì bật tiếng sẵn; dùng
  `GameCanvas` thì 30 hình/giây, còn game theo màn hình chỉ 20 để đỡ tốn pin.

Mỗi kết luận được ghi lại thành **một dòng tiếng Việt** hiện ngay đầu màn hình
cài đặt ("Màn hình 240x320 — cỡ phổ biến nhất", "Không dùng mạng — đã tắt"…).
Đoán mà người dùng không thấy là đoán họ không sửa được.

Toàn bộ phần còn lại nằm sau nút **Nâng cao**, mặc định đóng, kèm câu "chỉ chỉnh
khi game chạy sai". Chỉnh tay bất cứ giá trị nào đã dò thì cờ `auto` tắt — giao
diện thôi nhận là đã "đo được" thứ thực ra do người dùng chọn — và nút **Dò lại**
cấu hình lại từ đầu, giữ nguyên âm lượng và mục yêu thích vì đó là lựa chọn của
người dùng chứ không phải kết quả dò.


## Timer — đồng hồ mà phần lớn MIDlet chạy bằng

Rất nhiều game J2ME không có vòng lặp riêng: chúng `new Timer().schedule(task,
0, 50)` rồi để `TimerTask` lo tất cả. Thiếu hai lớp này thì game không chạy
chậm hay lạ — trình nạp lớp hỏng và game không khởi động được.

`java.util.Timer` và `java.util.TimerTask` nay có đủ: `schedule` (một lần, và
lặp theo khoảng cách giữa hai lần chạy), `scheduleAtFixedRate` (giữ đúng nhịp
gốc), `cancel` cả ở cấp timer lẫn cấp task, và `scheduledExecutionTime`.

Không có luồng nào được tạo ra. Task nằm trong hàng đợi và được emulator chạy
**giữa hai khung hình, trên đúng luồng của MIDlet**. Đây là khác biệt cố ý so
với máy thật, và là phía an toàn hơn: callback của game gần như luôn động vào
màn hình hoặc trạng thái của chính nó, và game thời đó được viết với giả định
lúc ấy không có gì khác chạy song song. Task trễ nhịp chỉ chạy **một lần** chứ
không chạy bù từng nhịp đã lỡ — game bị treo nền một phút thì chơi tiếp, không
phải "đuổi" một phút.

## Kiểm tra tương thích trước khi chơi

Thiếu một lớp trong J2ME không làm game yếu đi: game chết ngay lúc nạp, màn
hình đen, không một lời giải thích. Thông tin để đoán trước điều đó nằm sẵn
trong JAR, nên `Compatibility` đọc **constant pool của từng lớp** lúc nhập và
kết luận:

- **Chạy tốt** — chỉ dùng API đã hỗ trợ.
- **Thiếu vài thứ** — chạy được nhưng có phần mô phỏng chưa đủ (ví dụ nhạc
  MIDI).
- **Chưa chạy được** — cần gói chưa hỗ trợ, và nói rõ gói nào: 3D (M3G),
  Bluetooth, định vị, API riêng của Nokia/Siemens/Samsung/Motorola…

Đọc constant pool chứ không tìm chuỗi trong bytes: một hằng chuỗi trong game
tình cờ chứa tên gói không phải là bằng chứng game dùng gói đó — bộ test có
hẳn một trường hợp cho chuyện này.

Kết quả hiện thành huy hiệu ngay trên trang chi tiết, cạnh nút Chơi.


## Hiệu năng

Trước hết là **đo được đã**: `./build.sh run com.mobicore.tools.Benchmark` chạy
ba phép đo trên đồng hồ đóng băng — bytecode thuần, gọi hàm ảo, một khung hình
thật của MIDlet, và bộ phóng ảnh chạy mỗi khung. Lấy kết quả tốt nhất trong ba
lần chứ không lấy trung bình: lần chậm nghĩa là máy bận, mà trung bình vào thì
là đang đo cái máy.

Kết quả trên cùng một máy, trước → sau:

| Phép đo | Trước | Sau |
| --- | --- | --- |
| Bytecode | 1,5 M vòng/giây | 2,2 M vòng/giây |
| Gọi hàm ảo | 3,6 M lượt/giây | 4,4 M lượt/giây |
| Khung hình game | 150 hình/giây (6,66 ms) | **618 hình/giây (1,62 ms)** |
| Phóng ảnh 2× | 12,83 ms | **3,46 ms** |

Bốn thay đổi, tìm ra bằng cách lấy mẫu ngăn xếp chứ không đoán:

- **Viền mềm chỉ tính ở viền.** `fillRegion` lấy 16 mẫu mỗi điểm ảnh để khử
  răng cưa. Nhưng điểm ảnh có cả bốn góc nằm trong hình thì nằm trọn trong
  hình, và cả bốn góc lẫn tâm nằm ngoài thì không thuộc hình. Thử bốn góc
  trước biến một tam giác to từ 16 phép thử mỗi điểm xuống còn 4 ở gần như
  toàn bộ diện tích. Đây là phần lớn thời gian vẽ của game — ảnh chụp trước và
  sau giống hệt nhau.
- **Phóng ảnh bằng số nguyên**, vị trí lấy mẫu theo cột tính một lần cho cả
  khung thay vì mỗi điểm ảnh, và cho phép dùng lại đệm đích (`scaleSmoothInto`)
  — một khung 480×640 hơn một megabyte, cấp phát mỗi khung thì bộ dọn rác làm
  việc nhiều hơn cả emulator.
- **Mô tả hàm phân tích một lần** lúc nạp lớp thay vì mỗi lần gọi.
- **Nhớ kết quả tra hàm ảo** theo lớp: tra một lần rồi dùng lại, vì lớp ở đây
  không bao giờ bị định nghĩa lại.
- **Font đậm/nghiêng được nhớ** thay vì dựng lại mỗi lần vẽ chữ.


## Lưu trạng thái và chơi tiếp

Phần lớn game J2ME không lưu gì, hoặc chỉ lưu điểm cao. Thoát giữa màn là chơi
lại màn đó từ đầu — mà trên điện thoại, việc thoát nhiều khi không do người
chơi quyết định: một cuộc gọi, một tin nhắn, hết pin.

`SaveState` ghi lại **heap của máy ảo**: mọi đối tượng với tới được từ biến
static của các lớp, từ MIDlet và từ màn hình đang hiện. Đối tượng ở đây rất
gọn — một kiểu, một dãy số nguyên, một dãy tham chiếu — nên việc duyệt là đơn
giản. Phần không gọn là trạng thái phía host, và mỗi loại được viết tay: chuỗi,
số đóng hộp, pixel của ảnh, hạt ngẫu nhiên, nội dung tập hợp, và trạng thái của
`Sprite` / `TiledLayer` / `LayerManager`.

Thứ **không** ghi được thì **dừng lại và nói rõ**: một kết nối đang mở, một kho
dữ liệu, một bản nhạc đang phát. Bản lưu âm thầm bỏ mất chúng rồi khôi phục ra
một game hỏng ngầm còn tệ hơn là không có tính năng này.

`java.util.Random` được thay bằng bản tự viết đúng thuật toán mà tài liệu Java
quy định. Bản của máy chủ cho ra cùng dãy số nhưng giữ kín hạt giống, mà bản lưu
không đọc được hạt giống thì game có màn chơi sinh ngẫu nhiên sẽ chạy khác sau
khi nạp lại — đó là nói dối chứ không phải lưu.

Danh sách `Command` cũng được lưu. Chúng nằm trong context của máy chứ không
nằm trong đối tượng màn hình, và lần khôi phục chạy được đầu tiên đã chứng minh
vì sao điều đó quan trọng: trạng thái game trở lại chính xác, nhưng **thanh
phím mềm biến mất**, nên vùng vẽ cao hơn và **51.779 / 76.800 điểm ảnh lệch
nhau**. Lưu thêm danh sách lệnh: **0 điểm ảnh lệch**.

Trong ứng dụng, việc này không cần người chơi bấm gì: **thoát game là tự lưu**,
mở lại là chơi tiếp. Trang chi tiết có thẻ "Đang chơi dở" kèm **ảnh màn hình lúc
rời đi** — nhìn ảnh thì nhận ra mình đang ở đâu nhanh hơn nhìn ngày giờ nhiều —
và một dòng để bỏ bản lưu, chơi lại từ đầu.


## Giao diện sáng và tối

Mặc định nay là **sáng**. Giao diện tối trông đẹp trong ảnh chụp nhưng đọc mệt
mắt giữa ban ngày — mà điện thoại thì chủ yếu dùng ban ngày; ai thích tối chỉ
cần nói một lần.

- **Nút chuyển ngay trên trang chủ**, luôn ở cùng một góc: sáng → tối → theo hệ
  thống → sáng. Đây là cài đặt người ta đổi đủ thường xuyên để đáng nằm sẵn
  trên đường đi, thay vì nằm sâu ba màn hình.
- **Cài đặt** có lựa chọn ba mức rõ ràng cho ai muốn đặt một lần rồi thôi.
- Lựa chọn được lưu trong `AppSettings` — tách khỏi `GameProfile`, vì cài đặt
  của một game thì đi theo game khi sao lưu, còn cái này là của người dùng.
- **Thanh tiêu đề và phím mềm của máy giả lập cũng đổi theo.** Máy J2ME đời đó
  có cả hai kiểu nên không kiểu nào kém "thật" hơn; thứ trông sai là một dải
  tối dán lên đầu một màn hình sáng.
- Bảng màu sáng **không dùng trắng tinh**: nền trắng gắt cạnh màn hình game, và
  thẻ nào cũng phải viền đậm mới thấy. Màu nhấn cũng đậm hơn bản tối — cùng một
  màu xanh đặt trên nền trắng thì nhạt đến mức không đọc được.

Bộ test đo **tỉ lệ tương phản theo chuẩn WCAG** chứ không chỉ so mã màu: chữ
thường trên nền > 7:1, chữ mờ và màu nhấn > 3:1, nền sáng không phải trắng
tinh, và bar của máy giả lập đổi đúng theo chế độ.


## Tìm game: gõ không dấu vẫn ra

Người tìm "Người Chạy Trên Mây" sẽ gõ **"nguoi chay"** — dấu gõ chậm trên bàn
phím điện thoại, và quá nửa tên game trong kho vốn dĩ được đặt không dấu. Tìm
kiếm mà đòi đúng dấu thì ra rỗng và trông như hỏng.

`Text.searchKey` hạ chữ thường và bỏ toàn bộ dấu tiếng Việt (kể cả `đ` → `d`),
áp cho **cả hai phía**: từ khoá và tên game. `GameLibrary.search` còn tìm theo
**tên gốc** của game nữa — người dùng đổi tên rồi vẫn có thể nhớ tên cũ.

Việc lọc và sắp xếp nay nằm trong core cho cả hai nền tảng: iOS trước đây tự
lọc bằng `localizedCaseInsensitiveContains` nên hành xử khác Android. Nay cả
hai gọi `searchJson(query, sort)`, cùng một câu tìm cho cùng một danh sách.

Thứ tự sắp xếp cũng được nhớ giữa các lần mở app — ai sắp theo "vừa chơi" thì
lần nào cũng muốn thế.


## Gộp thư viện vào trang chủ

Tab "Thư viện" thực ra chỉ là một ô tìm kiếm đặt trên đúng những game mà trang
chủ đã liệt kê. Một tab lặp lại tab bên cạnh khiến người dùng phải nhìn hai chỗ
cho một việc.

Nay còn **ba tab**: Trang chủ, Công cụ, Cài đặt. Ô tìm kiếm nằm ngay dưới tiêu
đề trang chủ; **gõ vào là toàn bộ phần duyệt được thay bằng kết quả** — người
đang tìm thì đã thôi duyệt — kèm ba lựa chọn sắp xếp và số kết quả. Xoá chữ đi
là quay lại như cũ.

`LibraryScreen.kt` và `LibraryView.swift` bị xoá hẳn chứ không để lại làm màn
hình chết.


## Bàn phím: bốn nút xéo, chỉ còn số, và bàn phím máy

**Bốn nút xéo.** Cụm mũi tên nay đủ tám hướng. Bốn góc *không phải phím riêng*:
MIDP không có mã phím xéo và không máy J2ME nào có phím xéo — góc của cụm phím
ngày xưa là **hai hướng bấm cùng lúc**, và đó đúng là thứ nút góc gửi đi. Game
đọc `getKeyStates` hiểu ngay mà không cần biết gì thêm. Icon góc vẽ nhỏ hơn
bốn hướng chính: có mặt khi game cần, không tranh chỗ ngón tay.

**Bàn phím số chỉ còn số.** Mấy chữ "abc / def / ghi" in dưới phím là để gõ
đa chạm — cách duy nhất một bàn phím số ngày xưa nhập được chữ. Máy chạy app
này có bàn phím thật, nên ba hàng chú thích đó là hướng dẫn cho việc không ai
phải làm nữa.

**Bàn phím máy hiện khi game hỏi chữ.** `ScreenInput.textInputTarget` cho biết
game đang chờ nhập (một `TextBox`, hoặc một `TextField` đang được chọn); lúc đó
app dựng bàn phím hệ thống lên và **thay luôn bàn phím ảo** — vì trên máy thật,
bàn phím hệ thống che đúng nửa dưới màn hình đó. Không vẽ bàn phím giả: bàn
phím là của máy, vẽ lại là bịa ra một giao diện app không sở hữu.

Chữ đi vào theo **cả chuỗi** chứ không theo từng phím: bàn phím hệ thống tự lo
việc di con trỏ, sửa lỗi, dán — thứ game cần thấy là kết quả. Giới hạn của
`TextField` vẫn được áp: game xin 12 ký tự thì nhận đúng 12, và ràng buộc
`NUMERIC`/`PHONENUMBER`/`DECIMAL` vẫn lọc đúng ký tự cho phép.


## Nhập cả thư mục game một lần

Không ai có bộ sưu tập J2ME mà chỉ có một game. Họ có một thư mục tám mươi
game, thường là từng cặp `.jar` + `.jad`, và thường nằm trong một tệp zip ai đó
chia sẻ từ nhiều năm trước. Trước đây chọn hai mươi tệp thì app chỉ nhận **một**
— lấy tệp `.jar` đầu tiên rồi bỏ hết phần còn lại.

`BatchImport` nay nhận cả đống:

- **Ghép cặp theo tên tệp**: `SkyRunner.jad` đi với `SkyRunner.jar`, bất kể
  trình chọn tệp trả về theo thứ tự nào. Trên Android phải hỏi *display name*
  của URI mới ghép được — phần cuối của content URI thường chỉ là một mã.
- **Mở zip chứa game**: một JAR bản thân nó cũng là zip, nên phân biệt bằng
  ruột — có manifest thì là game, chứa các tệp `.jar` thì là bộ sưu tập. Không
  đệ quy tiếp vào zip trong zip: đó là việc của trình quản lý tệp.
- **Từng tệp có kết quả riêng**: đã cài / đã thay / lỗi / bỏ qua, kèm lý do và
  **tên tệp**. Một bản tải hỏng trong tám mươi tệp không được làm hỏng bảy mươi
  chín tệp còn lại, và người dùng phải biết tệp nào hỏng chứ không phải nhận
  câu "có lỗi xảy ra".
- Cuối cùng là một dòng tóm tắt: "Đã nhập 12 trò chơi, 1 tệp lỗi, bỏ qua 2".

## Phím mềm L/R và màn hình ngang

Hai thứ còn thiếu so với J2ME thật.

**L và R** là hai **phím mềm** trái/phải — đúng như bàn phím ảo của mọi trình
giả lập J2ME vẫn đặt tên. Mã nguồn J2ME Loader ghi thẳng:

```java
keypad[KEY_SOFT_LEFT]  = new VirtualKey(Canvas.KEY_SOFT_LEFT,  "L");
keypad[KEY_SOFT_RIGHT] = new VirtualKey(Canvas.KEY_SOFT_RIGHT, "R");
```

(phím bắn là "F", còn A/B/C/D mới là phím game). Nên ở đây **không thêm phím
mới**: hai phím mềm sẵn có được đánh dấu **L** và **R** ở góc, còn chữ ở giữa
vẫn là lệnh của game ("Tạm dừng", "Thoát") vì lệnh đó đổi theo từng màn hình.
Ai được bảo "bấm R" thì vẫn biết là phím nào, kể cả khi nó đang ghi "Thoát".

**Kích thước phím** cũng lấy theo J2ME Loader, không tự đặt:

```java
keySize = min(width, height) / 6.5f;   // cầm dọc
keySize = max(width, height) / 12f;    // cầm ngang
PHONE_KEY_SCALE_X = 2.0f;  PHONE_KEY_SCALE_Y = 0.75f;   // riêng phím mềm
```

- Phím **vuông**, cạnh tính theo màn hình chứ không phải con số cố định, nên
  máy to phím to, máy nhỏ phím nhỏ — ngón tay ở đâu cũng chạm trúng.
- Phím mềm rộng **2 ô**, cao **0,75 ô**: đọc như một thanh dẹt, không lẫn vào
  lưới phím.
- Phím giữa d-pad ghi **F** (fire) đúng như J2ME Loader, không phải "OK".
- Khi cầm ngang, cạnh phím còn bị ép thêm cho vừa chiều cao của cột: bên đó
  bàn phím có cột riêng chứ không nổi đè lên game như J2ME Loader.
- Bàn phím được xếp trước, game lấy phần còn lại: phím là thứ ngón tay phải
  bấm trúng, còn game nhỏ đi vài chục pixel thì không sao.

**Màn hình ngang** đi theo game chứ không theo người dùng:

- `AutoSetup` thấy game vẽ trên màn hình rộng (320×240 chẳng hạn) thì đặt luôn
  `orientation = ngang` và ghi lý do vào phần "đã tự cấu hình". Không ai phải đi
  tìm cái nút.
- Vẫn có nút **Ngang / Dọc** trên thanh trên cùng của màn chơi, cho những game
  vẽ nằm ngang trên màn hình dọc và bắt người chơi tự xoay máy. Lựa chọn được
  nhớ theo từng game (`toggleOrientation` ở facade).
- Bố cục khi nằm ngang: game giữ phần giữa — đó là thứ người chơi nhìn — mỗi
  bàn tay một cột: bàn phím con ở giữa, phím mềm (L bên trái, R bên phải) ở
  đáy, đúng chỗ ngón cái đang đặt. Xếp bàn phím xuống dưới một màn hình rộng thì game chỉ
  còn một dải mỏng trên đỉnh.
- Android xin xoay bằng `requestedOrientation`, iOS bằng
  `requestGeometryUpdate` — là **xin**, không phải ép: người chơi có thể đang
  khoá xoay màn hình, và một game từ chối chạy vì lý do đó thì tệ hơn nhiều.

Ảnh: `build/screenshots/18-landscape.png`.

## Menu trong game và bàn phím đổi kiểu

Xem J2ME Loader làm gì trong lúc chơi: nó có một menu ngay trên thanh công cụ,
gồm `action_take_screenshot`, `action_lock_orientation`, `action_ime_keyboard`,
`action_limit_fps`, `action_layout_switch`, `action_hide_buttons`,
`action_exit_midlet`. App này trước đó chỉ có nút "Thư viện" và "Tạm ngưng" —
mọi thứ khác phải thoát game mới chỉnh được.

Nay màn chơi có **Menu** với đúng những việc người ta cần *trong lúc* chơi:

- **Chụp màn hình** — game J2ME không có cách nào tự khoe nó vừa làm gì. Ảnh
  vào thư mục `screenshots/<game>/` của app, không đẩy vào thư viện ảnh của máy
  (đó là album của người ta, không phải của app).
- **Bàn phím** — đổi vòng: Đầy đủ → Chỉ phím hướng → Chỉ phím số → Ẩn bàn phím,
  đúng kiểu `layout_switch` + `hide_buttons` bên đó. Không phải thứ trang trí:
  game chỉ đọc phím hướng thì bỏ bàn phím số đi là game được thêm cả một vùng
  màn hình; game cảm ứng thì ẩn hẳn. Khi chỉ còn một bàn phím, nó nằm giữa chứ
  không để trống một bên.
- **Màn hình** — dọc/ngang (giữ từ phần trước).
- **Lưu trạng thái** — lưu ngay mà không cần thoát.
- **Thoát** — vẫn lưu trước khi ra, như cũ.

Bộ biểu tượng thêm `screen_rotation`, `speed`, `exit_to_app`. Để tô được
`speed`, `codegen/IconGen.java` học thêm lệnh cung `A`/`a` của SVG (chuyển từ
dạng "điểm cuối" sang tâm — theo phụ lục cài đặt của đặc tả SVG) — trước đó gặp
cung là ném lỗi, mà phần lớn biểu tượng Material có hình tròn đều dùng cung.

Ảnh: `build/screenshots/19-keypad-arrows.png`, `build/screenshots/20-game-menu.png`.

## Chạm thẳng vào thanh lệnh, bỏ hai phím trùng

Máy cảm ứng chạy game J2ME theo cách này: thanh lệnh ở đáy màn hình game **là**
nút — chạm vào chữ "Tạm dừng" là chạy lệnh đó. Trước đây app vẽ thanh đó nhưng
không nhận chạm, nên phải để thêm hai phím mềm bên dưới ghi đúng hai chữ ấy:
hai đường để làm một việc.

- `SystemChrome.softKeyHit` xác định chạm rơi vào nửa trái hay nửa phải của
  thanh; `EmulatorSession.pointerPressed` chạy lệnh tương ứng **trước** khi
  đưa chạm xuống cho game, đúng như máy thật.
- Bàn phím ảo chỉ hiện hai phím **L/R** khi màn hình **không** có thanh lệnh —
  tức là khi game chạy toàn màn hình (`setFullScreenMode(true)`), lúc đó phím
  mềm là đường duy nhất còn lại để gọi lệnh. J2ME Loader luôn hiện L/R vì bàn
  phím của nó nổi đè lên game và không biết game đang gán lệnh gì; ở đây thanh
  lệnh là của mình vẽ nên biết rõ.
- Bỏ được hàng phím đó, game cao thêm gần một trăm pixel, và khi cầm ngang thì
  hai bàn phím con nằm giữa cột thay vì treo trên đỉnh.

Ảnh: `build/screenshots/03-emulator.png`, `build/screenshots/18-landscape.png`.

## Phím hướng to hơn, trang chủ gọn lại

**Bàn phím hướng** trước đây thấp hơn hẳn bàn phím số bên cạnh (3 hàng so với
4), trông như thứ phụ — trong khi nó mới là thứ dùng để chơi, còn phím số chủ
yếu để gõ tên. Nay phím hướng giữ nguyên bề ngang (hai bàn phím vẫn vừa một
màn hình ở cỡ phím của J2ME Loader) và **cao thêm**: ba hàng của nó bằng đúng
bốn hàng phím số. Cỡ phím số **không đổi**.

**Trang chủ** làm lại theo J2ME Loader (`fragment_apps_list.xml`,
`menu/main.xml`, `list_row_jar.xml`):

- Thanh công cụ: tên app bên trái; bên phải là **tìm**, **sắp xếp**, và **⋮**.
  Bấm tìm thì ô nhập chiếm luôn thanh công cụ (bên đó dùng `SearchView`
  `showAsAction="ifRoom"`), chứ không để một ô tìm kiếm chiếm chỗ vĩnh viễn.
- Danh sách **phẳng**: biểu tượng 36dp, tên đậm, dưới là nhà phát hành và
  phiên bản — đúng bố cục hàng của họ. Bỏ thẻ (card) bao quanh mỗi game: tám
  mươi game là tám mươi khung phải nhìn xuyên qua.
- Bỏ luôn các mục "VỪA CHƠI / YÊU THÍCH / TẤT CẢ TRÒ CHƠI". Sắp xếp theo "Vừa
  chơi" đã làm được việc đó mà không tốn hàng nào; game yêu thích có dấu sao
  ngay trên hàng.
- **Bỏ thanh tab dưới đáy**. Công cụ và Cài đặt vào menu ⋮ — bên J2ME Loader
  chúng cũng nằm trong overflow. Chúng là hai trang cài đặt, không phải hai
  phần ba sản phẩm.
- Nút **+** tròn góc dưới phải, lề 16dp, y như `activity_main`.

Ảnh: `build/screenshots/05-library.png`, `build/screenshots/16-search.png`,
`build/screenshots/03-emulator.png`.

## Xem lại ảnh đã chụp

Chụp được ảnh mà không có chỗ nào xem lại thì nút chụp là ngõ cụt. Nay mỗi game
có trang **Ảnh chụp** riêng, vào từ trang chi tiết game:

- Lưới hai cột, ảnh **vừa khung chứ không cắt** — ảnh chụp bị cắt cho vừa ô
  vuông thì không còn là thứ đã hiện trên màn hình nữa.
- Chạm vào một ảnh thì hiện nút **xoá** ngay trên ảnh, không giấu sau thao tác
  nhấn giữ chẳng ai đoán ra.
- Ảnh vẫn nằm trong `screenshots/<game>/<thời-điểm>.png` thuộc thư mục riêng
  của app.

Ở lõi: `GameLibrary.readScreenshot` / `deleteScreenshot`, và facade có
`screenshotsJson`, `screenshot`, `deleteScreenshot`. Tên tệp đi qua cầu nối là
chuỗi từ bên ngoài nên bị cắt bỏ mọi phần đường dẫn trước khi dùng — `"../.."`
là cách một trình xem ảnh biến thành cách đọc trộm phần còn lại của bộ nhớ; có
test cho đúng trường hợp đó.

Ảnh: `build/screenshots/21-screenshots.png`.

## Bộ cấu hình: chỉnh một lần, áp cho cả bộ sưu tập

Người có tám mươi game và **một** cái điện thoại chỉ có một câu trả lời cho
"màn hình bao nhiêu, âm lượng bao nhiêu, mấy khung hình" — mà trước đây phải
trả lời tám mươi lần. Nay câu trả lời đó có tên: **bộ cấu hình** (J2ME Loader
gọi là *profiles*).

- Trong cài đặt của một game: gõ tên rồi **Lưu** — cấu hình hiện tại thành một
  bộ. Bộ nào cũng **Áp dụng** được cho game khác, hoặc **Xoá**.
- Trong Cài đặt chung: chọn **bộ mặc định** — mọi game nhập vào sau đó tự dùng
  bộ đó. Mặc định là "Không dùng": chưa ai quyết gì thì game vẫn được cấu hình
  từ chính nội dung của nó.
- Nhập cả thư mục thì bộ mặc định chỉ áp cho những game **chưa ai chỉnh tay** —
  một lần nhập không được xoá đi thiết lập người ta cố ý đổi cho game đã có.

Bộ cấu hình chỉ mang **thiết lập**, không mang **danh tính**: nó lưu máy giả
lập, bàn phím, phóng ảnh, âm thanh, mạng… và không bao giờ lưu `suiteId`, số
lần chơi, hay dấu yêu thích. Áp một bộ lên game khác không được biến game đó
thành game khác. Tên bộ do người dùng gõ nên bị lọc mọi ký tự đường dẫn trước
khi thành tên tệp.

Ở lõi: `core/library/PresetStore.java`, thư mục `presets/`, và facade có
`presetsJson`, `savePreset`, `applyPreset`, `deletePreset`, `setDefaultPreset`.

Ảnh: `build/screenshots/04-game-settings.png`.

## Bốn ô lưu của người chơi, một ô của máy

Trước đây mỗi game chỉ có **một** ô lưu, mà ô đó lại chính là ô trình giả lập
ghi đè mỗi lần thoát game. Nghĩa là lưu lại trước một đoạn khó rồi chơi tiếp,
đến lúc thoát là mất chỗ vừa lưu.

Nay mỗi game có **năm** ô:

- **Ô 0 — tự động**: máy ghi khi rời game, đúng như cũ, và chỉ mình nó bị ghi
  đè khi thoát.
- **Ô 1–4 — của người chơi**: lưu từ menu trong game (`Lưu vào ô N`), nạp lại
  bằng `Nạp ô N` ngay giữa ván mà không phải khởi động lại game.
- Mỗi ô giữ **ảnh màn hình lúc lưu** và **thời điểm lưu**: quay lại với bốn ô
  lưu thì nhìn ảnh biết ngay ô nào là ô nào, nhanh hơn đọc ngày nhiều.
- Trang **Ô lưu trạng thái** trong chi tiết game để xem và xoá từng ô.

Ở lõi: `StorageLayout.saveStatePath(suiteId, slot)` (ô 0 giữ nguyên tên tệp cũ
`state.mcs` nên bản lưu cũ vẫn dùng được), `GameLibrary` nhận thêm tham số ô, và
facade có `saveState(slot)`, `loadState(slot)`, `resumeGame(suiteId, slot)`,
`saveStatesJson`, `deleteSaveState(suiteId, slot)`. `Vfs` thêm `modifiedAt` —
bốn ô giống hệt nhau mà không có ngày giờ thì chỉ còn cách đoán.

Ảnh: `build/screenshots/22-save-slots.png`.

## Tua nhanh, chạy chậm

Game J2ME **tự đo nhịp**: nó đọc `System.currentTimeMillis` rồi `sleep` giữa
các khung hình. Cho nên muốn game chạy nhanh hơn thì không phải gọi nó nhiều
lần hơn — mà là **đổi cái đồng hồ nó nhìn vào**. Đưa cho nó đồng hồ chạy gấp
đôi thì mỗi giây thật nó đi xa gấp đôi, vẫn bằng chính logic và hoạt ảnh của
nó.

- `core/emu/SpeedClock.java` bọc `VmHost`: giờ trả về được nhân theo tốc độ, và
  `sleep` chia lại tương ứng — không chia thì riêng cái `sleep` đã giữ game ở
  nhịp cũ rồi.
- Đổi tốc độ giữa chừng thì **tính lại từ mốc hiện tại**, không nhân lại cả quá
  khứ: game chạy một tiếng rồi mà bấm "nhanh" không được nhảy vọt một tiếng.
  Đồng hồ cũng không bao giờ lùi — game thấy thời gian lùi sẽ tính ra khoảng
  cách khung hình âm, và đó là cách một nhân vật rơi vào chỗ không thể tới.
- Menu trong game có mục **Tốc độ**, bấm để đi vòng: 0,5× → 1× → 2× → 3×.
- Vòng lặp khung hình của Android và iOS cũng nhân theo tốc độ, nếu không thì
  vẽ ở nhịp cũ sẽ chỉ thấy một nửa những gì game làm.

Vì sao cần: mấy game này viết cho một chuyến xe buýt. Một màn đi bộ bốn phút
qua bản đồ trống thì bốn phút trên máy thật cũng là bốn phút bây giờ, mà người
chơi đã xem rồi. Chiều ngược lại cũng đáng: game canh nhịp cho máy chậm hơn
máy đang giả lập thì cho chậm lại là chơi được.

Ảnh: `build/screenshots/20-game-menu.png`.

## Liên thanh: máy bấm hộ

Hồ sơ điều khiển vốn đã có ô `turbo` từ lâu, lưu vào JSON đàng hoàng — nhưng
**chưa bao giờ được dùng**: một thiết lập ghi ra rồi không làm gì cả. Nay nó
chạy thật.

Vì sao cần: một nửa số game bắn thời đó viết cho ngón cái nện bàn phím — mỗi
lần bấm một phát đạn, không có tự động bắn, và có màn không mash thì không qua
nổi. Giữ phím **không phải** cùng một thao tác: game đọc `keyPressed` chỉ thấy
đúng một lần bấm dù giữ bao lâu. Nên liên thanh phải **nhả ra rồi bấm lại** —
đó chính là việc `EmulatorSession.pumpTurbo()` làm mỗi khung hình, theo đúng
khoảng thời gian đã đặt.

- Bật trong cài đặt game: **Tắt / Chậm (120ms) / Nhanh (50ms)**, cho phím Chọn
  (phím bắn). Phím hướng cố tình không có — d-pad mà tự nhả ra là d-pad giật
  cục.
- Đổi giữa ván thì game đang chạy nhận ngay: người bật liên thanh là đang bật
  cho trận họ đánh dở.
- Facade: `setTurbo(suiteId, button, intervalMs)`.

Ảnh: `build/screenshots/04-game-settings.png` (dòng "Chọn — liên thanh 50ms").

## Đổi gán phím: khi bộ mặc định đoán sai

Trước đây phần "GÁN PHÍM" chỉ **hiển thị** phím nào gửi mã nào; muốn đổi thì
chỉ có cách chọn cả một bộ khác (Nokia / Sony Ericsson / Samsung). Mà bộ nào
cũng chỉ là **phỏng đoán**: game viết cho một máy sẽ đọc đúng mã máy đó gửi —
rất nhiều game đọc `'2'` và `'8'` cho lên/xuống, hoặc mã riêng của hãng cho
phím bắn. Đoán sai thì game **không phản ứng gì cả**, nhìn như trình giả lập
hỏng chứ không phải như gán sai phím.

- Chạm vào từng dòng trong "GÁN PHÍM" để chọn mã phím khác.
- Danh sách chỉ gồm những mã một MIDlet thời đó có thể đọc: năm phím game, hai
  phím mềm, mười chữ số, `*` và `#`. Ô nhập số tự do sẽ cho phép gán vào mã mà
  không máy nào từng gửi — facade từ chối thẳng mã lạ.
- Đổi một phím thì hồ sơ **thôi tự nhận là "Nokia"** và ghi là "Tuỳ chỉnh":
  một bàn phím dán nhãn Nokia mà không phải Nokia còn tệ hơn.
- Chọn lại một bộ có sẵn là cách quay về khi lỡ chỉnh loạn.
- Đổi giữa ván thì game đang chạy nhận ngay.

Ở lõi: `InputProfile.setMapping` / `keyChoices()`, facade `setKeyMapping` và
`keyChoicesJson`.

Ảnh: `build/screenshots/04-game-settings.png` (dòng "Lên → 2").

## Sao lưu toàn bộ: đổi máy trong một tệp

Sao lưu từng game đã có từ lâu, nhưng sai hình dạng cho đúng việc người ta thật
sự làm: **đổi điện thoại**. Tám mươi game là tám mươi lần sao lưu, tám mươi lần
chuyển, tám mươi lần khôi phục — ai làm việc đó lúc mười một giờ đêm thì đến
game thứ sáu mươi là bỏ.

`core/library/LibraryArchive.java` gói **một tệp** gồm mọi thứ thuộc về người
dùng: bản cài game, tên họ đặt, ảnh bìa họ chọn, mọi cấu hình, dữ liệu game tự
lưu (RMS), các ô lưu trạng thái, ảnh chụp, bộ cấu hình, và cả cài đặt của app.
Không gói cái có thể tạo lại: `cache/` và `backups/` — sao lưu của sao lưu chỉ
làm tệp to gấp đôi mà không thêm gì.

- Khôi phục thì **ghi đè, không xoá trước**: người khôi phục lên máy đã có game
  là muốn lấy lại game cũ, chứ không phải muốn game mới biến mất.
- Đường dẫn trong tệp là dữ liệu từ bên ngoài nên bị kiểm tra: `../` hay đường
  dẫn tuyệt đối bị bỏ qua, không cho ghi ra ngoài thư mục của app.
- Định dạng dùng đúng khung chứa đơn giản của bản sao lưu từng game, không dùng
  zip: nó phải đọc được bằng **cùng một đoạn mã** trên hai nền tảng, mà một
  định dạng chỉ có một bản cài đặt thì không thể tự mâu thuẫn với chính nó.

Trong Cài đặt: **Xuất tệp** / **Khôi phục** (Android dùng SAF, iOS dùng
`fileExporter`/`fileImporter`).

Bài kiểm tra chuyển hẳn một thư viện sang "máy thứ hai" rỗng rồi kiểm từng thứ
ở đầu bên kia — tên game, màn hình đã đặt, ô lưu, ảnh chụp, bộ cấu hình, giao
diện — và cuối cùng mở game lên chơi tiếp từ ô lưu.

## Tua lại: lấy lại vài giây vừa rồi

Game thời đó khó theo kiểu **công bằng trên xe buýt nhưng không công bằng bây
giờ**: sai một nhịp là chơi lại cả màn, vì máy thật không có chỗ nào để giữ gì
khác. Máy bây giờ thì có.

`core/emu/Rewind.java` giữ **một ảnh chụp trạng thái mỗi giây, sâu mười hai
giây**. Menu trong game có **Tua lại 1 giây**; mỗi lần bấm lùi thêm một giây,
nên bấm liên tục là đi ngược qua chỗ vừa hỏng.

Vì sao nông và thưa như vậy: mỗi ảnh chụp là **toàn bộ heap** — đúng thứ mà ô
lưu trạng thái ghi — nên chụp mỗi khung hình thì tốn thời gian lưu nhiều hơn
chạy game, còn giữ cả tiếng đồng hồ thì tốn bộ nhớ hơn chính cái game. Một giây
một ảnh, mười hai ảnh, tốn vài megabyte và phủ đúng cái lỗi vừa mắc — cũng là
cái lỗi duy nhất người ta muốn lấy lại.

Vài chi tiết:
- Lịch sử tính theo **đồng hồ của game**, nên chạy 2× thì mười hai giây lịch sử
  vẫn là mười hai giây *chơi*.
- Lùi xong thì **bỏ luôn ảnh đó**: không bỏ thì bấm tiếp lại rơi về đúng chỗ
  cũ, không đi ngược được.
- Game không chụp được (đang mở kết nối chẳng hạn) thì đơn giản là không có
  lịch sử, chứ không ngắt ván chơi để báo — người chơi biết khi họ thử tua,
  đúng lúc điều đó mới có nghĩa với họ.
- Tắt là **bỏ luôn phần đã giữ**: để lại vài megabyte sau khi người ta bảo
  không dùng thì đúng ngược ý họ.

Ảnh: `build/screenshots/20-game-menu.png`.

## Một gói, nhiều ứng dụng

Một tệp `.jar` thường chứa **nhiều MIDlet**: game, màn hình trợ giúp, màn hình
cài đặt, đôi khi cả một game thứ hai. Trước đây chỉ chạy được cái đầu tiên
trong manifest — phần còn lại của gói coi như không tồn tại.

- Trang chi tiết game hiện mục **TRONG GÓI NÀY** liệt kê mọi MIDlet, chạm để
  chọn cái sẽ mở. Chỉ hiện khi gói thật sự có nhiều hơn một: một danh sách chọn
  chỉ có một lựa chọn là một câu hỏi chỉ có một đáp án.
- Lựa chọn được **nhớ theo game** (`GameProfile.midletClass`), nên nút Chơi mở
  đúng cái người ta coi là "game", không phải cái manifest tình cờ xếp trước.
- Tên lớp lưu lại mà **không còn trong gói** (cài lại từ bản build khác) thì
  quay về MIDlet đầu tiên và quên tên cũ đi — chứ không để game thành không mở
  được vì một cái tên cũ.

Facade: `midletsJson(suiteId)` và `startGame(suiteId, midletClass)`.

Ảnh: `build/screenshots/06-game-detail.png`.

## Đã chơi bao lâu

Thư viện vốn biết game được mở **lúc nào** — đủ để trả lời "hôm qua mình chơi
gì" và không gì hơn. Cái đáng biết về một bộ sưu tập tám mươi game là game nào
**giữ chân người ta**: nó tách bốn game thật sự được chơi khỏi bảy mươi sáu
game mở đúng một lần.

- `GameProfile.playedMs` cộng dồn sau mỗi lần thoát game; trang chi tiết hiện
  dòng **Đã chơi** ("3 giờ 12 phút", "12 phút", "dưới một phút", "chưa chơi" —
  không bao giờ là một con số trần).
- Thêm kiểu sắp xếp **Chơi lâu nhất** trong menu sắp xếp ở trang chủ.
- Đo bằng **đồng hồ thật**, không phải đồng hồ của game: chạy 3× thì người chơi
  vẫn bỏ ra đúng ngần ấy phút, một con số co lại vì ai đó tua nhanh là đang đo
  nhầm thứ.
- Lỗi khi ghi hồ sơ lúc thoát thì bỏ qua: một tổng thiếu mất một phiên còn hơn
  là sập khi rời game.

Ảnh: `build/screenshots/06-game-detail.png`.

## Nhạc MIDI: phần còn thiếu của âm thanh

Nhạc trong game J2ME gần như luôn là một tệp `.mid` — đó là định dạng duy nhất
vừa dung lượng, và máy thật có sẵn bộ tổng hợp trong phần cứng. Không có bộ
tổng hợp thì **mọi game có nhạc đều im tiếng**, mà tiếng nhạc là một nửa những
gì người ta còn nhớ về mấy game này.

`core/audio/MidiDecoder.java` là một trình đọc tệp cộng một bộ tổng hợp rất
nhỏ: đọc header, gộp các track thành một dòng thời gian note-on/note-off, bám
theo mọi lệnh đổi nhịp (tempo), rồi phát mỗi nốt bằng sóng vuông đúng bằng
khoảng thời gian nó được giữ.

Những chỗ cố tình không làm, và lý do:

- **Không có bộ nhạc cụ (soundbank)**: máy thật có chip với đủ nhạc cụ; ở đây
  piano và sáo cùng một dạng sóng. Đó là đánh đổi trung thực — **giai điệu,
  nhịp và hoà âm là của game**, còn âm sắc là của trình giả lập.
- **Kênh 10 (trống) để im**: kênh này chở *số hiệu trống* chứ không phải cao
  độ; phát nó như cao độ là thêm vào bản nhạc những nốt chưa từng có ở đó —
  đúng nghĩa đen là tiếng ồn chồng lên giai điệu.
- **MIDI theo SMPTE bị từ chối** thay vì đoán: không game J2ME nào dùng, mà
  đoán sai thì phát sai tốc độ chứ không phải không phát.
- **Cắt ngọn khi trộn** chứ không hạ âm lượng cả bài: một hợp âm to không được
  làm cả bản nhạc nhỏ đi.
- Giới hạn 180 giây cho một bản, để một tệp dài không ăn hết bộ nhớ.

MP3 vẫn từ chối một cách trung thực như cũ. Fixture `SoundDemo` nay phát một
tệp MIDI thật (do chính nó dựng ra) và báo "MIDI: phát được".

Ảnh: `build/screenshots/13-sound.png`.

## API Nokia: thứ phần lớn game này được viết cho

Nokia bán phần lớn máy thời đó, nên phần lớn game viết theo API riêng của
Nokia. Và game kế thừa `com.nokia.mid.ui.FullCanvas` thì **không phải chạy dở
— mà không nạp nổi**: trình nạp lớp chết ở lớp cha, trước cả khung hình đầu
tiên. Đó là kiểu hỏng tệ nhất để đưa cho người dùng xem, vì trên màn hình
không có gì giải thích cả.

`core/midp/NokiaUi.java` hiện thực đúng phần các game đó dùng:

- **`FullCanvas`** — Canvas chiếm trọn màn hình ngay khi được tạo, và **từ chối
  nhận lệnh** đúng như bản thật (game có bắt lỗi đó). Kèm bộ mã phím riêng
  (`KEY_SOFTKEY1` = -6, …).
- **`DirectGraphics`** — những thao tác MIDP không có: đổ mảng pixel vào, đọc
  pixel ra, đa giác đặc, tam giác, và **vẽ ảnh xoay/lật**. Nó là *góc nhìn thứ
  hai của cùng một bề mặt* mà `Graphics` đang vẽ, không phải bề mặt thứ hai.
- **`DirectUtils`** — `getDirectGraphics`, `createImage(w, h, argb)`.
- **`DeviceControl`** — đèn nền và rung. Máy tính để bàn không rung được, nên
  các hàm này nhận rồi bỏ qua; `flashLights` trả về `false` — trả lời trung
  thực là "đèn không nháy", đúng cách bản thật báo.

Chi tiết đáng nói: đa giác đặc được lấp bằng cách quạt tam giác từ đỉnh đầu,
và **tắt khử răng cưa trong lúc lấp** — hai cạnh khử răng cưa chồng lên nhau ở
mép chung sẽ để lại một đường chỉ chạy giữa hình đặc. Khử răng cưa là để làm
mượt *đường bao*, không phải mép trong.

Phân loại tương thích đổi theo: game dùng `com.nokia.mid.ui` từ **"chưa chạy
được"** thành **"chạy được, thiếu vài thứ"** — vì các gói Nokia khác vẫn chưa
có.

Fixture mới `demo/NokiaDemo` kế thừa `FullCanvas` và vẽ hoàn toàn bằng
`DirectGraphics`, chạy thật bằng bytecode trong bộ kiểm thử.

Ảnh: `build/screenshots/23-nokia.png`.

## Rung: phản hồi vật lý duy nhất mà game J2ME có

`Display.vibrate` và `DeviceControl.startVibra` trước đây đều trả lời "không
rung" và không làm gì. Mà cái rung khi đâm xe hay khi trúng đòn **là một phần
của game** — máy thời đó không có gì khác để phản hồi bằng xúc giác.

- `core/haptics/VibrationSink` là chỗ yêu cầu rung đi ra ngoài, giống hệt cách
  `AudioSink` làm với âm thanh: lõi quyết định *khi nào* và *bao lâu*, không
  bao giờ chạm vào mô-tơ. Mặc định là `VibrationLog` — ghi lại thay vì rung,
  cho bản xem trước và bộ kiểm thử.
- Android nối vào `Vibrator` / `VibratorManager`; iOS nối vào haptics.
- **Trả lời trung thực**: `Display.vibrate` theo đặc tả phải cho biết máy có
  rung thật hay không — game bị trả lời "không" có thể vẽ hiệu ứng khác thay
  thế. Nói "có" rồi không làm gì là tước mất lựa chọn đó của game.
- Giới hạn 5 giây một lần: máy thật không rung cả phút chỉ vì game bảo thế.
- Công tắc của người chơi: **Rung** trong cài đặt từng game, mặc định bật. Tắt
  là lựa chọn thật — game rung mỗi lần trúng đòn thì không chơi cạnh người khác
  được.

iOS không có khái niệm "rung 200 mili giây", chỉ có mẫu haptic và một tiếng
rung hệ thống, nên yêu cầu dài hơn được đổi thành **mạnh hơn** chứ không phải
lâu hơn — gần nhất mà nền tảng cho phép, và gần hơn là không có gì.

Ảnh: `build/screenshots/04-game-settings.png` (dòng "Rung").

## Ba hãng còn lại

Nokia không phải hãng duy nhất có lớp riêng, và game viết cho Siemens hay
Samsung hỏng đúng kiểu game Nokia đã hỏng: trình nạp lớp bỏ cuộc trước khi vẽ
được gì. Thứ mấy game đó gọi đến thì nhỏ và lặp đi lặp lại — rung máy, bật đèn
phím, kêu một tiếng — nên lớp cũng nhỏ theo.

`core/midp/VendorApis.java`:

- **Siemens**: `game.Vibrator` (đơn vị là **phần mười giây**, chỗ dễ sai nhất),
  `game.Light`, `game.Sound.playTone` và `game.ExtendedImage` (ảnh vẽ vào rồi
  blit ra — thứ Siemens đưa thay cho Image ghi được).
- **Samsung**: `util.Vibration` (mili giây rồi cường độ) và `util.AudioClip`.
- **Motorola**: `multimedia.Vibrator`.

Mỗi lớp nối vào thứ bộ giả lập **đã làm thật**: rung đi cùng đường với
`Display.vibrate`, tiếng đi qua đúng bộ tổng hợp mà `Manager.playTone` dùng,
còn đèn phím thì nhận rồi bỏ qua vì không có cách nào trung thực để làm.

Một chỗ dễ sai đã xử lý: **Siemens nói tần số Hz, MIDP nói số hiệu nốt**. Truyền
thẳng thì 440 Hz thành nốt 127 — đỉnh thang âm, cho mọi tiếng game phát ra. Nay
đổi sang nốt gần nhất (440 Hz → nốt 69, đúng nốt La chuẩn).

Vẫn **cố tình thiếu**: `com.siemens.mp.color_game` — đó là cả một thư viện chứ
không phải vài hàm tĩnh, và một lớp giả vờ là nó sẽ hỏng muộn hơn và khó hiểu
hơn là không có.

Fixture `NokiaDemo` nay gọi cả năm đường yêu cầu rung của bốn hãng; bài kiểm tra
đếm đúng 660ms tổng cộng, mỗi hãng theo đơn vị của mình.


## Giai đoạn 32 — một loại màn hình duy nhất

Trước đây màn hình cài đặt bày ra bảy cỡ máy: 128×128, 128×160, 176×208,
176×220, 240×320, 320×240 và 240×400 cảm ứng. Nghe thì rộng rãi, nhưng nó là
một câu hỏi người chơi **không có cách nào trả lời đúng**: muốn chọn được thì
phải biết game gốc viết cho máy nào, mà nếu biết thì đã không cần hỏi. Chọn sai
một lần là game chạy trên màn hình nó chưa bao giờ được vẽ cho — chữ tràn, nút
lệch, và chẳng có gì chỉ ra nguyên nhân.

Nay chỉ còn **240×320** — cỡ QVGA, cỡ phổ biến nhất của thời J2ME và cỡ mà gần
như mọi game đời đó chạy được. Thứ duy nhất còn tự đoán là **chiều màn hình**:
game nào khai báo `Nokia-MIDlet-Original-Display-Size` rộng hơn cao thì mở ra
đã xoay ngang sẵn, còn lại mở dọc.

Những gì đã bỏ đi cùng với nó:

- `DeviceProfile.catalog()` còn đúng một mục; `byId` trả về màn hình ngang chỉ
  khi hỏi đúng id của nó, mọi id lạ đều rơi về màn hình dọc.
- `AutoSetup` **không còn đo ảnh trong JAR** để đoán cỡ máy. Đo ảnh lớn nhất là
  một phỏng đoán: ảnh nền 176×208 trong một game vẽ 240×320 là chuyện thường,
  và nó đã từng đẩy game xuống màn hình nhỏ hơn game cần.
- Cầu nối bỏ `setDeviceProfile` và bỏ luôn danh sách `devices` gửi kèm
  `profileJson`; giao diện Android, iOS và bản xem trước bỏ hàng chip chọn máy,
  thay bằng hai dòng nói thẳng màn hình đang dùng là cỡ nào, bàn phím kiểu gì.

Cài đặt mất một lựa chọn, nhưng là lựa chọn mà mọi câu trả lời trừ một đều làm
game chạy tệ hơn.


## Giai đoạn 33 — chỉnh bàn phím ảo

Bàn phím ảo trước giờ chỉ có một kiểu: phím bo góc, đặc, luôn nằm đó. Dựng
dọc thì không sao — bàn phím nằm dưới màn game. Nhưng **xoay ngang thì bàn phím
nằm đè lên chính game**, và một bàn phím đặc kín là khác biệt giữa chơi cả màn
hình game với chơi phần game mà bàn phím chừa lại.

J2ME Loader cho chỉnh những thứ này trong màn cấu hình từng game
(`PREF_VK_ALPHA`, `pref_button_shape_title`, `PREF_VK_HIDE_DELAY`), nên đây là
ba thứ đó, gọi bằng tiếng Việt:

- **Độ rõ** — 20% đến 100%. Dưới một phần năm thì không còn tìm ra phím nữa, nên
  đó là chỗ dừng chứ không phải một mức để chọn.
- **Hình phím** — Bo góc, Vuông, Tròn. Không phải trang trí: mép phím tròn và
  mép phím vuông cho ngón cái hai cảm giác nhắm khác nhau, và cái nào hợp thì
  tùy bàn tay đang cầm máy.
- **Tự mờ khi không dùng** — Luôn rõ, 5, 10 hoặc 30 giây.

**Mờ đi chứ không biến mất.** Bàn phím biến hẳn thì để người chơi quờ tay trên
một khoảng màn hình trống không có gì để tìm; nên nó tụt xuống còn một phần ba
độ rõ — tránh đường cho game, vẫn nằm đúng chỗ ngón cái để lại, và chạm vào là
rõ lại ngay.

Chỗ dễ sai đã xử lý: **không thể làm mờ từng màu một khi vẽ.** Viền phím bo
tròn được vẽ bằng hàng trăm điểm chồng lên nhau, và hàng trăm điểm mờ đè lên
một điểm ảnh thì ra một điểm **đặc**. Kết quả là ruột phím mờ đi còn viền vẫn
nguyên. Nên cả bàn phím được vẽ lên một lớp riêng rồi mới hạ độ rõ của cả lớp —
`Modifier.alpha` bên Android, `.opacity` bên iOS, và một `Framebuffer` trong
suốt ở bản xem trước. Ba nơi, cùng một cách.

Một câu trả lời, một chỗ: `GameProfile.keypadOpacityAfter(idleMillis)` quyết
định vẽ đậm bao nhiêu, `EmulatorSession` giữ đồng hồ và trả lời
`keypadOpacity()`. Điện thoại và bản xem trước không thể vẽ cùng một bàn phím
theo hai kiểu khác nhau.

Cầu nối: `keypadJson`, `setKeypadOpacity`, `cycleKeypadShape`, `setKeypadShape`,
`setKeypadFadeDelay`, `keypadDrawOpacity` (một con số, không phải JSON — hỏi
mỗi khung hình thì không phải chỗ để phân tích văn bản) và `noteKeypadUse`.


## Giai đoạn 34 — quay màn chơi thành ảnh động

Ảnh chụp nói được người chơi **đến đâu**, không nói được **bằng cách nào**: cú
nhảy vừa ăn, đường đạn của con trùm, con bọ đáng báo lại. Vài giây game thật thì
nói được, và **GIF chạy được ở mọi nơi ảnh chạy được** — trong tin nhắn, trong
bài đăng, trong thư viện ảnh của máy. Không ai phải cài thêm gì để xem.

Bộ mã hoá tự viết, vì bộ giả lập **không có nền tảng nào để nhờ**: cùng một mã
chạy trên JVM của Android và, qua J2ObjC, trên iOS. Nghĩa là phải tự viết bộ
giảm màu và bộ nén LZW — đó là toàn bộ `core/gfx/GifEncoder.java`.

- **Bảng màu**: GIF chứa 256 màu, và game thời này hiếm khi dùng hơn — máy J2ME
  có màn 12 hoặc 16 bit và hoạ sĩ vẽ cho đúng cái đó. Nếu cả đoạn quay có từ 256
  màu trở xuống thì bảng màu **chính là những màu đó**, và ảnh trả về **đúng
  từng điểm ảnh**. Quá 256 mới đến lượt median cut: cắt hộp màu theo cạnh dài
  nhất, cắt mãi đến khi đủ 256 hộp. Cắt theo cạnh rộng nhất là thứ giữ cho vài
  màu sáng hiếm hoi — thanh máu, vụ nổ — không bị trung bình hoá vào nền.
- **LZW**: đúng bộ nén GIF quy định, mã rộng dần, đổ đi khi từ điển đầy 4096.
- **Lặp mãi**: khối NETSCAPE2.0, vì một đoạn chơi dài vài giây được xem bằng
  cách xem đi xem lại.

**Giới hạn cố ý: 10 giây, 10 hình/giây.** Khung hình phải giữ trong bộ nhớ đến
khi đoạn quay xong, vì bảng màu chọn từ cả đoạn cùng lúc; ở 240×320 mỗi khung là
300 KB, nên 10 giây là khoảng 30 MB và dài hơn nữa là một cách làm hết bộ nhớ
điện thoại bằng cách giữ một nút. 10 hình/giây cũng là mức trình xem GIF chịu
nghe: định dạng đếm thời gian theo phần trăm giây và phần lớn trình xem lặng lẽ
từ chối nhanh hơn 50 hình/giây.

Khung hình lấy theo **đồng hồ của game**, nên game chạy chậm lại thì đoạn quay
cũng chậm lại, chứ không phải bị bỏ cách khung. Và lấy từ **khung vừa vẽ xong**
chứ không phải khung sắp vẽ — nhờ vậy game đang không vẽ lại (menu, tạm dừng,
màn thua) vẫn quay được thời gian đang trôi thay vì quay ra không có gì.

Kiểm tra bằng cách **đọc ngược lại**: `tests/.../GifTest.java` có một bộ giải mã
GIF đầy đủ, và mọi khung hình bộ mã hoá tạo ra đều được giải mã rồi so từng điểm
ảnh. Một bài kiểm tra chỉ nhìn phần đầu tệp sẽ vẫn xanh trên một tệp không trình
xem nào mở được.

Đoạn quay nằm **chung thư mục với ảnh chụp**: với người chơi, đoạn quay là một
tấm ảnh biết chạy, và tách làm hai thư viện nghĩa là phải chọn mở cái nào trước
khi kịp nhớ mình đã lưu kiểu gì. Trong thư viện, đoạn quay có nhãn riêng và
phần đầu ghi rõ "3 ảnh, 1 đoạn quay".


## Giai đoạn 35 — tự sắp xếp bàn phím ảo

Bàn phím được xếp đúng như máy J2ME ngày xưa, vì đó là thứ ngón tay của người
từng chơi mấy game này đã quen. Nhưng **không bàn tay nào giống bàn tay nào**,
và điện thoại bây giờ to hơn máy hồi đó nhiều: phím bắn nằm ngay dưới ngón cái
người này là một cú với của người kia. J2ME Loader cho kéo phím đúng vì lý do
đó, và đây là cái đó.

`core/model/KeypadArrangement.java` giữ vị trí dưới dạng **độ lệch so với chỗ
bố cục chuẩn đặt phím**, tính bằng **đơn vị một phím**, chứ không phải toạ độ
tuyệt đối. Ba thứ theo sau và cả ba đều quan trọng:

- Bố cục chuẩn vẫn là bố cục chuẩn — không phải đo lại gì khi nó đổi.
- Cùng một cách sắp xếp dùng được cả **dọc lẫn ngang**, nơi bàn phím có hình
  dạng và kích thước khác hẳn, và trên mọi cỡ màn hình.
- "Đặt lại" là các độ lệch trở về 0, chứ không phải dựng lại bố cục từ một
  phỏng đoán.

Kéo ra khỏi màn hình thì **chặn lại chứ không từ chối**: một cú kéo quá tay nên
để phím nằm ở mép, chứ không phải để nguyên chỗ cũ rồi trông như hỏng. Tối đa 6
phím mỗi chiều.

Kèm theo là **cỡ phím** 60–160%. Nhưng **màn hình có tiếng nói cuối cùng**: cỡ
phím làm hai bàn phím tràn ra ngoài hai mép thì không được chiều theo đúng như
xin — bàn phím sẽ không bấm được và chẳng có gì chỉ ra là do cài đặt cỡ phím —
nên nó bị ghìm lại vừa đủ lọt.

Màn sắp xếp lấy **chính bàn phím thật, cỡ thật, cách sắp thật**: sắp phím trên
một tấm hình bàn phím là sắp ở chỗ khác với chỗ nó được dùng. Trong lúc sắp,
chạm vào phím là **kéo chứ không phải bấm** — một phím không thể vừa là thứ đang
được di chuyển vừa là thứ đang được chơi. Phím nào đã dời có viền sáng quanh, để
trả lời câu hỏi duy nhất màn này đặt ra: mình đã dời cái nào rồi?

Không có ô nào để gõ toạ độ, vì **không ai biết một phím nên nằm đâu cho tới khi
ngón tay đặt lên nó**.

Cầu nối: `keypadArrangementJson`, `moveKey` (độ lệch tính bằng **phần nghìn của
một phím**, không phải điểm ảnh — một phím là số điểm ảnh khác nhau khi dọc, khi
ngang và trên từng máy), `setKeyScale`, `resetKeypad`. Sửa xong là game đang
chạy nhận ngay, vì phím được kéo trong lúc đang nhìn chính game đó.


## Giai đoạn 36 — tay cầm và bàn phím ngoài

Chơi trên mặt kính là thứ **duy nhất bộ giả lập không sửa được**: không có gờ
phím để rà ngón, nên người chơi phải nhìn xuống thay vì nhìn game. Tay cầm trả
lại chỗ gờ đó, và phần lớn người còn chơi mấy game này đều có sẵn một cái — tay
cầm kẹp điện thoại, tay cầm máy chơi game nối Bluetooth, hay bàn phím rời trên
máy tính bảng.

**Tên nút là tên của bộ giả lập, không phải của nền tảng nào.** Android gọi một
nút mặt là `KEYCODE_BUTTON_A`, iOS gọi là `buttonA`, bàn phím thì gọi là
`Space`; mỗi bên tự dịch sự kiện của mình thành một trong 14 cái tên
(`padUp`…`padSelect`), và **mọi thứ sau đó xảy ra đúng một lần**, trong
`core/model/GamepadProfile.java`, cho cả hai nền.

Cách gán mặc định theo đúng cái game J2ME cần:

- **D-pad và cần analog** đều lái bốn hướng — game thời này có bốn hướng và
  không có gì khác, nên cần analog được đọc như d-pad chứ không phải như trục.
- **A là Bắn**, vì đó là nút nằm sẵn dưới ngón cái.
- **B là phím 5**, hàng xóm của phím bắn trên bàn phím máy J2ME — rất nhiều game
  đặt hành động thứ hai ở đó.
- **L1/R1 và Start/Select là hai phím mềm**, chỗ người chơi vốn đã với tới để
  tìm "menu" và "quay lại".
- **L2/R2 để trống**: game J2ME không có việc gì cho chúng, và một nút không làm
  gì thì tốt hơn một nút làm điều bất ngờ.

Số thì vẫn ở bàn phím cảm ứng: tay cầm không có chỗ cho mười hai phím số, và
game nào cần số thì cần số **có nhãn**.

Gán lại được từng nút, và **tắt hẳn tay cầm** được. Tắt nghĩa là tắt ở đúng lúc
bấm nút — nhưng màn cài đặt vẫn hiện đầy đủ nút nào đang gán vào đâu, vì tắt tay
cầm không phải là gỡ gán từng nút một.

Hai chỗ dễ sai đã xử lý:

- **Cần analog gửi vị trí, không gửi lượt bấm.** Nếu cứ thấy vị trí là bấm thì
  game đọc phím-đang-giữ sẽ thấy một lần bấm rồi thôi. Nên phần đổi trạng thái
  được tính ra: hướng nào mới đẩy thì bấm, hướng nào thôi đẩy thì nhả. Vùng chết
  để rộng 0,5 — cần mòn nằm nghỉ ở 0,2 thì sẽ đẩy nhân vật vào tường suốt buổi.
- **Nút giữ thì tự lặp.** Android gửi lại sự kiện `ACTION_DOWN` liên tục khi
  giữ; game đã biết là đang giữ, nên các lần lặp bị bỏ qua thay vì thành một
  tràng bấm.

Bên Android sự kiện tay cầm đến **Activity** chứ không đến ô nào trên màn hình —
tay cầm không biết gì về tiêu điểm — nên `dispatchKeyEvent` gửi chúng cho game
đang chạy, và khi không có game thì trả lại cho Android để d-pad vẫn đi được
trong danh sách game. Bên iOS thì tay cầm đến qua thông báo của `GameController`,
nên máy nghe khi vào màn chơi.

Cầu nối: `gamepadJson`, `setPadMapping`, `setGamepadEnabled`, `resetGamepad`,
`pressPad`, `releasePad`. `pressPad` trả về **có bấm được hay không** chứ không
phải "lệnh đã tới": một nút chưa gán gì không phải là lỗi, và báo "đã bấm" cho
nó là báo một cú bấm game không hề thấy.


## Giai đoạn 37 — tệp riêng của game (JSR-75)

Kho bản ghi (RMS) là chỗ lưu **duy nhất** mà bản thân MIDP có, và nó được nghĩ
ra cho vài trăm byte một lần — một điểm cao, một mẩu cài đặt. Game có trình sửa
màn chơi, có nhạc tải về, có ảnh chụp thì dùng **tệp**, và trên máy J2ME nghĩa
là JSR-75. Thiếu nó, mấy game đó **ném lỗi ngay lần lưu đầu tiên** và người chơi
không làm gì được.

Nay `javax.microedition.io.file.FileConnection` chạy thật:
`create`, `mkdir`, `delete`, `truncate`, `rename`, `list` (kèm bộ lọc `*` của
JSR-75), `openInputStream`/`openOutputStream` (có cả dạng ghi nối từ một vị
trí), `fileSize`, `lastModified`, `setFileConnection` để đi trong cây thư mục,
và `FileSystemRegistry.listRoots`. Game xin qua **đúng cái cửa cũ**:
`Connector.open("file:///...")`.

**Tất cả bị nhốt trong thư mục riêng của game.** Game J2ME xin máy
`file:///c:/` hoặc `file:///root1/` là được cả thẻ nhớ; trên điện thoại bây giờ
đó là **ảnh của chủ máy**. Nên:

- Mọi đường dẫn được giải về **một thư mục thuộc riêng game này**, bất kể game
  xin gốc nào. `c:/`, `root1/`, `Memory card/` đều rơi vào cùng một chỗ — game
  vốn chỉ đang gõ cứng tên bộ nhớ của cái máy nó nhắm tới.
- Đoạn đầu **không phải** tên bộ nhớ thì vẫn là một phần đường dẫn:
  `file:///levels/1.dat` là tệp tên `levels/1.dat`, ăn mất thư mục đầu sẽ khiến
  game không tìm lại được tệp của chính nó.
- Đường dẫn leo ra ngoài bằng `..` bị **từ chối chứ không cắt bớt**: game định
  leo ra thì phải hỏng to, chứ không phải im lặng ghi sang chỗ khác. Kiểm tra
  trên **các đoạn đã giải**, không phải trên chuỗi ký tự.
- Mở ở chế độ `READ` thì **không ghi được**, kể cả xoá.

**Cố tình thiếu**: `setHidden`, `setReadable` và mấy hàm quyền còn lại. Trong
một thư mục mà game làm chủ thì không có quyền nào cả, và một hàm giả vờ đặt
quyền là một lời nói dối game có thể đọc lại sau đó.

Kiểm tra bằng **bytecode thật**: `fixtures/src/demo/FileDemo.java` là một MIDlet
được biên dịch thật, chạy bằng chính bộ thông dịch — nó tạo thư mục, ghi một màn
chơi, đọc lại, ghi nối, liệt kê thư mục, thử ghi khi chỉ có quyền đọc, rồi thử
leo ra ngoài. Bài kiểm tra đọc **những gì MIDlet để lại trong các trường của
chính nó**: một hàm trả về mà không làm gì sẽ qua được bài kiểm tra chỉ xem có
ném lỗi hay không.

Tệp của game **là của người chơi**, nên chúng hiện ra chứ không giấu trong ứng
dụng: thẻ "TỆP CỦA GAME" trong màn dữ liệu lưu liệt kê từng tệp kèm dung lượng
và cho xoá. Tên tệp đi từ ngoài vào qua cầu nối cũng bị nhốt **đúng như** đường
dẫn của chính game — `deleteGameFile(suiteId, "../../library.json")` không xoá
được gì.

Và vì JSR-75 nay chạy thật, nó **rời khỏi danh sách "gói còn thiếu"** trong bảng
kiểm tra tương thích trước khi chơi.


## Giai đoạn 38 — cài game từ liên kết

Mấy game này sống trên web — trang lưu trữ, bài đăng diễn đàn, thư mục của một
người bạn — và **cái nào cũng đến dưới dạng một liên kết trước khi đến dưới dạng
một tệp**. Bắt người chơi mở trình duyệt tải về, tìm trong thư mục Tải xuống,
rồi chọn ra từ hộp chọn tệp là ba bước cho một việc bộ giả lập làm được trong
một bước.

Hai loại liên kết:

- **.jar** là chính game, tự nó đã đủ.
- **.jad** là bản mô tả mà máy J2ME lẽ ra được đưa trước: nó ghi tên tệp .jar ở
  `MIDlet-Jar-URL`, nên tệp đó được tải luôn — **tính tương đối so với bản mô
  tả**, vì một tệp .jad trên trang web gần như luôn ghi tên .jar trần, nghĩa là
  "nằm ngay cạnh tôi". Ghi sai chỗ này thì đi lấy .jar ở gốc trang, thường là
  404 và thỉnh thoảng là game của người khác.

**Thứ tải về được xem trước khi cài.** Liên kết sai, hết hạn, hoặc chỉ tới trang
đăng nhập thì trả về một trang web, và một trang web cài vào làm game là một
game hỏng muộn hơn và khó hiểu hơn. Nên các byte được nhìn tận nơi: `.jar` là
tệp nén, bắt đầu bằng `PK`; bản mô tả thì phải phân tích được và phải ghi tên
một MIDlet.

Và **báo lỗi bằng tiếng người**, không phải bằng tiếng nhật ký:

- "Không có gì ở liên kết này (404)"
- "đây là một trang web, không phải tệp game" — câu này nói cho người chơi biết
  cần mở liên kết trong trình duyệt trước, thường đúng là chuyện đã xảy ra
- "Không kết nối được tới games.example" — thứ duy nhất giúp được là **địa chỉ
  nào không trả lời**, chứ không phải cái stack trace của tầng truyền
- "Tệp quá lớn": game J2ME nặng vài trăm KB; thứ gì lớn hơn 32 MB thì không phải
  game, và điện thoại không nên tốn bộ nhớ để phát hiện ra điều đó

**Tải qua đúng tầng mạng** mà game vẫn dùng, nên nó được ghi lại và xem lại
được (`downloadsJson`). Nhưng đi bằng một chính sách **riêng**: game phải hỏi
trước khi kết nối, còn một lượt tải mà người chơi tự gõ địa chỉ thì **chính việc
gõ đó là sự cho phép**. Đổi lại, tải xong nó nói rõ đã lấy những gì và lấy ở
đâu, chứ không giấu.

Kiểm tra chạy hoàn toàn **không cần mạng**: cả bản mô tả lẫn tệp .jar được phục
vụ từ `LoopbackTransport`, đi qua đúng cái facade mà điện thoại gọi. Bản xem
trước cũng vậy — màn "Nhập từ liên kết" trong ảnh chụp là **một lượt cài thật**
vừa chạy xong, không phải hình vẽ mô phỏng.


## Giai đoạn 39 — bộ sưu tập trong thư viện

Tìm kiếm chỉ tìm được **game mà người ta còn nhớ tên**. Một thư viện tám mươi
game thì phần lớn là những game không nhớ tên: "cái game đua xe ấy", "mấy game
hay chơi lúc đi xe buýt", "mấy game thằng em để lại trong máy". Một cái kệ là
cách tìm ra chúng — bằng việc chính mình đã xếp chúng vào đâu đó.

`core/library/CollectionStore.java`. Vài quyết định đáng nói:

- **Kệ nằm cạnh chỉ mục thư viện, không nằm trong hồ sơ từng game.** Cái kệ là
  một chuyện về *bộ sưu tập*, không phải chuyện về một game: dọn sạch một kệ
  không nên có nghĩa là ghi lại tám mươi tệp, và gỡ một game khỏi máy không nên
  mang theo cả cái kệ.
- **Xếp game vào một chỗ mới thì kệ tự sinh ra.** Đó là việc người ta thật sự
  làm: không ai tạo một cái kệ rỗng rồi mới đi xếp.
- **Tên chính là khoá**, nên nó được cắt khoảng trắng đúng một lần trong lớp
  này chứ không phải ở từng chỗ gọi: "Đua xe" và "Đua xe " là cùng một cái kệ
  với người vừa gõ chúng.
- **Đổi tên thì dựng lại danh sách** chứ không xoá-rồi-thêm, để kệ vừa đổi tên
  giữ nguyên chỗ của nó trong hàng thay vì nhảy xuống cuối.
- **Gỡ game thì mọi kệ quên nó đi**: một cái kệ còn ghi tên thứ đã biến mất là
  một con số không ai bấm tới được.
- Đọc hỏng thì **không làm sập ứng dụng**: game vẫn còn nguyên đó, chỉ là không
  nằm trên kệ nào.

Trên màn hình chính, kệ hiện thành một hàng chip trên danh sách, và **"Tất cả"
đứng đầu như một cái kệ nữa** — "không lọc gì" là thứ người chơi chọn nhiều
nhất, không nên bắt họ đi tìm một dấu × để bấm. Hàng chip chỉ xuất hiện khi đã
có kệ: một hàng chỉ có mỗi chip "Tất cả" thì chẳng nói với ai điều gì.

Trong trang từng game có thẻ "BỘ SƯU TẬP" để xếp vào hoặc lấy ra, kèm ô tạo kệ
mới ngay tại chỗ — vì lúc nghĩ ra cái kệ mình cần thường là đúng lúc đang nhìn
một game chưa biết xếp vào đâu.

Cầu nối: `collectionsJson` (mỗi kệ kèm số game **và** kệ đó có chứa game đang
xem hay không — màn hình xếp game cần cả hai), `createCollection`,
`toggleCollection`, `renameCollection`, `deleteCollection`, `collectionJson`
(trả về game theo **đúng hình dạng** mà cả thư viện trả về, nên màn hình vẽ danh
sách game không cần cách vẽ thứ hai).


## Giai đoạn 40 — chia sẻ ảnh chụp và đoạn quay

Một tấm ảnh **không gửi đi được** thì mới là nửa tấm ảnh. Thư viện ảnh xem
được, xoá được — nhưng lý do người ta bấm chụp là để **cho người khác xem**, và
điều đó cần tệp tồn tại ở chỗ ứng dụng khác mở được, dưới một cái tên **có
nghĩa** khi nó rơi vào một khung chat.

Trong ứng dụng, tấm ảnh tên là `1700000000000.png` — một con số biết sắp xếp,
đúng là cái tên nên đặt cho tệp mà chính ứng dụng đọc. Gửi cho người khác thì
cái tên đó **chẳng nói gì cả**: thứ đáng nằm trên đó là **tên game và lúc nào**.
Nên một bản sao được tạo dưới tên đọc được, và **bản sao mới là thứ đi ra**.

`core/library/ShareExport.java`:

- Tên có dạng `Sky Runner 2023-11-14 22-13.gif`. Dấu hai chấm và dấu gạch chéo
  không có trong đó, vì tên tệp mang một trong hai thứ đó là tên mà điện thoại
  nào, ứng dụng chat nào hoặc máy tính nào cũng sẽ từ chối — thay hai chấm bằng
  gạch nối là thứ mọi công cụ chụp màn hình đều đã đi đến, vì cùng một lý do.
- **Tên game là của người chơi**: họ đổi tên game thành gì cũng được, kể cả
  thành một đường dẫn. Mọi ký tự có thể lái chỗ ghi đi nơi khác, hoặc mà hệ tệp
  từ chối, đều bị bỏ; `../../etc/passwd` ra `etcpasswd`.
- **Ngày giờ tính bằng số học, không dùng bộ định dạng ngày**: lõi không có phụ
  thuộc nào để còn dịch được sang iOS, mà đổi từ mili giây ra ngày dương lịch
  chỉ tốn hơn chục dòng (thuật toán *civil-from-days* của Howard Hinnant — dời
  lịch cho năm bắt đầu từ tháng Ba, để ngày nhuận rơi vào cuối năm và độ dài các
  tháng thành một đường thẳng thay vì một bảng ngoại lệ). Bài kiểm tra soi đúng
  ba ngày mà một cuốn lịch viết tay hay sai: ngày nhuận, ngày sau ngày nhuận, và
  một năm tròn thế kỷ **không** nhuận.
- Bản sao nằm trong **cache**, cố ý: thứ đưa cho ứng dụng khác là bản sao người
  chơi không yêu cầu giữ, và điện thoại được phép dọn nó bất cứ lúc nào. Giữ 20
  bản mới nhất — một thư mục chỉ có lớn lên là một thư mục sẽ có ngày trở thành
  lý do điện thoại hết chỗ.

Bên Android, tệp đi ra qua `FileProvider` mở **đúng một thư mục** — chỗ để các
bản sao đã chuẩn bị. Dữ liệu lưu của game, tệp riêng của game, chỉ mục thư viện
đều **không** lộ ra. Bên iOS là `ShareLink` trỏ vào chính bản sao đó.

Nút chia sẻ nằm **ngay trên tấm ảnh**, cạnh nút xoá: gửi đi là lý do tấm ảnh
được chụp, nó không nên nằm sau một cú nhấn giữ mà không ai tìm ra.


## Giai đoạn 41 — nghiêng máy để lái

Không máy J2ME nào làm được chuyện này — cảm biến gia tốc đến sau mấy game này —
nên **đây không phải giả lập, mà là một cách chơi**. Nó hợp với những game nó
hợp: một game đua lái bằng trái phải, một mê cung nghiêng cho viên bi lăn. Và
nó **tắt sẵn**, vì với mọi game khác thì đó là một cái máy tự đi khi xe xóc.

### Hai ngưỡng, không phải một

Thứ làm cho tính năng này dùng được không phải là cái ngưỡng, mà là **cặp
ngưỡng**. Với một ngưỡng duy nhất, cái máy cầm đúng ngay ở mép sẽ gửi bấm, nhả,
bấm, nhả — hàng chục lần mỗi giây — và game đọc ra đó là **một người đang đập
phím**. Nên một hướng được **nhận ở góc lớn** và **trả lại ở góc nhỏ hơn**, và
khoảng cách giữa hai góc chính là thứ giữ cho một bàn tay gần như đứng yên
không bị đọc thành một bàn tay đang run.

Bài kiểm tra làm đúng chuyện đó: cho máy rung quanh mép ngưỡng bốn mươi lần, và
đòi kết quả là **một lần đổi trạng thái**, không phải hai mươi.

Nhân tiện, **cần analog của tay cầm cũng có cùng bệnh** và cùng cách chữa: nó
vốn chỉ có một vùng chết 0,5. Nay nhận ở 0,5 và trả lại ở 0,35, y như nghiêng
máy — hai đường vào cùng một kiểu, cùng một lý do.

### Còn lại

- **Độ nhạy 50–200%**: nhạy hơn nghĩa là nghiêng ít hơn đã đủ, nên con số lên
  thì góc xuống.
- **Hướng**: bốn hướng, chỉ trái phải (mặc định), hoặc chỉ lên xuống. Phần lớn
  game hợp với nghiêng máy chỉ cần lái.
- **Đảo chiều**, cho game vẽ ngược lại.
- Tắt giữa chừng thì **thả ngay những hướng đang giữ**, chứ không để game bị ép
  vào tường.
- Cảm biến chỉ chạy khi một game **đã bật tính năng này** đang mở: một cảm biến
  bị bỏ quên là một cục pin cạn cho cái màn hình không ai nhìn.

Cầu nối: `tiltJson`, `setTiltEnabled`, `setTiltSensitivity`, `setTiltAxes`,
`setTiltInverted`, `tilted` (nghiêng tính bằng **phần nghìn**, vì cầu nối chỉ
mang số nguyên). Android đọc `TYPE_GRAVITY`, iOS đọc `CMDeviceMotion.gravity` —
cả hai đều đã ở đúng đơn vị: máy nằm nghiêng hẳn là 1.


## Giai đoạn 42 — chơi tiếp ngay ở màn hình chính

Mở ứng dụng để chơi tiếp cái game vừa chơi là **việc người ta làm nhiều nhất
với nó**, và cho tới giờ nó tốn ba lần chạm: tìm game trong danh sách, mở ra,
bấm chơi. Nay là một lần.

Thẻ nằm trên đầu thư viện, và **nó nói rõ nó sẽ làm cái nào trong hai cái**:

- **"Chơi tiếp — Tiếp tục từ chỗ đã lưu"** khi có trạng thái tự lưu.
- **"Chơi lại — Bắt đầu lại từ đầu"** khi không có.

Hai chuyện đó **không phải một**: người chơi được mời "chơi tiếp" mà nhận về một
ván mới thì đã mất đúng thứ họ quay lại để lấy. Nên thẻ đọc trạng thái lưu
trước, rồi mới chọn chữ.

Vài chỗ nhỏ nhưng cố ý:

- **Cài rồi chưa chơi thì không hiện.** Một cái thẻ mời "chơi tiếp" một game
  chưa ai bắt đầu là một cái thẻ nói về không có gì.
- **Đang tìm kiếm hoặc đang lọc theo kệ thì không hiện.** Lúc đó danh sách là
  câu trả lời cho một câu hỏi cụ thể, và cái thẻ này trả lời một câu khác.
- **Nút bấm tự tìm lại game mới nhất** chứ không cầm sẵn mã bộ cài từ lúc vẽ
  thẻ: giữa lúc vẽ và lúc bấm, game đó có thể đã bị gỡ, và chạy cái đang thật
  sự mới nhất thì tốt hơn là báo lỗi về cái đã từng là.

Bài kiểm tra đi qua đúng vòng đời đó: thư viện rỗng → cài mà chưa chơi → chơi
rồi thoát thường → chơi rồi thoát kiểu điện thoại (có lưu) → cài game thứ hai và
chơi → gỡ game đang được mời. Game thứ hai được dựng bằng cách **cài lại chính
tệp .jar đó với một bản mô tả khác tên**, vì thuộc tính trong .jad thắng
manifest — nhờ vậy game thứ hai chạy thật chứ không phải một lớp giả.

Cầu nối: `continueJson` và `continueGame`.

## Giai đoạn 43 — game hỏng thì nói vì sao

Cho tới giờ, một game chết để lại đúng hai thứ: **màn hình đứng im**, và một
dòng chữ đỏ cỡ chú thích nằm lọt giữa bàn phím ảo, ghi
`NoClassDefFoundError: javax/microedition/m3g/World`. Dòng đó viết cho người
làm máy ảo. Người đang chơi đọc xong vẫn không biết game hỏng vì cái gì, và
nhất là không biết mình có làm được gì không.

Nay mỗi lần hỏng được đọc thành **ba câu, đúng thứ tự người ta hỏi**: hỏng cái
gì, vì sao, làm gì tiếp. Việc phân loại nằm ở `CrashDiagnosis` trong lõi, nên
Android, iOS và bản xem trước không thể nói ba kiểu khác nhau về cùng một lỗi.

Cùng một ngoại lệ có thể là hai chuyện hoàn toàn khác nhau, và chỗ đó mới là
chỗ đáng làm cho đúng:

- `NoClassDefFoundError` **trên lớp của thư viện điện thoại** là phần máy ảo
  chưa làm. Tên phần thiếu được lấy từ đúng bảng tên mà thẻ "chưa chạy được"
  trong thư viện dùng (`Compatibility.describe`), nên game được báo trước bằng
  chữ gì thì lúc chết cũng được gọi bằng chữ ấy. Người chơi không bật nó lên
  được, nên **không có lời khuyên giả vờ** rằng tải lại sẽ xong.
- `NoClassDefFoundError` **trên lớp của chính game** là tệp .jar thiếu một mẩu
  — tải dở, bị cắt. Ở đây "tải lại tệp gốc" đúng là việc phải làm.

Còn lại: hết bộ nhớ, không mở được kết nối, hỏng phần lưu, đòi kiểu âm thanh
chưa phát được, và lỗi trong chính mã game. Cái nào không đủ căn cứ thì **nhận
là không biết** chứ không bịa một lý do nghe hợp lý.

Vài chỗ cố ý:

- **Thông báo gốc tiếng Anh không chen vào câu tiếng Việt.** "dùng một thứ chưa
  được tạo ra (array access on null)" là nửa Việt nửa Anh; nguyên văn ngoại lệ
  ở lại phần *chi tiết kỹ thuật*, gấp sẵn, cho người sửa game.
- **Chết rồi thì không vẽ tiếp.** Một game đã chết thì khung hình sau cũng chết
  y như vậy — trước đây nó ghi cùng một lỗi mấy chục lần mỗi giây. Lần hỏng
  được giữ là lần đầu tiên, không bị ghi đè.
- **Ngăn xếp phải chụp đúng lúc.** Ngăn xếp bị gỡ sạch trong lúc ngoại lệ bay
  lên, nên chỗ bắt được nó lại là chỗ không còn gì để đọc: `Interpreter` chụp
  lại ngay khi ngoại lệ rời khung ngoài cùng — tức là lúc chắc chắn không ai
  trong game bắt nó — và `crashTrace()` trả về bản chụp ấy.
- **Lời giải thích sống lâu hơn game.** Nó còn nguyên sau khi phiên chạy bị
  dọn, vì màn hình báo lỗi chỉ hiện ra sau lúc đó.

Bản mẫu `demo.CrashDemo` là một game hỏng thật: nó mở ra được, vẽ được, rồi ngã
ở khung hình đầu tiên vì một mảng chưa ai gán — đúng kiểu game J2ME viết cho
một đời máy rồi đem chạy trên đời máy khác. Ảnh chụp màn hình báo lỗi là chữ
đọc thẳng từ cầu nối, không viết sẵn.

Cầu nối: `crashJson`, `hasCrashed` và `dismissCrash`.

## Giai đoạn 44 — máy ảo khai nó là máy gì

Game J2ME hỏi nó đang chạy trên máy nào:

```java
String platform = System.getProperty("microedition.platform");
if (platform != null && platform.startsWith("Nokia")) { … }
```

và **đổi cách chạy theo câu trả lời** — bộ ảnh nào đúng cỡ màn hình, có bật
đường vẽ riêng của Nokia không, mã phím nào, có khi chỉ đơn giản là từ chối
chạy. Câu trả lời của máy ảo này cho tới giờ là `MobiCore`: một cái tên **chưa
game nào từng nghe**, nên game rơi vào đúng nhánh dành cho máy lạ — nhánh ít
được thử nhất và hỏng nhiều nhất.

Nay câu trả lời là **Nokia6233/05.10** — đúng con máy [J2ME Loader khai trong
`assets/defaults/system.props`][props], và cùng lý do: nhánh Nokia là nhánh
được nhiều game chăm chút nhất, còn phần Nokia thì máy ảo này có làm thật
(FullCanvas, DirectGraphics, DeviceControl).

**Một câu trả lời, cho mọi game.** Bản đầu của giai đoạn này có một tủ chọn sáu
chiếc máy, và tủ ấy đã bị bỏ: máy ảo này là **một cỗ máy duy nhất** — một cỡ
màn hình 240×320 (giai đoạn 32), một kiểu bàn phím, một bảng thuộc tính. Thêm
một tủ chọn là đẩy sang người chơi đúng câu hỏi mà giai đoạn 32 đã bỏ đi vì họ
không có cách nào trả lời đúng, và mỗi câu trả lời sai lại là một cỗ máy nữa
phải chịu trách nhiệm. Màn hình cài đặt vì thế **chỉ bày ra** những gì game đọc
được, không cho chọn.

Chỉ khai những thứ **thật sự có**. Một chiếc máy khai `microedition.m3g.version`
rồi để game gọi vào 3D là một chiếc máy nói dối: game không chết ở câu hỏi, nó
chết ở câu gọi ngay sau đó, và lúc ấy chẳng ai lần ra vì sao. Nên bảng khai gồm
CLDC-1.1, MIDP-2.0, phần tệp, phần âm thanh và cỡ màu 565 của DirectGraphics —
hết. Hỏi 3D hay danh bạ thì nghe thấy **không có**, đúng cách một chiếc máy
không có phần đó trả lời. Và `ISO-8859-1`, không phải UTF-8: game đời ấy đọc
chuỗi theo từng byte, đổi bảng mã làm lệch chính chữ của nó.

Bản mẫu `demo.DeviceDemo` hỏi đúng câu ấy rồi tự viết ra nó nghe thấy gì và rẽ
nhánh theo. Bài kiểm tra **nhìn vào điểm ảnh** để biết game rẽ nhánh nào: cùng
một lớp bytecode, nghe thấy tên Nokia thì vào nhánh Nokia.

Nhân tiện, `Ui.field` được sửa cho đúng cái nó vẫn làm sai: nhãn dài nuốt hết
chỗ của giá trị. Một cái tên như `com.nokia.mid.ui.DirectGraphics.PIXEL_FORMAT`
chạy hết hàng và ép giá trị `565` xuống còn đúng dấu ba chấm. Nay giá trị giữ
chỗ của nó trước, nhãn cắt bớt sau — vì giá trị mới là thứ người ta đọc, còn
nhãn thì cắt bớt vẫn đoán ra.

Cầu nối: `systemPropertiesJson` — chỉ để đọc.

[props]: https://github.com/nikita36078/J2ME-Loader/blob/master/app/src/main/assets/defaults/system.props


### Sửa: trang xem trước không còn bị cắt ngang

Trang chi tiết game và trang nhập game **dài hơn tấm vẽ** của bản xem trước, và
chỗ bị cắt rơi đúng vào giữa một cái thẻ — chữ lọt ra khỏi khung, hai mục cuối
của trang chi tiết ("NỘI DUNG BỘ CÀI" và nút gỡ game) thì không hiện ra lần
nào. Nó đã xảy ra hơn một lần vì cùng một chuyện: nội dung dài thêm mà con số
chiều cao thì đứng yên — thêm hai ứng dụng mẫu vào bộ cài là đủ.

Nay các trang ấy được vẽ vào một tấm rộng tay rồi **cắt về đúng chiều dài
thật** (`Preview.fit`), nên trang nào cũng hiện đủ dù nội dung của nó đổi. Và
để chuyện này không âm thầm quay lại: khi nội dung chạm đáy tấm vẽ, `fit` **kêu
lên** thay vì lặng lẽ trả về một trang cụt — cắt thêm khoảng trắng vào đấy chỉ
giấu chỗ cụt đi. Bài kiểm tra `Trang xem trước không bị cắt` vẽ từng trang và
nhìn mấy hàng cuối, chứ không kiểm một con số chiều cao nào: con số đúng hôm
nay sẽ sai vào ngày ai đó thêm một dòng.

Nhân tiện, dòng "Máy giả lập" trong trang chi tiết được gọi lại đúng tên của
nó — **"Màn hình"** — vì từ giai đoạn 32 nó chỉ còn nói về cỡ màn hình, và từ
giai đoạn 44 thì "máy giả lập" đã có nghĩa khác.

## Giai đoạn 45 — game treo thì vẫn thoát được

Game J2ME viết vòng lặp của chính nó, và một vòng lặp **không có lối ra** là
chuyện thường trong đám game viết cho đúng một đời máy: chờ một phím không bao
giờ tới, chờ một cờ không bao giờ đổi. Trên máy thật đó là một chiếc điện thoại
phải tháo pin. Ở đây, cho tới giờ, đó là luồng chạy game kẹt trong máy ảo mãi
mãi: màn hình đứng im, không nút nào bấm được, và cách duy nhất thoát ra là tắt
hẳn ứng dụng — mất luôn cả phần chưa lưu.

Nay có hai lối ra, và cả hai đều phải chạy được **từ bên trong một vòng lặp vô
tận**:

- **Hết giờ thì máy ảo tự cắt.** Một lời gọi vào game chạy quá **tám giây** bị
  cắt ngang và báo lên như một lần hỏng, với lời giải thích riêng của nó: "Game
  bị treo — game chạy mãi một chỗ mà không vẽ xong khung hình". Tám giây là
  rộng tay có chủ ý: máy ảo dịch từng lệnh, một màn mở đầu nặng có thể chạy vài
  giây thật, và **cắt nhầm một game đang chạy đúng thì tệ hơn là đợi thêm**.
- **Người chơi bấm thoát thì cắt ngay.** Không ai ngồi đợi cho hết tám giây.
  Lệnh dừng đến từ luồng giao diện và xuyên thẳng vào chỗ game đang chạy, nên
  rời một game treo là chuyện tức thì. Nó **không** bị coi là một lần hỏng —
  không có gì để báo, không có gì để giải thích — nên nó mang một tên riêng
  (`VmCancelled`) để chỗ bắt được phân biệt.

Cách cài đặt đáng nói ở chỗ nó **không làm chậm vòng lặp chính**. Vòng lặp đã
sẵn có một phép so sánh mỗi lệnh (hạn số lệnh); nay phép so sánh ấy đếm tới một
*mốc*, và cứ 65536 lệnh mới ngó ra ngoài một lần: hết giờ chưa, có ai bảo dừng
chưa. Máy ảo chạy hàng chục triệu lệnh mỗi giây, nên mốc ấy đủ nhỏ để bắt được
một game treo trong vài phần nghìn giây, và đủ lớn để phép so sánh thêm vào
không đo được.

Giờ dùng ở đây là **giờ thật**, không phải đồng hồ của game: điều khiển tốc độ
làm đồng hồ game chạy nhanh chậm khác đi, còn "người ngồi đợi bao lâu" thì
không.

Và dọn dẹp vẫn phải chạy được: `destroyApp` cùng việc ghi nốt phần lưu cũng là
mã chạy trong máy ảo, nên lệnh dừng được gỡ ngay đầu `destroy()` — dừng một
game treo không được phép làm mất phần đã chơi.

Bản mẫu `demo.HangDemo` treo thật bằng bytecode thật. Bài kiểm tra chạy cả hai
lối ra, và lối thứ hai chạy **từ một luồng khác** — vì đó đúng là cách nó xảy
ra ngoài đời.

Cầu nối: `requestStop`.

## Giai đoạn 46 — đọc được ảnh JPEG

MIDP chỉ bắt buộc máy đọc được **PNG**, nên máy ảo này lâu nay cũng chỉ đọc
PNG. Nhưng máy thật thì đọc thêm **JPEG**, và game biết thế: ảnh mở đầu, ảnh
nền, ảnh chân dung nhân vật — những thứ to và nhiều màu — hay được đóng gói
bằng JPEG vì nó nhẹ hơn hẳn. Game gọi `Image.createImage` với một tệp như vậy
thì trước đây nhận về `Unsupported image format` rồi chết ngay ở màn mở đầu.

`JpegReader` đọc **baseline** (SOF0/SOF1): Huffman, 8 bit, ảnh xám một thành
phần hoặc ảnh màu ba thành phần, mọi kiểu lấy mẫu màu thường gặp (4:4:4, 4:2:2,
4:2:0), kèm mốc khởi động lại. Ảnh **progressive** (SOF2) thì **nói thẳng là
chưa đọc được** — giải nó bằng cách của ảnh thường vẫn ra một tấm ảnh, nhưng là
một tấm nhiễu, và game sẽ vẽ tấm nhiễu ấy lên màn hình mà không ai hiểu vì sao.

Vài chỗ đáng nói:

- **Phần màu lưu thưa hơn phần sáng.** Mắt người nhạy với sáng tối hơn nhiều so
  với màu, nên JPEG lưu màu thưa gấp đôi theo cả hai chiều là chuyện thường.
  Quên giãn nó ra thì ảnh chỉ đúng một góc phần tư và ba phần còn lại xám
  ngoét — nên bài kiểm tra soi đúng cái góc xa nhất.
- **Bảng cosin dựng sẵn một lần**, và phép biến đổi ngược bỏ qua hệ số bằng
  không: phần lớn hệ số của một khối 8×8 là số không, đó chính là lý do JPEG
  nhỏ.
- **Byte `FF` trong phần ảnh được viết thành `FF 00`** để không lẫn với mốc
  đánh dấu; đọc tới đó thì bỏ byte `00` đi.
- **Tệp cụt vài byte cuối vẫn đọc được**: phần thiếu đọc bằng số 0 thay vì vứt
  cả tấm ảnh đi. Game đời ấy nhiều tệp đóng gói ẩu.

Ảnh dùng để kiểm tra là **ảnh JPEG thật** (`JpegSamples`, nhúng dưới dạng chữ vì
kho mã này không giữ tệp nhị phân), và cái được kiểm là **màu đọc ra có đúng
không** — một bộ đọc sai vẫn chạy trơn tru và trả về một tấm nhiễu, nên "chạy
mà không nổ" không chứng minh được gì. Bốn góc của dải màu, chiều tăng dần của
dải, ba kênh bằng nhau ở ảnh xám, và góc xa nhất của ảnh lấy mẫu thưa.

Bộ cài mẫu nay mang theo `res/photo.jpg`, và `demo.PhotoDemo` vẽ nó ra: ảnh
trong ảnh chụp màn hình là ảnh thật, do bộ đọc này giải mã, vẽ bởi một MIDlet
thật.

### Rà lỗi sau khi làm xong

Một bộ đọc ảnh chạy đúng với bốn tấm ảnh lành chưa chứng minh được gì: thứ nó
gặp ngoài đời là tệp tải dở, thẻ nhớ lỗi, gói game bị cắt. Nên bản đầu được đem
ra **thử phá**: cắt cụt ở mọi độ dài, lật byte ngẫu nhiên, và rác hoàn toàn —
hơn hai chục nghìn tệp hỏng.

- **310 tệp hỏng làm lọt ra `ArrayIndexOutOfBoundsException`.** Với game thì đó
  là khác biệt sống còn: `IOException` là thứ game viết sẵn `try/catch` để bắt
  và tự xử lý, còn lỗi kia thì không ai bắt và cả khung hình chết theo. Nguyên
  nhân là mấy chỗ đọc thẳng vào bảng mà không kiểm số hiệu bảng, và mấy chỗ đọc
  quá đuôi tệp. Nay mỗi chỗ tự kiểm, cộng một lưới an toàn cuối cùng, và **hai
  mươi nghìn tệp hỏng không còn tệp nào lọt**.
- **Một tệp khai ảnh 65535×65535** thì bộ đọc xin gần mười bảy tỉ điểm ảnh và
  máy hết bộ nhớ trước khi kịp biết là tệp hỏng. Nay có hạn.
- **Giãn phần màu bị thô.** Đối chiếu với thư viện JPEG chuẩn thì ảnh thường
  lệch trung bình khoảng 1 mức trên 256, nhưng ảnh nhỏ và ảnh có cạnh lẻ lệch
  tới **17 mức** — vì phần màu được giãn bằng cách lặp lại điểm gần nhất, và
  mỗi mẫu màu phủ đúng một ô 2×2. Nay nội suy giữa hai mẫu kề nhau, canh theo
  *tâm* điểm ảnh chứ không theo mép, và mức lệch xuống còn **0,5 trên mọi cỡ
  ảnh đã thử** — kể cả 8×8, 37×23 hay 100×3.

Cả ba đều có bài kiểm tra riêng, và cả ba bài đều được thử ngược lại bằng cách
cố ý làm hỏng mã: bỏ lưới an toàn thì bài kiểm tệp hỏng đỏ, quay về lấy mẫu lặp
lại thì bài kiểm độ mượt đỏ. Một bài kiểm tra không đỏ khi mã sai thì không
kiểm gì cả.

### Bỏ con số trên đầu màn game

Thanh trên cùng lúc chơi ghi `240×320 · 30 hình/giây`. Cỡ màn hình thì cả đời
máy ảo chỉ có một, nên nó nói một chuyện ai cũng biết; còn số hình mỗi giây là
con số của người viết máy ảo, và một con số nhảy liên tục ngay trên đầu màn
game thì kéo mắt đi khỏi đúng thứ người ta đang nhìn. Nay giữa thanh để trống:
còn lại đúng đường ra thư viện và nút menu.

Công tắc "Hiện số khung hình" trong cài đặt cũng bỏ theo — một công tắc không
còn bật tắt được gì thì tệ hơn là không có — và cùng với nó là bộ đếm hình mỗi
giây trong cả hai máy chạy game, thứ từ nay không ai đọc.


## Giai đoạn 47 — nối thẳng bằng socket

Lớp mạng cho tới giờ chỉ biết **hỏi một câu rồi nghe một câu trả lời**: đúng
hình dạng của `http`, và sai hình dạng của gần như mọi thứ còn lại. Game nhiều
người chơi đời ấy không nói chuyện kiểu đó. Hai máy giữ một đường dây mở rồi
thay nhau nói trên đó; phòng chờ giữ một đường tới máy chủ suốt lúc người chơi
còn trong phòng; vài game bắn thẳng từng gói nhỏ vì mất một gói còn đỡ hơn ngồi
đợi nó. Game nào mở `socket://` thì trước đây nhận về "MobiCore chỉ hỗ trợ
http" rồi tắt ngay ở màn chọn phòng.

Nay có ba thứ, vì game dùng cả ba:

- **`socket://máy:cổng`** — một đường dây mở tới máy khác.
- **`socket://:cổng`** — chính máy này mở một cổng cho người khác gọi vào.
  Địa chỉ không có phần tên máy không phải là địa chỉ hỏng: đó là cách game
  nói "tôi đợi, đừng gọi đi đâu cả".
- **`datagram://`** — từng gói một, gửi đi không đợi hồi âm.

Vài chỗ đáng nói:

- **Vẫn đi qua đúng cái cửa cũ.** Chính sách theo từng máy chủ và network
  monitor đứng trước socket y như đứng trước `http`: một game hai mươi năm
  tuổi mở đường tới một địa chỉ người chơi chưa nghe bao giờ chính là lúc đáng
  hỏi nhất. Việc mở cổng trên máy mình thì không có tên máy nào để hỏi, nên nó
  được nhớ dưới tên chính chiếc máy này — hỏi một lần, không hỏi lại mỗi lần
  game mở cổng.
- **Đếm byte thì phải cộng dồn.** Một socket không có "thân yêu cầu" duy nhất
  để đo: nó chở bất cứ thứ gì game gõ vào, suốt đời đường dây ấy. Nên bộ đếm
  cộng lên, còn phần xem trước chỉ giữ đoạn mở đầu — đúng đoạn nói cho biết
  đây là giao thức gì.
- **Dừng game phải cắt được đường dây.** Cách thoát khỏi một game treo là đếm
  lệnh nó chạy; nhưng một luồng đang chờ đọc socket thì có chạy lệnh nào đâu
  mà đếm. Thứ đánh thức nó là đóng đường truyền bên dưới, nên `requestStop`
  đóng mọi kết nối game còn để ngỏ. Không có chỗ này thì nút thoát ngồi đợi
  một máy chủ có thể không bao giờ trả lời.
- **Gói gửi vào chỗ không ai nghe thì rơi**, đúng như UDP vẫn làm. Báo lỗi ở
  đây là dạy game một điều sai.
- **Nói thẳng khi không chở được.** `comm://`, hồng ngoại và phần còn lại vẫn
  bị từ chối, kèm tên giao thức — giả vờ mở được sẽ để game ngồi đọc từ hư
  không.

Một lỗi cũ lộ ra khi làm phần này: **game chưa bao giờ thật sự ra được mạng**.
Máy ảo dựng lớp mạng nhưng không ai gắn đường truyền thật vào, nên đặt "cho
phép game vào mạng" trong hồ sơ game không có tác dụng gì. Nay `startGame` gắn
sẵn, và chính sách phía trước vẫn là thứ quyết định.

`LoopbackSockets` là nửa sau của cầu nối máy chủ nội bộ mà `LoopbackTransport`
mở đầu: một game có phòng chờ đã đóng cửa từ lâu nay được trả lời ngay trên
máy. Ống dẫn là luồng thật, đọc chặn thật, nên mã được kiểm chính là mã chạy
với socket thật; mọi chỗ chờ đều có hạn giờ, vì một mạng trong bộ nhớ mà kẹt
được thì còn tệ hơn không có — một bài kiểm tra treo thì chẳng nói lên điều gì.

Bản mẫu `demo.SocketDemo` là MIDlet thật: nó gửi một chữ tới máy chủ rồi đọc
câu trả lời, mở một cổng rồi nhận người gọi vào, bắn một gói đi rồi bắt lại
chính gói ấy. Cái được kiểm là **những gì nó đọc được**, không phải "gọi hàm
không nổ": một lớp socket mở rỗng rồi trả về chuỗi trống cũng qua được bài
kiểm tra chỉ soi ngoại lệ.

Cầu nối: `setGameNetwork`.

### Rà lỗi sau khi làm xong

Đem phần socket ra thử phá thì lộ ba chỗ, và cả ba đều là thứ gặp thật:

- **Số cổng vô lý làm lọt ra một lỗi không ai bắt được.** Số cổng hiếm khi
  được viết cứng trong game: nó đến từ ô "địa chỉ máy chủ" người chơi gõ vào,
  từ một dòng máy chủ gửi về, từ một tệp cấu hình. `java.net` trả lời một con
  số vô lý bằng `IllegalArgumentException` — game viết sẵn
  `try/catch (IOException)` quanh chỗ mở kết nối thì bắt không được, và cả
  khung hình chết theo. Nay cả bốn đường (gọi ra, mở cổng chờ, mở cổng gói
  tin, gửi gói) đều kiểm số cổng ở đúng một chỗ trong `NetworkStack`, và báo
  bằng `IOException` kèm con số sai. Cổng 0 vẫn hợp lệ ở chỗ mở cổng chờ, vì
  đó là cách nói "cổng nào trống cũng được".
- **Bảng theo dõi in ra chữ "null".** Một địa chỉ như `socket://:7100` là game
  mở cổng trên chính máy đang chơi, nên không có tên máy nào ở đầu bên kia.
  Nay dòng ấy ghi dưới tên chính máy này và hiện lên là **"máy này"**.
- **Cột trạng thái vô nghĩa với socket.** Một đường dây mở không có mã trạng
  thái kiểu `200`/`404`; nó chỉ có số byte đã đi qua, nên cột ấy nay hiện số
  byte gửi và nhận.

Bài kiểm tra chạy trên **cả hai đường truyền**: đường trong bộ nhớ thì hiền,
còn `java.net` mới là chỗ ném ra lỗi không ai bắt được — kiểm mỗi đường hiền
thì bài kiểm tra chẳng canh được gì. Và cả hai bài đều được thử ngược bằng
cách bỏ chỗ vá đi: bỏ ra thì chúng đỏ.

## Giai đoạn 48 — tệp trong game, và chia thẻ cho công cụ

Một game J2ME là một cái hộp `.jar`, và đổi một tấm ảnh bên trong là việc người
ta vẫn làm: Việt hoá chữ nằm trong ảnh, thay bộ hình nhân vật, đổi ảnh nền cho
vừa mắt. Cho tới giờ muốn làm thì phải mang tệp sang máy tính, giải nén, sửa,
đóng gói lại — rồi cài lại và mất phần đã lưu.

Nay máy ảo **tự đọc cái hộp ấy** và bày ra: mỗi thứ bên trong là gì, nặng bao
nhiêu, ảnh thì bao nhiêu điểm ảnh, đã bị thay chưa và ai thay. Chọn một tệp
trên máy là xong.

**Nhìn vào ruột tệp, không nhìn cái tên.** Đây mới là phần khó. Game đời ấy đặt
tên rất tuỳ hứng: một tấm PNG nằm trong `data/12.dat`, một đoạn nhạc trong
`r/07` không có đuôi tên. Đoán theo đuôi thì nửa số tệp thành "không rõ" — mà
đó lại đúng là những tệp người ta muốn thay. Nên bảng này đọc mấy byte đầu:
PNG, JPEG, GIF, MIDI, WAV, MP3, AMR, OGG. Còn tệp toàn chữ đọc được thì gọi là
**chữ**, vì bảng lời thoại và bảng màn chơi hay nằm ở dạng đó, và đó là thứ hay
bị sửa nhất khi Việt hoá một game.

**Game gốc không bị đụng tới.** Thứ thay vào nằm trong một bản mod riêng tên
"Của tôi" — cùng đường cài, cùng chỗ lưu, cùng cách gỡ như mọi bản mod khác —
phủ lên trên lúc chơi. Bỏ ra lúc nào cũng được, và bản cài vẫn nguyên. Đường
dẫn được rửa sạch trước khi ghi: một bản mod chỉ ghi vào chính nó, `..` thì bị
từ chối.

### Chia thẻ

Trang công cụ trước đây là một trang dài xếp chồng: mạng, mod, JAD, RMS, tài
nguyên. Nay là bốn thẻ — **Tài nguyên · Mạng · Mod · Dữ liệu** — vì chúng trả
lời bốn câu hỏi khác nhau, và người đang tìm một tấm ảnh để thay không việc gì
phải cuộn qua danh sách lớp Java. Cả ba giao diện đều chia thẻ: Android bằng
hàng thẻ, iOS bằng thanh phân đoạn, bản xem trước bằng hàng thẻ vẽ tay.

Cầu nối: `resourcesJson`, `replaceResource`, `restoreResource`, `resourceImagePng`.

Nhân tiện, `res/theme.mid` trong bộ cài mẫu từ nay là **một tệp MIDI thật** chứ
không phải năm nghìn byte số 0 mang tên `.mid`: bảng tài nguyên đọc ruột tệp,
nên một tệp giả thì nó gọi đúng tên — "dữ liệu".

## Giai đoạn 49 — tìm và sửa số vàng trong game

Game J2ME chơi một mình lưu mọi thứ vào RMS: **một dãy byte không có nhãn**.
Không có tên trường, không có kiểu, mỗi game một cách ghi. Mở phần lưu ra nhìn
thì chỉ thấy hex — không ai đoán được bốn byte nào là số vàng.

Nhưng người chơi thì **biết** mình đang có bao nhiêu. Nên cách làm là đi ngược,
đúng như cách người ta vẫn sửa game từ mấy chục năm nay:

1. Nhìn màn hình game: đang có **8630 vàng**. Gõ 8630 vào. Máy tìm khắp phần
   lưu và thường ra vài chục chỗ — 8630 có thể là số vàng, mà cũng có thể là
   điểm cao, là toạ độ, là một mẩu của con số khác.
2. **Chơi tiếp cho con số đổi đi**, còn 8500. Gõ 8500 vào. Máy chỉ giữ lại
   những chỗ **đổi theo đúng như vậy**. Hai lần thường đủ.
3. Đặt số mới. Phần lưu được **sao lưu trước khi ghi**.

Vài chỗ cố ý:

- **Không đoán kiểu ghi.** Game ghi số bằng đủ kiểu: bốn byte, hai byte, một
  byte, đầu to hay đầu nhỏ, thậm chí viết thành chữ số trong một dòng như
  `player=Tin;gold=8630;`. Chỗ này thử tất cả, và **nhớ kiểu nào khớp** để lúc
  ghi còn ghi đúng kiểu ấy — ghi bốn byte đè lên một ô hai byte là làm hỏng
  phần lưu.
- **Số không vừa ô thì từ chối, không cắt cụt.** Nhét 70000 vào một ô hai byte
  thì game đọc ra 4464 — tệ hơn là không sửa được.
- **Đặt vào mọi chỗ còn lại.** Game hay giữ số vàng ở hai nơi thật: một bản
  nhị phân để đọc nhanh, một bản viết thành chữ trong dòng lưu tên. Sửa mỗi
  một nơi là để lại một phần lưu tự mâu thuẫn, và game thường tin chỗ mình
  không sửa.
- **Số viết thành chữ thì độ dài đổi theo giá trị**: "9" ngắn hơn "8630", nên
  bản ghi được dựng lại chứ không ghi đè bừa vào giữa.

Bản mẫu `demo.PiggyBank` là một game thật giữ ví tiền trong RMS, và nó có mồi
nhử: số điểm bằng đúng số vàng lúc bắt đầu rồi đứng yên — đúng kiểu trùng số mà
một lần tìm không phân biệt được. Bài kiểm tra chạy đủ vòng trên nó và chốt
bằng câu hỏi duy nhất đáng hỏi: **game mở lại có đọc ra con số mới không**.

Nhân tiện, máy ảo nay có `String.getBytes(String)` — bản CLDC nhận tên bảng mã.
Game dùng nó thật (ghi tên người chơi bằng UTF-8), và thiếu nó thì game chết
ngay ở câu lưu.

### Bảng vật phẩm

Vàng không phải thứ duy nhất đáng sửa, và tìm một con số là việc mất công: mở
game, nhìn số, gõ vào, chơi tiếp, gõ lại. Làm xong cho số vàng rồi lần sau lại
làm y hệt cho số thuốc hồi máu — mà lần sau nữa đã quên mất chỗ cũ — thì công
cụ chỉ dùng được một lần.

Nên chỗ đã tìm ra được **đặt tên và cất đi**: "Vàng", "Thuốc hồi máu", "Ngọc".
Bảng nằm cạnh hồ sơ của game nên sống qua những lần tắt máy, và từ lần sau chỉ
còn **ô tìm kiếm, ô số lượng, nút gửi**. Cái đáng giữ không phải con số, mà là
biết con số ấy nằm ở đâu.

- **Ô tìm kiếm bỏ dấu**: gõ "thuoc" cũng ra "Thuốc hồi máu", vì không ai gõ dấu
  khi tìm nhanh.
- **Mỗi vật phẩm biết mức tối đa của nó.** Thuốc nằm trong hai byte thì nhiều
  nhất là 65535; gửi 70000 vào đó thì game đọc ra 4464, nên chỗ này từ chối
  kèm con số đúng.
- **Bỏ những chỗ nằm lồng nhau.** Một ô bốn byte cũng khớp khi đọc hai byte
  cuối của nó, và khớp cả khi đọc một byte — một ô duy nhất hiện ra thành ba
  chỗ. Ghi vào cả ba là ghi đè lên chính mình: hai byte cuối bị đặt lại và con
  số bốn byte thành ra một con số khác. Nên chỉ giữ chỗ rộng nhất; chỗ ở bản
  ghi khác hay chỗ viết thành chữ thì vẫn giữ, vì đó là những nơi *khác nhau*
  cùng chép một con số.

Bản mẫu nay giữ hai thứ — vàng bốn byte và thuốc hai byte — và bài kiểm tra đi
đủ vòng cho cả hai: tìm, đặt tên, gửi số mới, mở lại game và đọc ra đúng cả
hai. Bảng cũng được mở lại sau khi tắt ứng dụng, vì đó là chỗ nó đáng giá nhất.

Cầu nối: `scanSave`, `narrowSave`, `setAllSaveValues`, `clearSaveScan`,
`itemsJson`, `keepItem`, `sendItem`, `renameItem`, `forgetItem`.

## Giai đoạn 50 — vẽ ngay khi game bảo vẽ

Hầu hết game J2ME tự chạy vòng lặp của mình, và vòng lặp ấy luôn cùng một hình:

```java
while (playing) {
    tick();               // tính bước tiếp theo
    repaint();            // xin vẽ
    serviceRepaints();    // và đợi vẽ xong
    Thread.sleep(50);
}
```

Bước giữa là **một lời hứa MIDP đưa ra**: `serviceRepaints` chặn lại cho tới
khi khung hình vẽ xong. Máy ảo này để trống nó — một hàm rỗng — nên game vẫn
chạy, và đó chính là lý do lỗi này lọt được lâu đến vậy: **màn hình vẫn có
hình**. Chỉ có điều nhịp không còn là nhịp game tự đặt ra: nó tính hàng chục
bước giữa hai khung hình do vòng lặp máy ảo vẽ hộ, và người chơi thấy nhân vật
nhảy cóc.

Nay `serviceRepaints` làm đúng việc của nó: vẽ ngay, tại đó, trên chính luồng
đang gọi — cùng một đường vẽ mà vòng lặp máy ảo dùng, nên khung hình ra y như
nhau dù ai gọi.

Ba chỗ phải cẩn thận:

- **Gọi lồng.** Game gọi `serviceRepaints` từ trong chính `paint` của nó là
  chuyện có thật; vẽ tiếp ở đó là gọi đệ quy không đáy. Trong lúc đang vẽ thì
  lời gọi ấy bị bỏ qua.
- **Khung hình game tự vẽ vẫn phải lên màn hình.** Vòng lặp máy ảo nhìn vào cờ
  "có ai xin vẽ không", và game vừa tự vẽ xong thì cờ ấy đã tắt — khung hình sẽ
  nằm im trong bộ nhớ. Nay có thêm dấu "vừa vẽ" để vòng lặp biết còn thứ mới mà
  đưa lên.
- **Chỉ vẽ khi có ai xin.** `serviceRepaints` không phải lệnh vẽ, nó là lệnh
  *chờ*: không có repaint nào đang đợi thì không có gì để làm.

Bản mẫu `demo.LoopDemo` viết đúng vòng lặp ấy và tự đếm: xin mấy lần, vẽ mấy
lần, và **khung hình có vẽ xong trước khi `serviceRepaints` trả về hay không**.
Thử ngược lại bằng cách trả hàm về rỗng thì bài kiểm tra đỏ đúng ba chỗ.

Đây cũng là thứ J2ME Loader gọi là *immediate processing mode* — bên ấy là một
công tắc kèm lời cảnh báo "hành vi của midlet sẽ khó đoán"; ở đây nó không phải
lựa chọn, vì MIDP đã nói rõ hàm này phải làm gì.

## Giai đoạn 51 — bộ bàn phím dùng lại được

Kéo từng phím về đúng chỗ ngón tay mình là việc mất công, và **tay người chơi
không đổi từ game này sang game khác**. Nhưng thứ sắp được lại nằm trong hồ sơ
của *một* game, nên game thứ hai phải sắp lại từ đầu — đó mới là chỗ đáng sửa,
không phải chuyện có thêm một danh sách.

Nay bàn phím sắp xong **cất thành một bộ có tên**, nằm chung cho cả máy, và đặt
lên game nào cũng được. Bộ chỉ mang những gì thuộc về bàn phím: kiểu phím nào
hiện ra, hình phím, độ mờ, bao lâu thì mờ đi, vị trí và cỡ từng phím. Không
mang theo cỡ màn hình hay âm lượng — đó là chuyện của game, không phải của bàn
tay. Bài kiểm tra canh đúng chỗ ấy: vặn âm lượng game xuống 42, đặt một bộ lên,
rồi soi xem 42 có còn nguyên không.

Ba bộ có sẵn, mỗi bộ giải một chuyện có thật — không phải ba biến thể cho vui:

- **Mặc định** — bàn phím đứng yên như cũ.
- **Cầm một tay** — cả cụm hướng dồn về phía ngón cái phải và to lên một chút,
  vì ngón cái với xa thì kém chính xác.
- **Nhẹ nhàng** — phím nhỏ lại, mờ đi sau ba giây không chạm, cho người muốn
  nhìn game nhiều hơn nhìn phím.

Vài chỗ cố ý:

- **Bộ có sẵn không xoá được**, vì xoá xong thì không ai dựng lại được nó; sửa
  và lưu thành bộ của mình thì được.
- **Lưu lại cùng tên thì đè lên**: người ta lưu lại một cái tên là vì bộ cũ đã
  không còn đúng, chứ không phải để có hai dòng giống nhau.
- **Đổi bộ khi đang chơi ăn ngay dưới tay**, không phải mở lại game: đây là thứ
  người ta thử đi thử lại cho vừa ngón.
- **Bỏ dấu trước khi đặt mã bộ.** Hàm rút gọn tên coi chữ có dấu là dấu ngăn,
  nên "Tay tôi" thành `tay-t-i` — một cái tên không ai gõ lại được.

Cầu nối: `keypadLayoutsJson`, `saveKeypadLayout`, `applyKeypadLayout`,
`deleteKeypadLayout`.

Đây là thứ J2ME Loader gọi là *button layouts* — bên ấy lưu được bố cục riêng
cạnh những bố cục dựng sẵn, và lý do giống hệt: bàn tay chỉ có một, còn game
thì nhiều.

## Giai đoạn 52 — chỗ vẽ tự bắt chữ tràn khung

Chữ lọt ra khỏi khung là lỗi đã phải sửa nhiều lần, và lần nào cũng chỉ lộ ra
khi có người phóng to tấm ảnh chụp lên nhìn. Sửa từng chỗ thì hết chỗ này lại
ra chỗ khác, vì nguyên do luôn giống nhau: một con số chiều cao gõ tay không
theo kịp nội dung bên trong nó.

Nên lần này không sửa một chỗ nào cả, mà bắt chính chỗ vẽ tự khai. `Ui` nhớ lại
mọi cái khung nó vẽ ra, mọi dòng chữ và mọi cái chip nó đặt xuống; `overflows`
hỏi lại một câu duy nhất — *có cái khung nào bọc trọn vệt mực này không?* Không
có thì `Preview` ném ngay tại chỗ ghi ảnh, kèm tên ảnh, câu chữ và số điểm bị
lòi ra.

Vài chỗ cố ý:

- **Hỏi "có khung nào bọc trọn" chứ không hỏi "khung nhỏ nhất là khung nào".**
  Một dòng chữ hay nằm đè lên cái ô vuông nhỏ vẽ biểu tượng của tệp; cái ô đó
  không phải chỗ chứa nó, và hỏi theo lối kia thì mọi dòng như vậy đều bị kêu
  oan.
- **Chip cũng bị soi như chữ.** Chip rộng hơn chữ bên trong, nên một hàng chip
  vừa chữ vẫn có thể lòi cái vệt tròn ra ngoài viền — đúng thứ vừa xảy ra ở
  hàng bộ bàn phím.
- **Khung con phải nằm gọn trong khung cha**, cùng một phép kiểm.
- **Hàng chip tự xuống dòng.** Trước đây chip nào không vừa thì bị lặng lẽ bỏ
  đi: khung không tràn, nhưng bộ bàn phím thứ tư thì mất tăm. Giờ nó đo trước
  bề ngang, xuống dòng khi hết chỗ, rồi lấy số hàng đó tính chiều cao khung.

Phép kiểm nằm trong bộ test (`OverflowTest`) và chạy qua mọi màn hình xem
trước, cả nền sáng lẫn nền tối. Màn hình thêm vào sau này được soi luôn, không
ai phải nhớ khai báo thêm.

## Giai đoạn 53 — vòng lặp trên luồng riêng của game

Gần như game J2ME nào cũng có một hình dạng: Canvas mở một `Thread`, luồng ấy
chạy tới khi được bảo dừng, và hai bên nói chuyện qua một cái khoá — bên này
`wait`, bên kia `notify`. Máy ảo vẫn chạy được luồng thật từ đầu, nhưng đi soi
kỹ từng thứ cái vòng lặp ấy cần thì hỏng bốn chỗ, và cả bốn đều hỏng lặng lẽ.

**`currentThread()` dựng một đối tượng mới mỗi lần gọi.** Mọi câu hỏi về luồng
đều trả lời sai mà không kêu tiếng nào: so sánh nào cũng không bằng, tên nào
cũng rỗng. Giờ máy ảo giữ một bảng luồng, và `currentThread()` trả về đúng cái
đối tượng game đã mở. Kèm theo là phần còn thiếu của CLDC 1.1: `Thread(String)`,
`Thread(Runnable, String)`, `getName`, `setName`, `activeCount`, `toString` và
ba mức ưu tiên — trước đây gọi `getName()` là game chết ngay tại dòng đó.

**`wait()` tỉnh dậy vì cái khoá bận, không phải vì được báo.** Chỗ nằm đợi của
`wait` dùng chung với chỗ giành khoá, nên hễ có luồng nào nhả khoá là bên đang
đợi tỉnh dậy như thể vừa được báo. Đo thật: hai trăm lần chạm khoá làm bên đợi
dậy **199 lần**, đáng lẽ một. Một vòng lặp game viết theo lối đợi-báo — tức là
gần như mọi vòng lặp game — chạy loạn hết cả. Giờ mỗi đối tượng có một hàng đợi
riêng cho `wait`, và người báo phải cầm hàng đợi ấy mới báo được, nên lời báo
không lọt vào khe giữa hai việc.

**Đồng hồ "người chơi đợi bao lâu" dùng chung cho mọi luồng.** Chỉ cần một
luồng phụ gọi vào máy ảo là đồng hồ bị đặt lại, và luồng đang treo không bao
giờ bị bắt — game treo vĩnh viễn, đúng cái mà giai đoạn 45 đã làm để tránh.
Chỗ này hỏng đúng vào lúc nó cần nhất, vì game nào cũng có luồng phụ. Giờ mỗi
luồng giữ sổ riêng: ngăn xếp, đồng hồ, số lệnh đã chạy. Câu báo cũng gọi tên
luồng đang kẹt, vì cùng một hàm có thể vẫn đang chạy tốt ở luồng khác.

**Luồng game chết thì không ai nghe thấy.** Lỗi trên luồng riêng chỉ được ghi
vào nhật ký: màn hình đứng im, mọi nút vẫn bấm được, và không có gì xảy ra nữa.
Giờ chỗ hỏng được giữ lại và khung hình kế tiếp nhặt lên, nên game chết trên
luồng của nó cũng hiện ra màn hình "vì sao hỏng" như mọi lần chết khác.

Nhìn thấy được: màn **Máy ảo** có bảng **LUỒNG CỦA GAME** — mỗi luồng một dòng,
tên, còn sống hay không, và đang ở trong hàm nào. Cầu nối: `threadsJson`.

Vài chỗ cố ý:

- **Đếm luồng từ bảng của máy ảo, không từ nhóm luồng của máy chủ.** Máy chủ
  chạy luồng riêng của nó — âm thanh, mạng — không phải phần của game, và một
  game đang đếm thợ của mình không được thấy chúng.
- **Đọc trộm ngăn xếp luồng khác thì chỉ đọc một phần tử**, và bỏ qua nếu vừa
  lúc ấy nó đổi: đây là thứ để nhìn, chứ không phải thứ để dựa vào.
- **Sổ của luồng đã tắt được gộp vào tổng rồi bỏ đi**, nên game mở rồi đóng
  nhiều luồng không làm bảng đếm lệnh tụt xuống.

## Giai đoạn 54 — phần thư viện chuẩn còn thiếu

Không có tính năng nào để bắt đầu ở đây cả: chỉ có một câu hỏi — máy ảo còn
thiếu gì của CLDC 1.1? Nên đi rà thật: năm mươi ba lời gọi mà game hay dùng,
mỗi lời gọi một hàm nhỏ, chạy từng cái qua máy ảo. **Mười chín cái chết.**

Chết chứ không phải chạy sai. CLDC không có phản chiếu, không có đường vòng:
game gọi một hàm không có thì dừng ngay tại dòng đó, trước cả khi kịp vẽ gì.
Bốn lớp vắng hẳn:

- **`java.util.Calendar` và `java.util.TimeZone`** — phần thưởng mỗi ngày, cái
  đồng hồ trong góc màn hình, dấu thời gian trên ô lưu, mầm cho bộ sinh số
  ngẫu nhiên: tất cả đi qua `Calendar.getInstance()`.
- **`java.io.Reader`, `Writer`, `InputStreamReader`, `OutputStreamWriter`** —
  cách game đọc chữ trong chính gói của nó: màn chơi, lời thoại, bảng chữ.
- **`java.lang.Short` và `java.lang.Byte`** — game đọc dữ liệu nhị phân của
  chính nó thì dựng cái vỏ ở đây.

Và mười lăm hàm lẻ: `String.regionMatches` (cả hai dạng), `String.intern`,
`String.valueOf(char[],int,int)`, `new String(byte[],String)`,
`StringBuffer.delete`, `StringBuffer.setCharAt`, `Integer.toString(int,int)`,
`Integer.toOctalString`, `Long.toString(long,int)`, `Long.parseLong(String,int)`,
`Math.round` (cả `float` lẫn `double`), `Character.isLowerCase`,
`Character.isUpperCase`, `Vector.lastIndexOf`, `Vector.setSize`,
`Hashtable.contains`.

Vài chỗ cố ý:

- **Lịch tính bằng số học thuần, không mượn lịch của máy chủ.** Phần lõi phải
  dịch được sang iOS. Phép đổi dùng cách đếm ngày từ một mốc dời về đầu tháng
  ba, nên không có chỗ nào phải chia trường hợp năm nhuận.
- **Đây là lịch Gregory suốt dọc**, không có chỗ nhảy sang lịch Julius năm
  1582 như `GregorianCalendar` của Java. Khác nhau chỉ ở những năm trước 1582,
  và không game nào hỏi tới đó.
- **Máy ảo không mang bảng múi giờ của thế giới**, chỉ mang đúng một con số:
  độ lệch chiếc điện thoại đang chạy, giờ mùa hè đã tính sẵn trong đó. Câu
  game hay hỏi là "bây giờ mấy giờ" và câu ấy đúng; câu "tháng bảy sang năm
  lệch bao nhiêu" thì không, và `useDaylightTime()` nói thẳng là không.
  Android và iOS nói múi giờ thật vào qua `setTimeZone`.
- **`Hashtable.contains` hỏi về giá trị, không phải khoá** — chỗ hay lẫn, nên
  làm cho đúng chứ không mượn `containsKey`.
- **`Vector.setSize` lấp chỗ mới bằng `null`**, đúng như lớp ấy hứa: game dựng
  sẵn một mảng chỗ rồi mới điền vào dựa vào đó.

Kiểm bằng cách đối chiếu, không bằng vài mốc chọn tay: lịch được so với lịch
của máy chủ trên **400.000 mốc ngẫu nhiên**, trên bốn múi giờ, cả đọc lẫn đặt
từng trường — sai một ngày trong lịch là thứ không nhìn ra bằng ba phép thử.
Mười chín góc kia được chạy hai lần, một lần trong máy ảo một lần trên máy
chủ, rồi đem hai kết quả ra so, nên phép kiểm không thể trôi theo thời gian.

Ảnh chụp: **36-clock.png** — một game xem giờ, giờ và thứ do máy ảo tính, và
dòng chữ dưới cùng là một tệp trong gói game đọc bằng `InputStreamReader`.

## Giai đoạn 55 — tám phép lật xoay, và lỗi nằm giữa hai phép

Rà tiếp, lần này là MIDP 2.0: mười tám nhóm lời gọi — hình vẽ, chữ, vùng cắt,
màu, nét, phông, ảnh, `Image`, `Font`, `Canvas`, `Display`, `RecordStore`,
`GameCanvas`, `Sprite`, `TiledLayer`, `LayerManager` — chạy hết qua máy ảo.
Mặt này khá hơn CLDC nhiều: chỉ bốn hàm vắng hẳn (`Graphics.getGrayScale`,
`getDisplayColor`, `copyArea` và `Font.isPlain`).

Nhưng có mặt không có nghĩa là đúng. Nên rà tiếp bằng cách vẽ từng hình vào
một tấm 12×12 rồi soi từng điểm ảnh: `drawRect` có bao gồm điểm cuối không,
`fillRect` bề rộng 0 có vẽ gì không, `fillArc` đếm góc từ đâu về đâu, vùng
cắt giao nhau ra sao. Mười sáu hình, tất cả đúng.

**Chỗ sai nằm ở phép lật xoay.** MIDP có tám phép, và bốn phép mang chữ MIRROR
là *lật trước, xoay sau*. Thứ tự ấy có thật: lật rồi xoay chín mươi độ không ra
cùng kết quả với xoay rồi lật. Máy ảo làm ngược thứ tự, và hậu quả đúng bằng
việc **`MIRROR_ROT90` và `MIRROR_ROT270` đổi chỗ cho nhau** — game xin phép
này thì nhận phép kia, con thú quay mặt sang phải hiện ra quay sang trái.

Lỗi này sống được lâu vì bộ kiểm cũ chỉ soi `MIRROR` và `ROT90` — đúng hai
phép không thể sai, vì với chúng thứ tự không quan trọng. Bộ kiểm mới không
chép sẵn kết quả của tám phép: nó dựng lại phép lật và phép xoay chín mươi độ
bằng hai hàm nhỏ độc lập rồi ghép theo đúng cái tên MIDP đặt, nên hai đường
tính không thể sai giống nhau.

Vài chỗ cố ý:

- **`copyArea` chép qua một bản sao**, không chép thẳng: vùng nguồn và vùng
  đích có thể chồng lên nhau, và chép thẳng thì phần chồng bị bôi mất giữa
  chừng. Chép ra ngoài mép tấm vẽ thì kêu, không lặng lẽ bỏ qua.
- **`getGrayScale` trả về độ sáng của màu hiện tại** khi màu ấy không phải một
  mức xám, đúng như máy trắng đen ngày ấy hiện ra.
- **`getDisplayColor` trả lại đúng màu được hỏi.** Game hỏi câu này để tự chọn
  bảng màu cho máy ít màu; ở đây màn hình đủ màu, nên câu trả lời thật là
  "không đổi gì".

Ảnh chụp: **37-flip.png** — cùng một hình dưới cả tám phép. Hình cố ý không
đối xứng, có một chỗ khuyết đỏ ở góc, vì hình đối xứng giấu đúng cái lỗi vừa
bắt được.

## Giai đoạn 56 — nối lại iOS, và ba lỗi tự mình vừa gây ra

Một lượt rà toàn bộ `core/src` tìm được bốn mươi bảy chỗ. Ba chỗ nặng nhất là
do chính hai giai đoạn vừa rồi đưa vào, và một chỗ nữa nằm ở ứng dụng iOS —
nơi không ai nhìn, vì iOS không được biên dịch ở đây.

**Ứng dụng iOS không biên dịch được, ba chỗ.** `setTimeZone` được Swift gọi
suốt từ giai đoạn 54 mà cầu nối chưa hề có hàm ấy. `bridge.press(...)` và
`bridge.release(...)` gọi vào hai hàm tên thật là `pressButton:` và
`releaseButton:`. `bridge.resource(named:inSuite:)` gọi vào `resourceNamed:` —
Swift chỉ tách giới từ ở cuối tên hàm, mà "Named" không phải giới từ.

Ba lỗi biên dịch nằm im được là vì không có Xcode ở đây. Nên bộ kiểm mới
(`BridgeTest`) không cần trình biên dịch: nó đọc tên hàm trong
`MobiCoreBridge.h`, đọc mọi chỗ Swift gọi `bridge.…`, rồi đối chiếu — có tính
đến lối Swift tách giới từ (`openAtPath:` thành `open(atPath:)`). Chỗ thứ ba là
do chính nó tìm ra.

**`copyArea` cộng phép tịnh tiến hai lần.** Chỗ tính điểm đích cộng một lần,
rồi `drawFramebuffer` đi qua `drawPixels` cộng thêm lần nữa. Game cuộn thanh
trạng thái bằng `copyArea` vẽ lệch gấp đôi. Phép kiểm cũ chạy với gốc toạ độ 0
nên không thấy gì — phép kiểm mới dời gốc toạ độ ra rồi mới chép.

**`wait()` lấy lại khoá khi còn đang giữ hàng đợi — treo cứng.** Luồng đang đợi
giữ hàng đợi rồi chờ khoá của đối tượng; luồng đang giữ khoá ấy lại chờ hàng
đợi để báo. Hai bên đứng im nhìn nhau, và chó canh tám giây cũng không sủa
được vì không luồng nào chạy lệnh nào để nó ngó tới. Giờ khoá chỉ lấy lại sau
khi đã buông hàng đợi. Kiểm bằng hai bên chuyền nhau hai trăm món qua một cái
khoá, chạy có hạn giờ — kẹt thì bộ kiểm báo hỏng chứ không đứng luôn.

**Khoá ném sai loại lỗi.** `monitorenter`/`monitorexit` trên null và `wait()`
ngoài khối `synchronized` ném `VmError` — thứ game không bắt được — thay vì
`NullPointerException` và `IllegalMonitorStateException`. Một game khoá sai chỗ
vẫn phải bắt được lỗi của chính nó, chứ không được kéo sập cả máy ảo.

## Giai đoạn 57 — bỏ những thứ không thuộc về một trình giả lập

Sau năm mươi sáu giai đoạn, MobiCore mang nhiều thứ hơn mức cần. Đợt này bỏ đi
ba nhóm, mỗi nhóm xoá đủ bốn mặt: lõi, cầu nối, hai ứng dụng, và bộ kiểm.

**Tua lại, chỉnh tốc độ, liên thanh.** Không thứ nào có trong J2ME Loader và
không thứ nào thuộc về việc giả lập. Chúng ngồi ngay cạnh "Chụp màn hình" và
"Thoát" trong một cái menu người ta mở lúc đang vội. Đồng hồ của máy ảo giờ là
đồng hồ thật, không còn một lớp bọc chỉ để nhân thời gian lên.

**Quay GIF, chia sẻ, cài từ liên kết.** Quay màn chơi kéo theo một bộ mã hoá
GIF 518 dòng và giữ mọi khung hình trong bộ nhớ tới lúc quay xong. Chụp màn
hình và thư viện ảnh giữ nguyên — thư viện giờ chỉ đếm ảnh, vì không còn loại
thứ hai để phân biệt.

**Công cụ còn đúng một trang.** Năm thẻ, bốn trong số đó là dụng cụ của người
viết máy ảo: xem tệp trong gói, bảng theo dõi mạng, mod, trình sửa JAD và RMS
ở dạng byte thô. Còn lại phần vật phẩm game — tìm hai lượt, bảng vật phẩm đã
đặt tên, ô số lượng và nút gửi.

Cùng lúc: màn Công cụ bỏ thanh thẻ dưới đáy cho khớp với hai ứng dụng thật,
màn nhật ký âm thanh (một bản in chẩn đoán) bị bỏ, và hai lớp
`LoopbackSockets`, `LoopbackTransport` — đồ giả lập cho bộ kiểm mà lại nằm
trong mã sản phẩm — chuyển hẳn sang `tests/`.

Vài chỗ cố ý:

- **`NetworkMonitor` giữ nguyên.** Nó luồn qua đường xét quyền của socket chứ
  không phải một tính năng riêng, và gỡ ra là viết lại phần mạng đang chạy
  tốt. Thứ bỏ đi là *bảng theo dõi*, không phải khả năng nối mạng.
- **Khả năng nối mạng của game giữ nguyên** — `NetworkStack`, `NetworkPolicy`,
  `HttpTransport`, `SocketTransport`, `RealSockets`.

Tổng cộng khoảng 2.600 dòng ít đi, và bộ kiểm vẫn xanh: 37 bộ, 1503 phép kiểm.

## Giai đoạn 58 — chọn vật phẩm rồi mới gõ số lượng

Bảng vật phẩm có ba bước: chọn một thứ, gõ số lượng, gửi. Hai ứng dụng điện
thoại làm đúng như vậy từ đầu — nhưng **ảnh xem trước thì không kể chuyện đó**:
nó vẽ thẻ "GỬI VÀO GAME" với một cái tên gõ cứng và một danh sách không hàng
nào được chọn, nên nhìn vào thì tưởng công cụ bắt gõ số lượng trước khi biết gõ
cho cái gì. Ảnh chụp là thứ người ta nhìn để đánh giá, nên nó phải kể đúng.

Giờ ảnh vẽ đủ ba bước: hàng đang chọn có nền riêng, tên đổi màu và một dấu
tích; thẻ gửi lấy tên từ chính vật phẩm ấy; ô số lượng để trống kèm dòng
"nhiều nhất N". Chưa chọn gì thì thẻ nói "Chọn một vật phẩm ở trên đã."

**Và một lỗi thật lộ ra khi đi hỏi cho kỹ: sửa phần lưu trong lúc game đang
chạy thì số vừa gửi biến mất, không một lời nào.** Game đang chạy giữ phần lưu
trong bộ nhớ của nó và ghi đè cả tệp khi thoát, nên ghi thẳng xuống đĩa lúc ấy
là ghi vào chỗ sắp bị xoá. Cả ba đường ghi — `sendItem`, `setSaveValue`,
`setAllSaveValues` — đều chỉ trả về một lá cờ `restartNeeded`, tức là **kể lại
chuyện đã hỏng thay vì không để nó hỏng**.

Giờ cả ba đóng game lại trước khi ghi, có lưu trạng thái nên mở ra là chơi tiếp
đúng chỗ cũ, và nói ra là đã phải đóng (`closedGame`). Phép kiểm mở game rồi
gửi mà **không** đóng, rồi mở lại đọc số — trước khi sửa thì phép kiểm này
hỏng.

Vá luôn mấy chỗ hai ứng dụng lệch nhau:

- **Android: thẻ gửi còn treo khi vật phẩm đang chọn bị ô tìm kiếm lọc mất** —
  cổng chỉ hỏi "đã chọn gì chưa", nên gõ tìm là ra một thẻ không tên mà vẫn gửi
  được vào thứ không nhìn thấy. Giờ tra ra vật phẩm trước rồi mới mở thẻ.
- **Android: nút "Gửi" là một dòng chữ có thể bấm**, không phải nút thật; và
  cái `Spacer` giữa ô số lượng với nút đặt sai trục nên không tạo khoảng cách
  nào. Gõ số lượng rỗng rồi bấm thì không có gì xảy ra, không một chữ nào.
- **iOS: ô số lượng không lọc chữ** — bàn phím số chỉ là gợi ý, bàn phím ngoài
  và một cú dán vẫn đưa chữ vào được.
- **Cả hai: `note` dùng chung cho luồng gửi lẫn luồng tìm**, nên kết quả của
  việc này xoá kết quả của việc kia. Tách thành hai.
- **iOS: đổi game mà không xoá vật phẩm đang chọn, số lượng đang gõ dở và lời
  nhắn cũ.**
- **Cả hai: kiểm mức tối đa ngay tại chỗ**, để câu từ chối nói được "nhiều
  nhất N" mà không phải đi một vòng xuống lõi.

Bộ kiểm chữ tràn khung của giai đoạn 52 bắt được ngay dòng chú thích mới quá
dài dưới ô số lượng, trước khi kịp nhìn ảnh.

## Giai đoạn 59 — hộp thoại đóng được

`display.setCurrent(alert, mànHìnhKếTiếp)` là một trong những câu lệnh MIDP
được gõ nhiều nhất, và **máy ảo không có hàm ấy**: game nào hiện hộp thoại đầu
tiên theo lối chuẩn là `NoSuchMethodError` ngay tại dòng đó, không đường vòng,
không phản chiếu, chết hẳn. `setCurrentItem(Item)` cũng thiếu.

Nặng hơn: `Alert.setTimeout` cất con số rồi bỏ đó — **không chỗ nào đọc**. Một
hộp thoại hẹn giờ mà không có lệnh nào của riêng nó thì không phím nào đóng
được: game treo vĩnh viễn ở màn hình ấy. Đúng cái MIDP cấm, vì MIDP nói một
alert luôn phải có đường ra.

Giờ:

- `Display.setCurrent(Alert, Displayable)` hiện hộp thoại và **nhớ màn hình
  phải quay về**; gọi `setCurrent(alert)` trơn thì quay về màn hình đang hiện
  trước đó. `setCurrentItem(Item)` chuyển sang màn hình chứa item — `Item` giờ
  giữ một liên kết ngược tới Form sở hữu nó, đặt ở mọi chỗ thêm/chèn/thay và gỡ
  khi xoá.
- **Hết giờ thì tự đóng**, đếm theo đồng hồ máy ảo, móc vào `renderFrame` ngay
  cạnh nhịp hẹn giờ đã có sẵn.
- **Hộp thoại không có lệnh nào được máy phát cho một lệnh "Xong"** trên phím
  mềm phải — và dải phím mềm được vẽ ra cho nó, vì `hasSoftKeys()` trước đây
  chỉ hỏi "game có lệnh nào không". Một cái nhãn không ai nhìn thấy thì không
  phải đường ra: ảnh `12b-alert-countdown.png` là chỗ nhìn ra điều đó.

Phép kiểm dựng đúng ca khó — hộp thoại **hẹn giờ, không lệnh nào, có hẹn màn
hình kế tiếp** — rồi hỏi ba câu: hết giờ có tự đóng không, đóng xong có về đúng
chỗ đã hẹn (chứ không phải chỗ nó được gọi lên) không, và phím mềm phải có đóng
được không. Phá lại từng chỗ sửa một để chắc phép kiểm cắn: bỏ nhịp đếm giờ,
bỏ màn hình đã hẹn, bỏ đường ra trên phím mềm, bỏ dải phím mềm — mỗi lần một
câu hỏng, đúng câu tương ứng.
