package app.proyecta.remote

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import app.proyecta.shared.ClientMessage
import app.proyecta.shared.PresentationState
import app.proyecta.shared.SongSummary
import app.proyecta.shared.search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ScrollableTabRow
import app.proyecta.shared.ServerMessage
import app.proyecta.shared.ServicePlanState
import app.proyecta.shared.PlanKind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton

class MainActivity : ComponentActivity() {
    private lateinit var client: RemoteClient
    /** Dirección recibida por un enlace proyecta://connect?host=… (desde el QR del navegador). */
    private val linkHost = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Acepta proyecta://connect?host=192.168.x.x ; ignora cualquier otra cosa. */
    private fun readLink(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "proyecta" || uri.host != "connect") return
        val host = uri.getQueryParameter("host")?.trim().orEmpty()
        // El control web pasa también la clave que ya tiene, así la app no la pide otra vez.
        if (!host.matches(Regex("""^[0-9A-Za-z.\-:]{1,64}$"""))) return
        uri.getQueryParameter("pin")?.trim()?.takeIf { it.matches(Regex("""\d{4,6}""")) }?.let { pin ->
            // La clave del enlace vale solo para ese mismo computador.
            getSharedPreferences("proyecta", Context.MODE_PRIVATE).edit().putString("pin", pin).putString("pinHost", host).apply()
        }
        linkHost.value = host
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // En pleno culto la pantalla no debe apagarse.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("proyecta", Context.MODE_PRIVATE)
        client = RemoteClient(lifecycleScope)
        // La clave se manda sola solo al computador donde se aceptó.
        // (Las versiones anteriores no guardaban de qué computador era: vale para el que ya estaba guardado.)
        client.savedPin = { host ->
            val pinHost = prefs.getString("pinHost", null) ?: prefs.getString("host", null)
            prefs.getString("pin", null)?.takeIf { pinHost == host }
        }
        client.onPinAccepted = { host, pin -> prefs.edit().putString("pin", pin).putString("pinHost", host).apply() }
        readLink(intent)
        prefs.getString("host", null)?.let { client.connect(it) }
        client.onHostChanged = { host -> prefs.edit().putString("host", host).apply() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                client.errors.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
            }
        }

        // Android 15+ dibuja la app detrás de las barras del sistema: se deja su espacio libre
        // (barra de estado, botones de navegación y teclado) para que nada quede tapado.
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                  Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    var pcName by remember { mutableStateOf(prefs.getString("name", "").orEmpty()) }
                    val link by linkHost.collectAsState()
                    val connect: (FoundServer) -> Unit = { server ->
                        prefs.edit().putString("host", server.host).putString("name", server.name).apply()
                        pcName = server.name
                        client.connect(server.host)
                    }
                    // Enlace desde el navegador: conectarse a ese PC y averiguar su nombre.
                    LaunchedEffect(link) {
                        val host = link ?: return@LaunchedEffect
                        connect(FoundServer(client.pcName(host) ?: host, host))
                        linkHost.value = null
                    }
                    // Conexión guardada de una versión anterior, sin nombre: preguntárselo al PC.
                    LaunchedEffect(Unit) {
                        val host = prefs.getString("host", "").orEmpty()
                        if (pcName.isEmpty() && host.isNotEmpty()) {
                            client.pcName(host)?.let { name ->
                                prefs.edit().putString("name", name).apply()
                                pcName = name
                            }
                        }
                    }
                    RemoteApp(
                        client = client,
                        savedHost = prefs.getString("host", "").orEmpty(),
                        pcName = pcName,
                        linkKey = link,
                        onConnect = connect,
                    )
                  }
                }
            }
        }
    }

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }
}

