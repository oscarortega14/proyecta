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
    val presenter = Presenter(library)
    val server = RemoteServer(presenter, library)
    val serverError = runCatching { server.start() }.exceptionOrNull()?.let {
        "No pude abrir el puerto ${server.port} para el control remoto: ${it.message}"
    }
    val addresses = RemoteServer.localAddresses()

    application {
        val state by presenter.state.collectAsState()
        val catalog by library.catalog.collectAsState()
        var projectorOn by remember { mutableStateOf(true) }
        var editing by remember { mutableStateOf<EditRequest?>(null) }

        Window(
            onCloseRequest = { server.stop(); exitApplication() },
            title = "Proyecta — Operador",
            state = rememberWindowState(size = DpSize(1280.dp, 780.dp)),
            onPreviewKeyEvent = { handleKey(it, presenter) },
        ) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                OperatorScreen(
                    state = state,
                    catalog = catalog,
                    addresses = addresses,
                    port = server.port,
                    serverError = serverError,
                    projectorOn = projectorOn,
                    onToggleProjector = { projectorOn = !projectorOn },
                    onCommand = presenter::handle,
                    onReload = { library.reload() },
                    onOpenFolder = { runCatching { Desktop.getDesktop().open(library.baseDir) } },
                    onEdit = { id -> editing = EditRequest(id) },
                )
            }
        }

        editing?.let { req ->
            SongEditorWindow(req, library, presenter, onClose = { editing = null })
        }

        if (projectorOn) {
            ProjectorWindow(state, onClose = { projectorOn = false }, onKey = { handleKey(it, presenter) })
        }
    }
}

/** Atajos pensados para los "clickers" de presentación, que envían Av Pág / Re Pág. */
private fun handleKey(e: KeyEvent, presenter: Presenter): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    return when {
        e.key == Key.PageDown -> { presenter.handle(ClientMessage.Next); true }
        e.key == Key.PageUp -> { presenter.handle(ClientMessage.Prev); true }
        e.isCtrlPressed && e.key == Key.B -> { presenter.handle(ClientMessage.SetBlank(!presenter.state.value.blank)); true }
        else -> false
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
