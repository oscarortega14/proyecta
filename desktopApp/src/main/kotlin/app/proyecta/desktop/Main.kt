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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val discovery = DiscoveryResponder().also { runCatching { it.start() } }
    val themes = ThemeStore(File(library.baseDir, "tema.json"))

    application {
        val state by presenter.state.collectAsState()
        val catalog by library.catalog.collectAsState()
        val plan by commands.plan.state.collectAsState()
        var projectorOn by remember { mutableStateOf(true) }
        var editing by remember { mutableStateOf<EditRequest?>(null) }
        var backgroundsOpen by remember { mutableStateOf(false) }
        // Firewall de Windows: se revisa al abrir; si falta la regla se ofrece crearla con un clic.
        var firewallOk by remember { mutableStateOf<Boolean?>(null) }
        var askFirewall by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            val ok = withContext(Dispatchers.IO) { FirewallSetup.ruleExists() }
            firewallOk = ok
            askFirewall = !ok
        }
        val allowFirewall: () -> Unit = {
            askFirewall = false
            scope.launch { firewallOk = withContext(Dispatchers.IO) { FirewallSetup.requestRule() } }
        }
        val theme by themes.theme.collectAsState()
        val look = Look(theme, backgroundFile = { name -> library.backgroundFile(name) })

        ProvideLook(look) {
        Window(
            onCloseRequest = { server.stop(); discovery.stop(); exitApplication() },
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
                    firewallOk = firewallOk,
                    onAllowFirewall = allowFirewall,
                    onImport = { importSongs(library) },
                    onExport = { exportSongs(library) },
                    searchBible = { q -> library.bible?.search(q) },
                )
                if (askFirewall) {
                    AlertDialog(
                        onDismissRequest = { askFirewall = false },
                        title = { Text("Conectar tablets y celulares") },
                        text = {
                            Text(
                                "Para que la tablet, los celulares y la vista de escenario se conecten a Proyecta, " +
                                    "Windows necesita dar permiso una sola vez. Al presionar \"Permitir\" aparecerá " +
                                    "la ventana de Windows: elige \"Sí\".",
                            )
                        },
                        confirmButton = { Button(allowFirewall) { Text("Permitir") } },
                        dismissButton = { TextButton({ askFirewall = false }) { Text("Ahora no") } },
                    )
                }
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
        // ¿La ventana se dibuja con la tarjeta gráfica? Así los fondos animados van fluidos y livianos.
        LaunchedEffect(Unit) { RenderInfo.hardware = !window.renderApi.name.startsWith("SOFTWARE") }
        ProjectorScreen(state)
    }
}

/** Elegir archivos .txt o .zip (o varios) e importarlos a la biblioteca. */
private fun importSongs(library: Library): String? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Importar canciones (.txt o .zip)", java.awt.FileDialog.LOAD).apply {
        isMultipleMode = true
        setFilenameFilter { _, name -> name.endsWith(".txt", true) || name.endsWith(".zip", true) }
        file = "*.txt;*.zip"
        isVisible = true
    }
    val files = dialog.files?.toList().orEmpty()
    if (files.isEmpty()) return null
    val r = runCatching { library.importSongs(files) }.getOrElse { return "No pude importar: ${it.message}" }
    return buildString {
        append("Importadas: ${r.added}")
        if (r.skipped > 0) append(" · ya estaban: ${r.skipped}")
        if (r.renamed > 0) append(" · con nombre nuevo (texto distinto): ${r.renamed}")
    }
}

/** Guardar todas las canciones en un .zip (respaldo o para otro PC). */
private fun exportSongs(library: Library): String? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Exportar canciones", java.awt.FileDialog.SAVE).apply {
        file = "canciones-proyecta.zip"
        isVisible = true
    }
    val name = dialog.file ?: return null
    val target = java.io.File(dialog.directory, if (name.endsWith(".zip", true)) name else "$name.zip")
    val n = runCatching { library.exportSongs(target) }.getOrElse { return "No pude exportar: ${it.message}" }
    return "Exportadas $n canciones a ${target.absolutePath}"
}
