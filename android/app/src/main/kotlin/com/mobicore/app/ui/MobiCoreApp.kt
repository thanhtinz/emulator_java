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
            // Everything picked, not just the first: a collection is a folder
            // of games, and importing them one at a time is a reason not to
            // bother. The core pairs each .jad with its .jar, unpacks a zip of
            // games, and reports on every file separately.
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val names = ArrayList<String>()
                    val payloads = ArrayList<ByteArray>()
                    uris.forEach { uri ->
                        val bytes = context.contentResolver.openInputStream(uri)
                            ?.use { it.readBytes() }
                        if (bytes != null) {
                            names.add(displayName(context, uri))
                            payloads.add(bytes)
                        }
                    }
                    library.importMany(names.toTypedArray(), payloads.toTypedArray())
                }.fold(
                    onSuccess = { it.summary() },
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

                Tab.TOOLS -> ToolsScreen(library = library, games = games)

                Tab.SETTINGS -> SettingsScreen(library = library, games = games)
            }
        }
    }
}

@Composable
private fun iconFor(tab: Tab) = when (tab) {
    Tab.HOME -> Icons.Filled.Home
    Tab.TOOLS -> Icons.Filled.Build
    Tab.SETTINGS -> Icons.Filled.Settings
}

/**
 * The file's own name, which is how a descriptor finds its archive.
 *
 * A content URI's last path segment is often an opaque id, so the display
 * name is asked for; without it "game.jad" and "game.jar" cannot be paired.
 */
private fun displayName(context: android.content.Context, uri: android.net.Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (column >= 0 && cursor.moveToFirst()) {
            val name = cursor.getString(column)
            if (!name.isNullOrEmpty()) return name
        }
    }
    return uri.lastPathSegment ?: "tệp"
}

private val IMPORT_MIME_TYPES = arrayOf(
    "application/java-archive",
    "text/vnd.sun.j2me.app-descriptor",
    "application/octet-stream",
    "text/plain",
)
