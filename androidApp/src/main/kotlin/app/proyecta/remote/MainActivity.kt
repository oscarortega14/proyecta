package app.proyecta.remote

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ScrollableTabRow
import app.proyecta.shared.ServerMessage
import app.proyecta.shared.ServicePlanState
import app.proyecta.shared.PlanKind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton

class MainActivity : ComponentActivity() {
    private lateinit var client: RemoteClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // En pleno culto la pantalla no debe apagarse.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("proyecta", Context.MODE_PRIVATE)
        client = RemoteClient(lifecycleScope)
        prefs.getString("host", null)?.let { client.connect(it) }
        client.onHostChanged = { host -> prefs.edit().putString("host", host).apply() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                client.errors.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RemoteApp(
                        client = client,
                        savedHost = prefs.getString("host", "").orEmpty(),
                        onConnect = { host ->
                            prefs.edit().putString("host", host).apply()
                            client.connect(host)
                        },
                    )
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
private fun RemoteApp(client: RemoteClient, savedHost: String, onConnect: (String) -> Unit) {
    val status by client.status.collectAsState()
    val state by client.state.collectAsState()
    val songs by client.songs.collectAsState()
    val bibleName by client.bibleName.collectAsState()
    val plan by client.plan.collectAsState()
    val bibleResults by client.bibleResults.collectAsState()
    var editingHost by rememberSaveable { mutableStateOf(savedHost.isEmpty()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        StatusBar(status, onEdit = { editingHost = true })
        // Ojo: nada de "return" temprano dentro de Column (es inline y rompe la recomposición).
        if (editingHost) {
            ConnectForm(savedHost, client) { host -> editingHost = false; onConnect(host) }
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
                    1 -> SongsTab(songs, onAdd = { id -> client.send(ClientMessage.PlanAddSong(id)) }) { id ->
                        client.send(ClientMessage.SelectSong(id)); tab = 0
                    }
                    2 -> BibleTab(
                        bibleName, bibleResults,
                        onSearch = { q -> client.send(ClientMessage.BibleSearch(q)) },
                        onAdd = { ref -> client.send(ClientMessage.PlanAddBible(ref)) },
                    ) { ref ->
                        client.send(ClientMessage.ShowBible(ref)); tab = 0
                    }
                    3 -> PlanTab(plan, state.stageMessage, client::send) { tab = 0 }
                    4 -> MoreTab(state, client.serverOffset, client::send)
                }
            }
            Controls(state, client::send)
        }
    }
}

@Composable
private fun StatusBar(status: ConnectionStatus, onEdit: () -> Unit) {
    val (text, color) = when (status) {
        ConnectionStatus.Idle -> "Sin configurar" to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionStatus.Connecting -> "Conectando a ${status.host}…" to MaterialTheme.colorScheme.tertiary
        is ConnectionStatus.Connected -> "Conectado a ${status.host}" to MaterialTheme.colorScheme.primary
        is ConnectionStatus.Failed -> "Sin conexión con ${status.host} — reintentando" to MaterialTheme.colorScheme.error
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Cambiar", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ConnectForm(initial: String, client: RemoteClient, onConnect: (String) -> Unit) {
    var host by rememberSaveable { mutableStateOf(initial) }
    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<FoundServer>?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val connect = { if (host.isNotBlank()) onConnect(host.trim()) }

    // Al abrir: buscar a Proyecta en la red. Si hay uno solo, conectarse sin preguntar.
    LaunchedEffect(attempt) {
        searching = true
        val result = client.discover()
        searching = false
        found = result
        if (result.size == 1 && attempt == 0 && initial.isEmpty()) onConnect(result[0].host)
    }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            searching -> Text("Buscando Proyecta en la red…", style = MaterialTheme.typography.titleMedium)
            found.isNullOrEmpty() -> Text(
                "No encontré Proyecta en esta red. Revisa que el computador tenga Proyecta abierto y esté en el mismo Wi-Fi.",
                color = MaterialTheme.colorScheme.error,
            )
            else -> Text("Elige el computador:", style = MaterialTheme.typography.titleMedium)
        }
        found.orEmpty().forEach { s ->
            Button({ onConnect(s.host) }, Modifier.fillMaxWidth().height(56.dp)) { Text("${s.name}  (${s.host})", maxLines = 1) }
        }
        OutlinedButton({ attempt++ }, Modifier.fillMaxWidth(), enabled = !searching) { Text("Buscar de nuevo") }

        Text("O escribe la dirección que muestra Proyecta (ej. 192.168.1.20):", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            host, { host = it },
            label = { Text("Dirección del computador") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { connect() }),
        )
        OutlinedButton(onClick = connect, modifier = Modifier.fillMaxWidth(), enabled = host.isNotBlank()) { Text("Conectar a esta dirección") }
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
private fun SongsTab(songs: List<SongSummary>, onAdd: (String) -> Unit, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, songs) { songs.search(query) }
    OutlinedTextField(query, { query = it }, label = { Text("Buscar canción") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    LazyColumn(Modifier.padding(top = 8.dp)) {
        items(results, key = { it.id }) { song ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onSelect(song.id) }.padding(vertical = 10.dp)) {
                    Text(song.title, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                    Text(song.firstLine, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun BibleTab(
    bibleName: String?,
    results: ServerMessage.BibleResults?,
    onSearch: (String) -> Unit,
    onAdd: (String) -> Unit,
    onShow: (String) -> Unit,
) {
    val context = LocalContext.current
    var ref by rememberSaveable { mutableStateOf("") }
    val show = { if (ref.isNotBlank()) onShow(ref.trim()) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { item {
        Text(bibleName ?: "El computador no tiene Biblia cargada", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            ref, { ref = it },
            label = { Text("Cita o frase (ej. Juan 3:16 · amó Dios)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { show() }),
        )
        Button(onClick = show, modifier = Modifier.fillMaxWidth()) { Text("Mostrar en pantalla") }
        OutlinedButton(
            onClick = {
                if (ref.isNotBlank()) {
                    onAdd(ref.trim())
                    Toast.makeText(context, "Agregada a la lista", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Agregar a la lista") }
        OutlinedButton({ if (ref.isNotBlank()) onSearch(ref.trim()) }, Modifier.fillMaxWidth()) { Text("Buscar frase") }
        results?.let { r ->
            Text(
                if (r.total == 0) "Sin resultados para «${r.query}»"
                else "${r.total} resultados" + if (r.total > r.hits.size) " (primeros ${r.hits.size})" else "",
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    items(results?.hits.orEmpty(), key = { it.reference }) { hit ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { onShow(hit.reference) }.padding(vertical = 6.dp)) {
                Text(hit.reference, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Text(hit.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton({
                onAdd(hit.reference)
                Toast.makeText(context, "Agregada a la lista", Toast.LENGTH_SHORT).show()
            }) { Text("+") }
        }
    } }
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
private fun PlanTab(plan: ServicePlanState, stageMessage: String, send: (ClientMessage) -> Unit, onPlayed: () -> Unit) {
    var msg by rememberSaveable { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lista del culto", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button({ send(ClientMessage.PlanNext); onPlayed() }, enabled = plan.current < plan.items.lastIndex) {
                    Text("Siguiente ▶")
                }
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
                        Text(if (item.kind == PlanKind.BIBLE) "Lectura" else "Canción", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun MoreTab(state: PresentationState, serverOffset: Long, send: (ClientMessage) -> Unit) {
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
    }
}
