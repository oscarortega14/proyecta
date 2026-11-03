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
import androidx.compose.material3.TabRow
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
    var editingHost by rememberSaveable { mutableStateOf(savedHost.isEmpty()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        StatusBar(status, onEdit = { editingHost = true })
        // Ojo: nada de "return" temprano dentro de Column (es inline y rompe la recomposición).
        if (editingHost) {
            ConnectForm(savedHost) { host -> editingHost = false; onConnect(host) }
        } else {
            TabRow(selectedTabIndex = tab) {
                listOf("En pantalla", "Canciones", "Biblia", "Lista").forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                when (tab) {
                    0 -> LiveTab(state, client::send)
                    1 -> SongsTab(songs, onAdd = { id -> client.send(ClientMessage.PlanAddSong(id)) }) { id ->
                        client.send(ClientMessage.SelectSong(id)); tab = 0
                    }
                    2 -> BibleTab(bibleName, onAdd = { ref -> client.send(ClientMessage.PlanAddBible(ref)) }) { ref ->
                        client.send(ClientMessage.ShowBible(ref)); tab = 0
                    }
                    3 -> PlanTab(plan, state.stageMessage, client::send) { tab = 0 }
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
private fun ConnectForm(initial: String, onConnect: (String) -> Unit) {
    var host by rememberSaveable { mutableStateOf(initial) }
    val connect = { if (host.isNotBlank()) onConnect(host.trim()) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Escribe la dirección que muestra Proyecta en el computador (ej. 192.168.1.20)")
        OutlinedTextField(
            host, { host = it },
            label = { Text("Dirección del computador") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { connect() }),
        )
        Button(onClick = connect, modifier = Modifier.fillMaxWidth()) { Text("Conectar") }
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
private fun BibleTab(bibleName: String?, onAdd: (String) -> Unit, onShow: (String) -> Unit) {
    val context = LocalContext.current
    var ref by rememberSaveable { mutableStateOf("") }
    val show = { if (ref.isNotBlank()) onShow(ref.trim()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(bibleName ?: "El computador no tiene Biblia cargada", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            ref, { ref = it },
            label = { Text("Cita (ej. Juan 3:16, Sal 23, 1 Co 13:4-7)") },
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
        ) { Text(if (state.blank) "Mostrar" else "Negro", fontSize = 18.sp) }
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
