# Ứng dụng Android

Module `android/` là vỏ ứng dụng Kotlin + Jetpack Compose, dùng **trực tiếp**
emulator core ở `core/` (cùng ngôn ngữ JVM nên không cần cầu nối nào).

## Build

```bash
cd android
./gradlew :app:assembleDebug        # hoặc mở thư mục android/ bằng Android Studio
```

Yêu cầu: Android SDK 35, JDK 17, Gradle 8.9+ (AGP 8.7.2, Kotlin 2.0.21).

> Trong môi trường CI dựng repo này, `dl.google.com` bị chặn bởi network
> policy nên **module Android chưa được biên dịch tại đây**. Toàn bộ core thì
> được biên dịch và test đầy đủ (`./build.sh test`).

## Cách core được nhúng

`android/settings.gradle.kts` khai báo module `:core` trỏ thẳng vào
`core/src` ở gốc repository:

```kotlin
project(":core").projectDir = file("core")   // android/core/build.gradle.kts
```

`android/core/build.gradle.kts` đặt `srcDirs = ["../core/src"]`. Nhờ vậy chỉ
tồn tại một bản core duy nhất, dùng chung với iOS.

## Bản đồ màn hình

| Màn hình | File | Nội dung |
| --- | --- | --- |
| Home | `ui/HomeScreen.kt` | Recently played, Favourites, All games |
| Library | `ui/LibraryScreen.kt` | Tìm kiếm, sắp xếp theo tên/mới chơi/nhà phát hành |
| Game Detail | `ui/GameDetailScreen.kt` | Cover, metadata, Play, Settings, Saves, gỡ cài |
| Emulator | `ui/EmulatorScreen.kt` | Khung hình game + keypad ảo + pause/FPS |
| Game settings | `ui/GameSettingsScreen.kt` | Device profile, scaling, FPS, âm lượng, input, network |
| Saves | `ui/SavesScreen.kt` | RecordStore, backup, restore, reset |
| Tools | `ui/ToolsScreen.kt` | Manifest/JAD, MIDlet, class, resource |
| Settings | `ui/SettingsScreen.kt` | Thông tin emulator, storage, bảo mật |

## Các quyết định đáng chú ý

- **MIDlet chạy trên thread riêng** (`emu/EmulatorEngine.kt`). Game loop J2ME
  chặn và ngủ tự do; để nó trên main thread sẽ đóng băng cả ứng dụng. Compose
  được đánh thức bằng một bộ đếm khung hình, bitmap chỉ được chạm dưới khóa.
- **Không làm mượt pixel**: `isFilterBitmap = false` cộng integer scaling mặc
  định — đúng nguyên tắc "pixel-perfect cho game cổ điển".
- **Không xin quyền filesystem rộng**: import đi qua Storage Access Framework.
- **Nút giữ được**: keypad phát riêng press và release vì
  `GameCanvas.getKeyStates` đọc trạng thái phím đang giữ.
- **Không tái tạo Activity khi xoay máy**: một MIDlet đang chạy giữ cả một máy
  ảo, nên `configChanges` xử lý xoay tại chỗ.
