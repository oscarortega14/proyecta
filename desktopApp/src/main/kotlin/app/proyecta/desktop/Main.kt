package app.proyecta.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.proyecta.shared.ClientMessage
import app.proyecta.shared.PresentationState
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.File

fun main() {
    val library = Library(File(System.getProperty("user.home"), "Proyecta")).also { it.reload() }
    val commands = Commands.create(library)
    val presenter = commands.presenter
    val server = RemoteServer(commands, library)
    val serverError = runCatching { server.start() }.exceptionOrNull()?.let {
        "No pude abrir el puerto ${server.port} para el control remoto: ${it.message}"
    }
    val addresses = RemoteServer.localAddresses()
    val themes = ThemeStore(File(library.baseDir, "tema.json"))

    application {
        val state by presenter.state.collectAsState()
        val catalog by library.catalog.collectAsState()
        val plan by commands.plan.state.collectAsState()
        var projectorOn by remember { mutableStateOf(true) }
        var editing by remember { mutableStateOf<EditRequest?>(null) }
        var backgroundsOpen by remember { mutableStateOf(false) }
        val theme by themes.theme.collectAsState()
        val look = Look(theme) { name -> library.backgroundFile(name) }

        ProvideLook(look) {
        Window(
            onCloseRequest = { server.stop(); exitApplication() },
            title = "Proyecta — Operador",
            state = rememberWindowState(size = DpSize(1440.dp, 820.dp)),
            onPreviewKeyEvent = { handleShortcut(it, commands) },
        ) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                OperatorScreen(
                    state = state,
                    catalog = catalog,
                    plan = plan,
                    addresses = addresses,
                    port = server.port,
                    serverError = serverError,
                    projectorOn = projectorOn,
                    onToggleProjector = { projectorOn = !projectorOn },
                    onCommand = commands::handle,
                    onReload = { library.reload() },
                    onOpenFolder = { runCatching { Desktop.getDesktop().open(library.baseDir) } },
                    onEdit = { id -> editing = EditRequest(id) },
                    onOpenBackgrounds = { backgroundsOpen = true },
                    searchBible = { q -> library.bible?.search(q) },
                )
            }
        }

        if (backgroundsOpen) {
            BackgroundsWindow(library, themes, presenter, onClose = { backgroundsOpen = false })
        }

        editing?.let { req ->
            SongEditorWindow(req, library, presenter, onClose = { editing = null })
        }

        if (projectorOn) {
            ProjectorWindow(state, onClose = { projectorOn = false }, onKey = { handleShortcut(it, commands) })
        }
        } // ProvideLook
    }
}


/**
 * Si hay un segundo monitor (el videobeam), la ventana ocupa toda esa pantalla sin bordes.
 * Si solo hay uno, se abre como ventana normal para poder probar.
 */
@Composable
private fun ApplicationScope.ProjectorWindow(
    state: PresentationState,
    onClose: () -> Unit,
    onKey: (KeyEvent) -> Boolean,
) {
    val bounds = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            .getOrNull(1)?.defaultConfiguration?.bounds
    }
    val windowState = rememberWindowState(
        position = bounds?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
        size = bounds?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(960.dp, 540.dp),
    )
    Window(
        onCloseRequest = onClose,
        title = "Proyecta — Proyección",
        state = windowState,
        undecorated = bounds != null,
        alwaysOnTop = bounds != null,
        focusable = bounds == null,
        onPreviewKeyEvent = onKey,
    ) {
        ProjectorScreen(state)
    }
}
