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
import java.io.File

fun main() {
    val baseDir = File(System.getProperty("user.home"), "Proyecta")
    val settingsStore = SettingsStore(File(baseDir, "ajustes.json"))
    val library = Library(baseDir).also { it.preferredBible = settingsStore.value.bible; it.reload() }
    val commands = Commands.create(library)
    val presenter = commands.presenter
    // Clave para controlar desde la red: se crea sola la primera vez (se puede cambiar o quitar).
    if (settingsStore.value.pin == null) settingsStore.update { it.copy(pin = Settings.randomPin()) }
    val server = RemoteServer(commands, library, pin = settingsStore.pin)
    val backups = Backups(baseDir, settingsStore)
    val serverError = runCatching { server.start() }.exceptionOrNull()?.let {
        "No pude abrir el puerto ${server.port} para el control remoto: ${it.message}"
    }
    val discovery = DiscoveryResponder().also { runCatching { it.start() } }
    val themes = ThemeStore(File(library.baseDir, "tema.json"))

    application {
        val state by presenter.state.collectAsState()
        val catalog by library.catalog.collectAsState()
        val plan by commands.plan.state.collectAsState()
        val savedLists by commands.plan.saved.collectAsState()
        var projectorOn by remember { mutableStateOf(true) }
        val settings by settingsStore.settings.collectAsState()
        // Pantallas conectadas: se revisan cada pocos segundos por si enchufan el videobeam después.
        var screens by remember { mutableStateOf(Screens.list()) }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(3000)
                val now = withContext(Dispatchers.IO) { Screens.list() }
                if (now != screens) screens = now
            }
        }
        val projectorScreen = chooseProjectorScreen(screens, settings.projectorScreen)
        // Anuncios copiados a mano a la carpeta: aparecen solos.
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(5000)
                withContext(Dispatchers.IO) { library.refreshAnnouncements() }
            }
        }
        var confirmProjectorOff by remember { mutableStateOf(false) }
        var confirmExit by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<EditRequest?>(null) }
        var backgroundsOpen by remember { mutableStateOf(false) }
        var connectOpen by remember { mutableStateOf(false) }
        var historyOpen by remember { mutableStateOf(false) }
        var backupsOpen by remember { mutableStateOf(false) }
        // Respaldo semanal automático, un rato después de abrir.
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(30_000)
            if (backups.isDue()) withContext(Dispatchers.IO) { runCatching { backups.create() } }
        }
        // Actualizaciones: se pregunta a GitHub un rato después de abrir (sin estorbar el arranque).
        var release by remember { mutableStateOf<Release?>(null) }
        var updateOpen by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(15_000)
            release = Updater.latest()
        }
        val offerUpdate = Updater.shouldOffer(release, Updater.currentVersion, settings.skippedUpdate)
        val historyEntries by commands.history!!.entries.collectAsState()
        val lastSung = remember(historyEntries) { commands.history!!.lastSung() }
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
        val look = Look(theme, backgroundFile = { name -> library.backgroundFile(name) }, mediaFile = { name -> library.announcementFile(name) })

        ProvideLook(look) {
        Window(
            onCloseRequest = { confirmExit = true },
            title = "Proyecta — Operador",
            state = rememberFittedWindowState(DpSize(1440.dp, 820.dp), maximizeIfSmall = true),
            onPreviewKeyEvent = {
                if (isReopenProjectorKey(it)) { projectorOn = true; true } else handleShortcut(it, commands)
            },
        ) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                OperatorScreen(
                    state = state,
                    catalog = catalog,
                    plan = plan,
                    savedLists = savedLists,
                    onDeleteSavedList = { commands.plan.deleteSaved(it) },
                    serverError = serverError,
                    projector = ProjectorControl(
                        on = projectorOn,
                        screens = screens,
                        current = projectorScreen,
                        onTurnOn = { projectorOn = true },
                        onTurnOff = { confirmProjectorOff = true },
                        onChoose = { id -> settingsStore.update { s -> s.copy(projectorScreen = id) }; projectorOn = true },
                    ),
                    onCommand = commands::handle,
                    onReload = { library.reload() },
                    onOpenFolder = { runCatching { Desktop.getDesktop().open(library.baseDir) } },
                    onEdit = { id -> editing = EditRequest(id) },
                    onEditSermon = { id -> editing = EditRequest(id, sermon = true) },
                    onOpenBackgrounds = { backgroundsOpen = true },
                    onOpenConnect = { connectOpen = true },
                    onOpenHistory = { historyOpen = true },
                    updateVersion = release?.version?.takeIf { offerUpdate },
                    onOpenUpdate = { updateOpen = true },
                    onOpenWelcome = { settingsStore.update { it.copy(welcomeDone = false) } },
                    onOpenBackups = { backupsOpen = true },
                    lastSung = lastSung,
                    searchBible = { q -> library.bible?.search(q) },
                    bible = remember(catalog) { library.bible },
                    onSelectBible = { name ->
                        settingsStore.update { it.copy(bible = name) }
                        scope.launch(Dispatchers.IO) { library.selectBible(name) }
                    },
                    firewallOk = firewallOk,
                    onImport = { importSongs(library) },
                    onExport = { exportSongs(library) },
                    onAddAnnouncements = { addAnnouncements(library) },
                    onOpenAnnouncements = { library.announcementsDir.mkdirs(); runCatching { Desktop.getDesktop().open(library.announcementsDir) } },
                )
                // La primera vez: guía corta. Después del aviso del firewall, para no encimar ventanas.
                if (!settings.welcomeDone && !askFirewall) {
                    WelcomeDialog(onDone = { settingsStore.update { it.copy(welcomeDone = true) } })
                }
                if (updateOpen && offerUpdate) {
                    UpdateDialog(
                        release!!,
                        onLater = { updateOpen = false },
                        onSkip = { updateOpen = false; settingsStore.update { it.copy(skippedUpdate = release!!.version) } },
                        onExitForInstall = { server.stop(); discovery.stop(); exitApplication() },
                    )
                }
                if (backupsOpen) {
                    BackupsDialog(
                        backups, settings,
                        onChooseCopyDir = { d -> settingsStore.update { it.copy(backupCopyDir = d) } },
                        onRestored = { library.reload(); commands.plan.reloadFromDisk(); commands.history?.reloadFromDisk() },
                        onClose = { backupsOpen = false },
                    )
                }
                if (confirmProjectorOff) {
                    AlertDialog(
                        onDismissRequest = { confirmProjectorOff = false },
                        title = { Text("¿Apagar la proyección?") },
                        text = { Text("La congregación dejará de ver la letra. Para volver a encenderla presiona F5.") },
                        confirmButton = { TextButton({ confirmProjectorOff = false; projectorOn = false }) { Text("Apagar") } },
                        dismissButton = { Button({ confirmProjectorOff = false }) { Text("Seguir proyectando") } },
                    )
                }
                if (confirmExit) {
                    AlertDialog(
                        onDismissRequest = { confirmExit = false },
                        title = { Text("¿Cerrar Proyecta?") },
                        text = { Text("Se apaga la proyección y se desconectan las tablets y celulares.") },
                        confirmButton = {
                            TextButton({ server.stop(); discovery.stop(); exitApplication() }) { Text("Cerrar Proyecta") }
                        },
                        dismissButton = { Button({ confirmExit = false }) { Text("Seguir usando") } },
                    )
                }
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

        if (connectOpen) {
            ConnectWindow(
                addresses = RemoteServer.localAddresses(),
                port = server.port,
                pcName = discovery.name,
                firewallOk = firewallOk,
                onAllowFirewall = allowFirewall,
                pin = settings.activePin,
                onNewPin = { settingsStore.update { it.copy(pin = Settings.randomPin(), pinOff = false) } },
                onPinOff = { off -> settingsStore.update { it.copy(pinOff = off) } },
                onClose = { connectOpen = false },
            )
        }

        if (historyOpen) {
            HistoryWindow(commands.history!!, onClose = { historyOpen = false })
        }

        if (backgroundsOpen) {
            BackgroundsWindow(library, themes, presenter, onClose = { backgroundsOpen = false })
        }

        editing?.let { req ->
            SongEditorWindow(req, library, presenter, onClose = { editing = null })
        }

        if (projectorOn) {
            // key: al cambiar de pantalla la ventana se vuelve a crear en la nueva posición.
            androidx.compose.runtime.key(projectorScreen) {
                ProjectorWindow(
                    state, projectorScreen,
                    onClose = { confirmProjectorOff = true },
                    onKey = { handleShortcut(it, commands) },
                )
            }
        }
        } // ProvideLook
    }
}