@Composable
private fun RemoteApp(client: RemoteClient, savedHost: String, pcName: String, linkKey: String?, onConnect: (FoundServer) -> Unit) {
    val status by client.status.collectAsState()
    val state by client.state.collectAsState()
    val songs by client.songs.collectAsState()
    val bibleName by client.bibleName.collectAsState()
    val plan by client.plan.collectAsState()
    val savedLists by client.savedLists.collectAsState()
    val songResults by client.songResults.collectAsState()
    val announcements by client.announcements.collectAsState()
    val sermons by client.sermons.collectAsState()
    val bibleNames by client.bibleNames.collectAsState()
    val secondBible by client.secondBible.collectAsState()
    val bibleResults by client.bibleResults.collectAsState()
    val bibleBooks by client.bibleBooks.collectAsState()
    val bibleChapter by client.bibleChapter.collectAsState()
    // Dónde quedó el explorador de la Biblia (se conserva al cambiar de pestaña).
    var bibleNav by rememberSaveable(stateSaver = BibleNav.Saver) { mutableStateOf(BibleNav()) }
    var editingHost by rememberSaveable { mutableStateOf(savedHost.isEmpty()) }
    // Si llegó un enlace con la dirección, no hace falta buscar.
    LaunchedEffect(linkKey) { if (linkKey != null) editingHost = false }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        StatusBar(status, pcName, showSearch = !editingHost, onEdit = { editingHost = true })
        UpdateBanner()
        // El PC pide clave: se pregunta una vez y queda guardada.
        val auth by client.auth.collectAsState()
        var pinDismissed by remember(auth) { mutableStateOf(false) }
        val needsPin = auth?.let { it.required && !it.ok } == true
        if (needsPin && !editingHost && !pinDismissed) {
            PinDialog(pcName, auth?.message, onDismiss = { pinDismissed = true }) { client.login(it) }
        }
        if (needsPin && !editingHost && pinDismissed) {
            TextButton({ pinDismissed = false }, Modifier.fillMaxWidth()) { Text("Solo mirando · Escribir la clave para controlar") }
        }
        // Ojo: nada de "return" temprano dentro de Column (es inline y rompe la recomposición).
        if (editingHost) {
            SearchScreen(client) { server -> editingHost = false; onConnect(server) }
        } else {
            // Desplazable: en celulares angostos las pestañas no parten las palabras.
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                listOf("En pantalla", "Canciones", "Biblia", "Lista", "Más").forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, maxLines = 1, softWrap = false) })
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                when (tab) {
                    0 -> LiveTab(state, client::send)
                    1 -> SongsTab(songs, songResults, onSearch = { q -> client.send(ClientMessage.SongSearch(q)) }, onAdd = { id -> client.send(ClientMessage.PlanAddSong(id)) }) { id ->
                        client.send(ClientMessage.SelectSong(id)); tab = 0
                    }
                    2 -> BibleTab(
                        bibleName, bibleBooks, bibleChapter, bibleResults, bibleNav,
                        bibleNames = bibleNames, secondBible = secondBible,
                        onSelectBible = { client.send(ClientMessage.SelectBible(it)) },
                        onSelectSecond = { client.send(ClientMessage.SetSecondBible(it)) },
                        onNavigate = { bibleNav = it },
                        onSearch = { q -> client.send(ClientMessage.BibleSearch(q)) },
                        onClearSearch = client::clearBibleResults,
                        onRequestChapter = { book, c -> client.send(ClientMessage.BibleChapter(book, c)) },
                        onAdd = { ref -> client.send(ClientMessage.PlanAddBible(ref)) },
                        onShow = { ref, slide ->
                            client.send(ClientMessage.ShowBible(ref))
                            if (slide > 0) client.send(ClientMessage.GoTo(slide))
                            tab = 0
                        },
                    )
                    3 -> PlanTab(plan, savedLists, state.stageMessage, client::send) { tab = 0 }
                    4 -> MoreTab(state, announcements, sermons, client.serverOffset, client::send, onUpload = client::uploadAnnouncement) { tab = 0 }
                }
            }
            Controls(state, client::send)
        }
    }
}

