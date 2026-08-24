# MobiCore — J2ME Game Platform

MobiCore chạy, quản lý và tùy biến game Java ME (J2ME) trên thiết bị hiện đại.
Không chỉ là một trình giả lập: thư viện game, virtual phone, quản lý save/RMS,
remap phím, profile thiết bị, developer tools và lớp networking nằm trong cùng
một ứng dụng.

## Kiến trúc

Emulator core là **pure Java, không phụ thuộc thư viện ngoài**, để một bản
runtime duy nhất phục vụ được mọi nền tảng:

| Lớp | Thư mục | Ghi chú |
| --- | --- | --- |
| Emulator core | `core/` | CLDC/MIDP runtime, JAR/JAD, framebuffer, RMS |
| Desktop tools | `tools/` | Preview harness, sample suite, screenshot |
| Test suite | `tests/` | Bộ test không phụ thuộc framework ngoài |
| Codegen | `codegen/` | Sinh dữ liệu font bitmap (chạy offline) |
| Fixtures | `fixtures/` | MIDlet thật dùng để test (kèm stub API để biên dịch) |
| Android | `android/` | Ứng dụng Kotlin + Jetpack Compose |
| iOS | `ios/` | Ứng dụng SwiftUI + cầu nối J2ObjC |

Android dùng core trực tiếp (cùng ngôn ngữ JVM); iOS dùng core sau khi dịch
bằng J2ObjC. Vì vậy core tránh mọi API riêng của JDK ngoài `java.lang`,
`java.util`, `java.io`, `java.util.zip` và `java.net`.

Một bản runtime duy nhất phục vụ cả hai nền tảng: viết lại trình thông dịch
JVM lần thứ hai bằng Swift đồng nghĩa với hai tập lỗi tương thích và những
game chạy đúng bên này nhưng sai bên kia.

Chi tiết: `docs/ANDROID.md`, `docs/IOS.md`, `docs/ROADMAP.md`.

## Build & test

```bash
./build.sh test                                  # biên dịch + chạy toàn bộ test
./build.sh run com.mobicore.tools.Preview out/    # render screenshot các màn hình
```

Chỉ cần một JDK; không cần mạng, không cần Gradle.

## Bố cục lưu trữ trên máy

```
MobiCore/
├── games/     bộ cài (.jar/.jad/artwork)
├── profiles/  cấu hình theo từng game
├── rms/       RecordStore, sandbox riêng mỗi suite
├── saves/     save state
├── skins/     skin virtual phone
├── mods/      gói mod
├── backups/   bản sao lưu trước khi reset/mod
├── logs/      log console và crash
└── cache/     dữ liệu dẫn xuất, xóa được
```

Mỗi game có sandbox riêng: một game không bao giờ đọc hay ghi đè dữ liệu của
game khác.

## Những gì đã chạy được

- Nạp và chạy **bytecode Java thật**: trình thông dịch phủ tập lệnh CLDC,
  thư viện `java.lang`/`java.io`/`java.util` viết bằng native.
- Thư viện MIDP: `Canvas`, `GameCanvas`, `Graphics`, `Image`, `Font`,
  `Sprite`, `TiledLayer`, `LayerManager`, vòng đời MIDlet.
- Thư viện game, profile theo từng game, RMS lưu vĩnh viễn, backup/restore.
- Lớp mạng có policy theo host và network monitor.
- Modding không đụng tới file gốc; JAD editor, RMS editor, crash report.
- Ứng dụng Android và iOS dựng trên cùng một core.

Bộ test: **368 checks**, trong đó phần thông dịch được kiểm tra vi sai với
chính JVM thật.

## Trạng thái

Xem `docs/ROADMAP.md`.
