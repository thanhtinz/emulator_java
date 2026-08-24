package com.mobicore.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mobicore.app.R
import com.mobicore.app.data.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Top level destinations, mirroring the product's information architecture. */
private enum class Tab(val labelRes: Int) {
    HOME(R.string.tab_home),
    LIBRARY(R.string.tab_library),
    TOOLS(R.string.tab_tools),
    SETTINGS(R.string.tab_settings),
}

/**
 * Where the user is. Detail, settings and the emulator are pushed on top of a
 * tab rather than being tabs themselves.
 */
sealed interface Route {
    data object Tabs : Route
    data class Detail(val suiteId: String) : Route
    data class GameSettings(val suiteId: String) : Route
    data class Emulator(val suiteId: String) : Route
    data class Saves(val suiteId: String) : Route
}

@Composable
fun MobiCoreApp(library: LibraryRepository, filesDir: String) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var route by remember { mutableStateOf<Route>(Route.Tabs) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val games by library.games.collectAsState()
    val profiles by library.profiles.collectAsState()

    // Importing goes through the storage access framework: MobiCore never asks
    // for broad filesystem permission, as the security section requires.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val payloads = uris.mapNotNull { uri ->
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?.let { bytes -> uri.toString() to bytes }
                    }
                    val jar = payloads.firstOrNull { looksLikeJar(it.second) }
                        ?: error("Hãy chọn tệp .jar, kèm theo .jad nếu có")
                    val jad = payloads.firstOrNull { it !== jar }?.second
                    library.importSuite(jar.second, jad)
                }.fold(
                    onSuccess = { "Đã cài ${it.title()}" },
                    onFailure = { "Nhập thất bại: ${it.message}" },
                )
            }
            snackbar.showSnackbar(message)
        }
    }

    when (val current = route) {
        is Route.Emulator -> {
            EmulatorScreen(
                library = library,
                filesDir = filesDir,
                suiteId = current.suiteId,
                onExit = { route = Route.Detail(current.suiteId) },
            )
            return
        }

        is Route.Detail -> {
            GameDetailScreen(
                library = library,
                suiteId = current.suiteId,
                onBack = { route = Route.Tabs },
                onPlay = { route = Route.Emulator(current.suiteId) },
                onSettings = { route = Route.GameSettings(current.suiteId) },
                onSaves = { route = Route.Saves(current.suiteId) },
            )
            return
        }

        is Route.GameSettings -> {
            GameSettingsScreen(
                library = library,
                suiteId = current.suiteId,
                onBack = { route = Route.Detail(current.suiteId) },
            )
            return
        }

        is Route.Saves -> {
            SavesScreen(
                library = library,
                suiteId = current.suiteId,
                onBack = { route = Route.Detail(current.suiteId) },
            )
            return
        }

        Route.Tabs -> Unit
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(iconFor(entry), contentDescription = null) },
                        label = { Text(stringResource(entry.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    library = library,
                    games = games,
                    profiles = profiles,
                    onOpen = { route = Route.Detail(it) },
                    onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
                )

                Tab.LIBRARY -> LibraryScreen(
                    library = library,
                    games = games,
                    profiles = profiles,
                    onOpen = { route = Route.Detail(it) },
                    onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
                )

                Tab.TOOLS -> ToolsScreen(library = library, games = games)

                Tab.SETTINGS -> SettingsScreen(library = library, games = games)
            }
        }
    }
}

@Composable
private fun iconFor(tab: Tab) = when (tab) {
    Tab.HOME -> Icons.Filled.Home
    Tab.LIBRARY -> Icons.Filled.VideogameAsset
    Tab.TOOLS -> Icons.Filled.Build
    Tab.SETTINGS -> Icons.Filled.Settings
}

/** JAR files start with the ZIP local file header; JAD files are plain text. */
private fun looksLikeJar(bytes: ByteArray): Boolean =
    bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

private val IMPORT_MIME_TYPES = arrayOf(
    "application/java-archive",
    "text/vnd.sun.j2me.app-descriptor",
    "application/octet-stream",
    "text/plain",
)