@Composable
private fun StatusBar(status: ConnectionStatus, pcName: String, showSearch: Boolean, onEdit: () -> Unit) {
    val pc = pcName.ifEmpty { "el computador" }
    val toPc = if (pcName.isEmpty()) "al computador" else "a $pcName"
    val (text, color) = when (status) {
        ConnectionStatus.Idle -> "Sin conectar" to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionStatus.Connecting -> "Conectando $toPc…" to MaterialTheme.colorScheme.tertiary
        is ConnectionStatus.Connected -> "Conectado $toPc" to MaterialTheme.colorScheme.primary
        is ConnectionStatus.Failed -> "Sin conexión con $pc — reintentando" to MaterialTheme.colorScheme.error
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = showSearch, onClick = onEdit).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showSearch) Text("Buscar otro", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SearchScreen(client: RemoteClient, onConnect: (FoundServer) -> Unit) {
    var found by remember { mutableStateOf<List<FoundServer>>(emptyList()) }
    var rounds by remember { mutableIntStateOf(0) }
    var restart by remember { mutableIntStateOf(0) }

    // Busca y vuelve a buscar cada pocos segundos hasta encontrar Proyecta en la red.
    // Si hay un solo computador se conecta sin preguntar.
    LaunchedEffect(restart) {
        rounds = 0
        found = emptyList()
        while (true) {
            val result = client.discover()
            rounds++
            found = result
            if (result.size == 1) { onConnect(result[0]); return@LaunchedEffect }
            if (result.size > 1) return@LaunchedEffect // que la persona elija
            kotlinx.coroutines.delay(2_500)
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (found.isEmpty()) {
            CircularProgressIndicator(Modifier.padding(top = 24.dp))
            Text("Buscando Proyecta…", style = MaterialTheme.typography.headlineSmall)
            if (rounds >= 3) {
                Text(
                    "Revisa que:\n• Proyecta esté abierto en el computador\n• este equipo esté conectado al mismo Wi-Fi",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                )
            }
        } else {
            Text("¿A qué computador te conectas?", style = MaterialTheme.typography.headlineSmall)
            found.forEach { s ->
                Button({ onConnect(s) }, Modifier.fillMaxWidth().height(64.dp)) { Text(s.name, fontSize = 20.sp, maxLines = 1) }
            }
            OutlinedButton({ restart++ }, Modifier.fillMaxWidth()) { Text("Buscar de nuevo") }
        }
    }
}

@Composable
private fun LiveTab(state: PresentationState, send: (ClientMessage) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.index, state.title) { if (state.index >= 0) listState.animateScrollToItem(state.index) }

    Text(
        state.title.ifEmpty { "Nada en pantalla. Elige una canción o una cita." },
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    val labels = state.sectionLabels
    if (labels.isNotEmpty()) {
        val current = state.currentLabel
        LazyRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(labels) { label ->
                FilterChip(
                    selected = label == current,
                    onClick = { state.jumpTarget(label)?.let { send(ClientMessage.GoTo(it)) } },
                    label = { Text(label, fontSize = 16.sp) },
                    modifier = Modifier.height(44.dp),
                )
            }
        }
    }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(state.slides) { i, slide ->
            val selected = i == state.index
            Card(
                Modifier.fillMaxWidth().clickable { send(ClientMessage.GoTo(i)) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Column(Modifier.padding(12.dp)) {
                    if (slide.label.isNotEmpty()) {
                        Text(slide.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(slide.text, fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun SongsTab(
    songs: List<SongSummary>,
    serverResults: ServerMessage.SongResults?,
    onSearch: (String) -> Unit,
    onAdd: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    // Al instante por título; un momento después el computador busca también en la letra.
    LaunchedEffect(query) {
        if (query.trim().length >= 2) { kotlinx.coroutines.delay(250); onSearch(query.trim()) }
    }
    val results = remember(query, songs, serverResults) {
        serverResults?.takeIf { it.query == query.trim() }?.hits
            ?: songs.search(query).map { app.proyecta.shared.SongHit(it.id, it.title, it.firstLine) }
    }
    OutlinedTextField(query, { query = it }, label = { Text("Buscar por título o por la letra") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    LazyColumn(Modifier.padding(top = 8.dp)) {
        items(results, key = { it.id }) { song ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onSelect(song.id) }.padding(vertical = 10.dp)) {
                    Text(song.title, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                    Text(
                        if (song.snippet.isNotEmpty()) "…${song.snippet}…" else song.firstLine,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton({
                    onAdd(song.id)
                    Toast.makeText(context, "Agregada a la lista", Toast.LENGTH_SHORT).show()
                }) { Text("+", fontSize = 20.sp) }
            }
        }
    }
}

@Composable
private fun Controls(state: PresentationState, send: (ClientMessage) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button({ send(ClientMessage.Prev) }, Modifier.weight(1f).height(72.dp)) { Text("◀", fontSize = 26.sp) }
        OutlinedButton(
            { send(ClientMessage.SetBlank(!state.blank)) },
            Modifier.weight(1f).height(72.dp),
            colors = if (state.blank) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer) else ButtonDefaults.outlinedButtonColors(),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) { Text(if (state.blank) "Mostrar" else "Negro", fontSize = 17.sp, maxLines = 1, softWrap = false) }
        Button({ send(ClientMessage.Next) }, Modifier.weight(1.6f).height(72.dp)) { Text("▶", fontSize = 26.sp) }
    }
}

@Composable
private fun PlanTab(plan: ServicePlanState, saved: List<String>, stageMessage: String, send: (ClientMessage) -> Unit, onPlayed: () -> Unit) {
    var msg by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }
    var opening by rememberSaveable { mutableStateOf(false) }
    if (saving) SavePlanDialog(plan.name.ifEmpty { suggestedListName() }, { saving = false }) { name -> saving = false; send(ClientMessage.PlanSave(name)) }
    if (opening) OpenPlanDialog(saved, { opening = false }) { name -> opening = false; send(ClientMessage.PlanOpen(name)) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lista del culto", style = MaterialTheme.typography.titleLarge)
                    if (plan.name.isNotEmpty()) Text(plan.name, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button({ send(ClientMessage.PlanNext); onPlayed() }, enabled = plan.current < plan.items.lastIndex) {
                    Text("Siguiente ▶")
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ saving = true }, Modifier.weight(1f), enabled = plan.items.isNotEmpty()) { Text("Guardar") }
                OutlinedButton({ opening = true }, Modifier.weight(1f), enabled = saved.isNotEmpty()) { Text("Abrir (${saved.size})") }
            }
            if (plan.items.isEmpty()) {
                Text(
                    "Vacía. Agrega canciones con \"+\" o citas desde Biblia.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        itemsIndexed(plan.items) { i, item ->
            val selected = i == plan.current
            Card(
                Modifier.fillMaxWidth().clickable { send(ClientMessage.PlanGo(i)); onPlayed() },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text("${i + 1}. ${item.title}", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text(when (item.kind) { PlanKind.BIBLE -> "Lectura"; PlanKind.MEDIA -> "Anuncio"; PlanKind.SERMON -> "Predicación"; else -> "Canción" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ send(ClientMessage.PlanMove(i, i - 1)) }, enabled = i > 0) { Text("▲") }
                    TextButton({ send(ClientMessage.PlanMove(i, i + 1)) }, enabled = i < plan.items.lastIndex) { Text("▼") }
                    TextButton({ send(ClientMessage.PlanRemove(i)) }) { Text("✕") }
                }
            }
        }
        item {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mensaje al escenario", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    msg, { msg = it },
                    placeholder = { Text("Ej. Repetir coro · 2 minutos") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(ClientMessage.StageMessage(msg)) }),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ send(ClientMessage.StageMessage(msg)) }, Modifier.weight(1f)) { Text("Enviar") }
                    OutlinedButton({ msg = ""; send(ClientMessage.StageMessage("")) }) { Text("Borrar") }
                }
                if (stageMessage.isNotEmpty()) Text("En escenario: $stageMessage", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MoreTab(
    state: PresentationState,
    announcements: List<String>,
    sermons: List<SongSummary>,
    serverOffset: Long,
    send: (ClientMessage) -> Unit,
    onUpload: suspend (name: String, size: Long?, open: () -> java.io.InputStream) -> String,
    onShown: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var uploadMsg by remember { mutableStateOf<String?>(null) }
    // Elegir foto o video (galería o cámara) y subirlo a los anuncios del PC.
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (name, size) = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val n = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val s = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (c.moveToFirst()) (c.getString(n) ?: "foto.jpg") to (if (s >= 0 && !c.isNull(s)) c.getLong(s) else null) else null
        } ?: ("foto.jpg" to null)
        uploadMsg = "Subiendo $name…"
        scope.launch { uploadMsg = onUpload(name, size) { context.contentResolver.openInputStream(uri)!! } }
    }
    var alert by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("5") }
    var cdText by rememberSaveable { mutableStateOf("Comenzamos en") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.countdownEnd) {
        while (state.countdownEnd != null) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(500) }
    }
    val left = state.countdownSeconds(now + serverOffset)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button({ send(ClientMessage.ShowLogo(!state.logo)) }, Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (state.logo) "Quitar logo" else "Mostrar logo de la iglesia", fontSize = 17.sp)
            }
        }
        if (sermons.isNotEmpty()) {
            item { Text("Predicaciones", style = MaterialTheme.typography.titleMedium) }
            items(sermons, key = { "predicacion:${it.id}" }) { s ->
                val on = state.sermon && state.songId == s.id
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { send(ClientMessage.ShowSermon(s.id)); onShown() }.padding(vertical = 8.dp)) {
                        Text((if (on) "▶ " else "") + s.title, fontSize = 17.sp, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(s.firstLine, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton({
                        send(ClientMessage.PlanAddSermon(s.id))
                        Toast.makeText(context, "Agregada a la lista", Toast.LENGTH_SHORT).show()
                    }) { Text("+ Lista") }
                }
            }
        }
        item {
            Text("Anuncios", style = MaterialTheme.typography.titleMedium)
            OutlinedButton({ pick.launch(arrayOf("image/*", "video/*")) }, Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("📷 Subir foto o video") }
            uploadMsg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        if (announcements.isNotEmpty()) {
            items(announcements, key = { "anuncio:$it" }) { name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (name == state.media) "▶ " else "") + name.substringBeforeLast('.'),
                        Modifier.weight(1f).clickable { send(ClientMessage.ShowMedia(name)); onShown() }.padding(vertical = 10.dp),
                        color = if (name == state.media) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                    )
                    TextButton({
                        send(ClientMessage.PlanAddMedia(name))
                        Toast.makeText(context, "Agregado a la lista", Toast.LENGTH_SHORT).show()
                    }) { Text("+ Lista") }
                }
            }
        }
        item {
            Text("Alerta en pantalla", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                alert, { alert = it }, placeholder = { Text("Ej. Se busca al dueño del carro ABC123") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (alert.isNotBlank()) send(ClientMessage.SetAlert(alert)) }),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ send(ClientMessage.SetAlert(alert)) }, Modifier.weight(1f), enabled = alert.isNotBlank()) { Text("Mostrar") }
                OutlinedButton({ alert = ""; send(ClientMessage.SetAlert("")) }, enabled = state.alert.isNotEmpty()) { Text("Quitar") }
            }
            if (state.alert.isNotEmpty()) Text("En pantalla: ${state.alert}", color = MaterialTheme.colorScheme.primary)
        }
        item {
            Text("Cuenta regresiva", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    minutes, { v -> minutes = v.filter { it.isDigit() }.take(3) }, label = { Text("Min.") },
                    singleLine = true, modifier = Modifier.weight(0.35f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(cdText, { cdText = it }, label = { Text("Texto") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    { send(ClientMessage.StartCountdown((minutes.toIntOrNull() ?: 0) * 60, cdText)) },
                    Modifier.weight(1f), enabled = (minutes.toIntOrNull() ?: 0) > 0,
                ) { Text("Iniciar") }
                OutlinedButton({ send(ClientMessage.StopCountdown) }, enabled = state.countdownEnd != null) { Text("Detener") }
            }
            if (left != null) {
                Text(
                    if (left > 0) "Faltan ${app.proyecta.shared.formatClock(left)}" else "Terminó",
                    style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item { PreacherTimer(state, serverOffset, send) }
        item { LowerThird(state, send) }
    }
}