/**
 * En la pantalla elegida (el videobeam) la ventana ocupa toda la pantalla sin bordes.
 * Sin pantalla ([screen] null) se abre como ventana normal para poder probar.
 */
@Composable
private fun ApplicationScope.ProjectorWindow(
    state: PresentationState,
    screen: ScreenInfo?,
    onClose: () -> Unit,
    onKey: (KeyEvent) -> Boolean,
) {
    val windowState = rememberWindowState(
        position = screen?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
        size = screen?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(960.dp, 540.dp),
    )
    Window(
        onCloseRequest = onClose,
        title = "Proyecta — Proyección",
        state = windowState,
        undecorated = screen != null,
        alwaysOnTop = screen != null,
        focusable = screen == null,
        onPreviewKeyEvent = onKey,
    ) {
        // ¿La ventana se dibuja con la tarjeta gráfica? Así los fondos animados van fluidos y livianos.
        LaunchedEffect(Unit) { RenderInfo.hardware = !window.renderApi.name.startsWith("SOFTWARE") }
        ProjectorScreen(state)
    }
}

/** Elegir archivos .txt o .zip (o varios) e importarlos a la biblioteca. */
private fun importSongs(library: Library): String? {
    val dialog = java.awt.FileDialog(
        null as java.awt.Frame?, "Importar canciones: .txt, .zip, OpenLP (.xml) u Holyrics (.json)", java.awt.FileDialog.LOAD,
    ).apply {
        isMultipleMode = true
        setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in Library.IMPORT_EXTENSIONS + "zip" }
        file = "*.txt;*.zip;*.xml;*.json"
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

/** Elegir imágenes o videos y copiarlos a la carpeta de anuncios. */
private fun addAnnouncements(library: Library): String? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Agregar anuncios (imágenes o videos)", java.awt.FileDialog.LOAD).apply {
        isMultipleMode = true
        setFilenameFilter { _, name -> Library.isMediaFile(name) }
        file = (Library.IMAGE_EXTENSIONS + VIDEO_EXTENSIONS).joinToString(";") { "*.$it" }
        isVisible = true
    }
    val files = dialog.files?.toList().orEmpty()
    if (files.isEmpty()) return null
    val n = runCatching { library.addAnnouncements(files) }.getOrElse { return "No pude agregar: ${it.message}" }
    return if (n == 0) "Esos anuncios ya estaban" else "Agregados $n anuncios"
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
