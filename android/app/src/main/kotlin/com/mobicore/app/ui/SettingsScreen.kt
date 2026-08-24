package com.mobicore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicore.app.data.LibraryRepository
import com.mobicore.core.library.LibraryEntry
import com.mobicore.core.storage.StorageLayout

/** Application-wide settings and storage information. */
@Composable
fun SettingsScreen(library: LibraryRepository, games: List<LibraryEntry>) {
    val layout = remember { library.storageLayout() }
    val totalBytes = remember(games) { games.sumOf { it.jarSize() } }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Cài đặt", color = MobiColors.Text, fontSize = 28.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        }

        item {
            SectionCard(title = "BỘ GIẢ LẬP") {
                Column {
                    FieldRow("Cấu hình", "CLDC 1.0 / 1.1")
                    FieldRow("Hồ sơ", "MIDP 1.0 / 2.0")
                    FieldRow("Kết xuất", "Điểm gần nhất, phóng bội số nguyên")
                }
            }
        }

        item {
            SectionCard(title = "LƯU TRỮ") {
                Column {
                    FieldRow("Trò chơi đã cài", games.size.toString())
                    FieldRow("Dung lượng bộ cài", formatBytes(totalBytes))
                    FieldRow("Thư mục gốc", layout.root())
                    Spacer(Modifier.height(6.dp))
                    StorageLayout.TOP_LEVEL.forEach { directory ->
                        Text(
                            text = "MobiCore/$directory",
                            color = MobiColors.TextDim,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "BẢO MẬT") {
                Column {
                    FieldRow("Vùng cách ly", "Mỗi trò chơi một thư mục riêng")
                    FieldRow("Truy cập tệp", "Chỉ khi nhập, không xin quyền rộng")
                    FieldRow("Mạng", "Tắt cho tới khi hồ sơ trò chơi cho phép")
                }
            }
        }

        item {
            SectionCard(title = "GIỚI THIỆU") {
                Column {
                    FieldRow("MobiCore", "1.0")
                    Text(
                        "Nền tảng chơi game J2ME: chạy, quản lý và tuỳ biến game Java ME "
                            + "trên thiết bị hiện đại.",
                        color = MobiColors.TextDim,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
