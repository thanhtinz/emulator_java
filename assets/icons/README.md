# Biểu tượng

Toàn bộ tệp `.svg` trong thư mục này là **Material Symbols** của Google, lấy từ
kho `google/material-design-icons` (bản `materialicons`, khung 24×24), phát hành
theo giấy phép Apache 2.0 — xem `LICENSE.txt`.

Giao diện không tự vẽ biểu tượng nào. Cùng một bộ này được dùng ở cả ba nơi:

- **Android** vẽ trực tiếp qua `androidx.compose.material.icons.Icons.Filled`.
- **iOS** dùng SF Symbols tương ứng (`star`, `square.and.arrow.down`, …).
- **Bản xem trước trên máy tính** không có sẵn bộ nào, nên `codegen/IconGen.java`
  đọc các tệp `.svg` ở đây, tô ở kích thước 64×64 có khử răng cưa rồi sinh ra
  `tools/src/com/mobicore/tools/ui/IconData.java`. Tệp sinh ra được commit,
  nhờ vậy khi build không cần AWT và cũng không cần bộ phân tích SVG.

Thêm biểu tượng mới:

```
curl -o assets/icons/<tên>.svg \
  https://raw.githubusercontent.com/google/material-design-icons/master/src/<nhóm>/<tên>/materialicons/24px.svg
javac -d build/codegen codegen/IconGen.java
java -cp build/codegen IconGen assets/icons tools/src/com/mobicore/tools/ui/IconData.java
```
