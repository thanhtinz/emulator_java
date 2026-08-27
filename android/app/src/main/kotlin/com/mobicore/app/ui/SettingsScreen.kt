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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
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
        // The bar above already names this page.
        item { Spacer(Modifier.height(4.dp)) }

        item {
            SectionCard(title = "GIAO DIỆN") {
                val theme by library.theme.collectAsState()
                OptionRow(
                    label = "Sáng tối",
                    options = listOf("Sáng", "Tối", "Theo hệ thống"),
                    selected = theme,
                ) { library.setTheme(it) }
            }
        }

        item {
            val presets by library.presets.collectAsState()
            val defaultPreset by library.defaultPreset.collectAsState()
            SectionCard(title = "BỘ CẤU HÌNH MẶC ĐỊNH") {
                Column {
                    if (presets.isEmpty()) {
                        Text(
                            "Lưu một bộ cấu hình trong phần cài đặt của game, rồi chọn ở đây "
                                + "để mọi game nhập vào sau đều dùng bộ đó.",
                            color = MobiColors.TextDim,
                            fontSize = 13.sp,
                        )
                    } else {
                        // "Không dùng" is first and is the default: with
                        // nothing chosen, a new game is configured from what
                        // is inside it, which is right until someone has
                        // decided otherwise for their own phone.
                        OptionRow(
                            label = "Game mới sẽ dùng",
                            options = listOf("Không dùng") + presets,
                            selected = (presets.indexOf(defaultPreset) + 1).coerceAtLeast(0),
                        ) { index ->
                            library.setDefaultPreset(if (index == 0) "" else presets[index - 1])
                        }
                    }
                }
            }
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

/**
 * Carrying everything to the next phone.
 *
 * One file with the games, their settings, what they saved, the save states,
 * the screenshots and the presets in it — the per-game backups are the wrong
 * shape for this, because eighty games would mean eighty transfers.
 */
@Composable
private fun BackupCard(library: LibraryRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            note = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(library.exportLibrary())
                    }
                    "Đã lưu bản sao lưu"
                }.getOrElse { "Lưu thất bại: ${it.message}" }
            }
        }
    }

    val opener = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            note = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: ByteArray(0)
                    library.importLibrary(bytes).summary()
                }.getOrElse { "Khôi phục thất bại: ${it.message}" }
            }
        }
    }

    SectionCard(title = "SAO LƯU TOÀN BỘ") {
        Column {
            Text(
                "Một tệp gồm trò chơi, cấu hình, dữ liệu lưu, ảnh chụp và bộ cấu hình — "
                    + "để mang sang máy khác.",
                color = MobiColors.TextDim,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    "Xuất tệp",
                    color = MobiColors.Accent,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { saver.launch("mobicore-library.mcl") },
                )
                Text(
                    "Khôi phục",
                    color = MobiColors.Accent,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { opener.launch(arrayOf("*/*")) },
                )
            }
            note?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MobiColors.TextDim, fontSize = 12.sp)
            }
        }
    }
}
