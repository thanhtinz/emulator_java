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
