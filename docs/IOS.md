# Ứng dụng iOS

Module `ios/` là vỏ SwiftUI chạy **cùng một emulator core** với Android. Core
là Java thuần, được dịch sang Objective-C bằng
[J2ObjC](https://developers.google.com/j2objc).

## Vì sao dịch mã thay vì viết lại

Viết lại một trình thông dịch JVM lần thứ hai bằng Swift nghĩa là hai bản
runtime, hai tập lỗi tương thích, và một game chạy đúng trên Android nhưng sai
trên iOS. Core được viết có chủ đích để dịch được: không phụ thuộc thư viện
ngoài, không dùng API JDK nào ngoài `java.lang`, `java.util`, `java.io` và
`java.util.zip` — đúng phần mà runtime của J2ObjC cung cấp.

## Build

```bash
brew install j2objc xcodegen
cd ios
./build-core.sh          # dịch core/src -> ios/Generated
xcodegen generate        # sinh MobiCore.xcodeproj
open MobiCore.xcodeproj
```

> Môi trường CI dựng repo này chạy Linux và không có Xcode/J2ObjC, nên **app
> iOS chưa được biên dịch tại đây**. Phần Java mà nó gọi (`MobiCoreFacade`) thì
> đã có test đầy đủ: xem suite `Bridge facade` trong `./build.sh test`.

## Lớp cầu nối

Toàn bộ bề mặt giữa Swift và core chỉ có **một** lớp Java:
`com.mobicore.core.bridge.MobiCoreFacade`. Nó chỉ nhận và trả về `String`,
`byte[]`, `int[]` và số nguyên; mọi cấu trúc đi qua dạng JSON.

Lý do: mỗi kiểu Java đi qua ranh giới sẽ sinh ra một lớp Objective-C mà Swift
phải biết. Giới hạn ở một lớp phẳng khiến cầu nối chỉ còn vài chục selector,
và thay đổi bên trong core không lan ra tầng UI.

```
Swift  ──JSON──▶  MobiCoreBridge (Obj-C)  ──▶  ComMobicoreCoreBridgeMobiCoreFacade (J2ObjC)  ──▶  core
```

`MobiCoreBridge` là nơi duy nhất chạm vào kiểu do J2ObjC sinh ra; Swift chỉ
thấy `NSString`, `NSData` và `CGImage`.

## Bản đồ màn hình

| Màn hình | File | Nội dung |
| --- | --- | --- |
| Home | `MobiCore/Views/HomeView.swift` | Recently played, Favourites, All games |
| Library | `MobiCore/Views/LibraryView.swift` | Tìm kiếm + sắp xếp |
| Game Detail | `MobiCore/Views/GameDetailView.swift` | Metadata, Play, Settings, Saves, gỡ cài |
| Emulator | `MobiCore/Views/EmulatorView.swift` | Khung hình + keypad ảo |
| Game settings | `MobiCore/Views/GameSettingsView.swift` | Device, scaling, FPS, âm lượng, input, network |
| Saves | `MobiCore/Views/SavesView.swift` | RecordStore, backup, restore, reset |
| Tools | `MobiCore/Views/ToolsView.swift` | Manifest/JAD, MIDlet, class, resource |
| Settings | `MobiCore/Views/SettingsView.swift` | Emulator, storage, bảo mật |

## Các quyết định đáng chú ý

- **MIDlet chạy trên background queue**, chỉ khung hình hoàn chỉnh mới được đưa
  về main actor; `CADisplayLink` điều nhịp hiển thị nên UI không vẽ nhiều hơn
  một lần mỗi chu kỳ màn hình.
- **`interpolation(.none)` + integer scaling**: giữ pixel vuông, không làm mượt.
- **Nút giữ được**: `DragGesture(minimumDistance: 0)` là cách duy nhất đáng tin
  để tách sự kiện nhấn và nhả trong SwiftUI — cần thiết cho `getKeyStates`.
- **Bộ đệm pixel dùng lại** trong `MobiCoreBridge`: một màn 240×320 là 300 KB,
  cấp phát lại 60 lần mỗi giây là lãng phí vô ích.
