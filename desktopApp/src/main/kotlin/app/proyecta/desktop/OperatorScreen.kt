package app.proyecta.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.proyecta.shared.ClientMessage
import app.proyecta.shared.PresentationState
import app.proyecta.shared.search
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.AlertDialog
import app.proyecta.shared.BibleReference
import app.proyecta.shared.BibleHit
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import app.proyecta.shared.ServicePlanState
import app.proyecta.shared.PlanKind
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.PaddingValues
import kotlinx.coroutines.delay

@Composable
fun OperatorScreen(
    state: PresentationState,
    catalog: Catalog,
    plan: ServicePlanState,
    serverError: String?,
    projectorOn: Boolean,
    onToggleProjector: () -> Unit,
    onCommand: (ClientMessage) -> String?,
    onReload: () -> Unit,
    onOpenFolder: () -> Unit,
    onEdit: (songId: String?) -> Unit,
    onOpenBackgrounds: () -> Unit,
    onOpenConnect: () -> Unit,
    searchBible: (String) -> Pair<List<BibleHit>, Int>?,
    firewallOk: Boolean?,
    onImport: () -> String?,
    onExport: () -> String?,
) {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { if (message != null) { delay(6000); message = null } }
    val send: (ClientMessage) -> Unit = { m -> message = onCommand(m) }
    var helpOpen by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    Surface(
        Modifier.fillMaxSize().onPreviewKeyEvent {
            // Esc saca el foco del campo de texto para que vuelvan a funcionar los atajos.
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape && Typing.active) {
                focusManager.clearFocus(); Typing.active = false; true
            } else false
        },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                message = serverError ?: message,
                projectorOn = projectorOn,
                connectAttention = firewallOk == false,
                onToggleProjector = onToggleProjector,
                onOpenBackgrounds = onOpenBackgrounds,
                onOpenConnect = onOpenConnect,
                onHelp = { helpOpen = true },
            )
            HorizontalDivider()
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LeftPanel(
                    catalog, plan, Modifier.width(330.dp).fillMaxHeight(), send, onReload, onOpenFolder, onEdit, searchBible,
                    onImport = { onImport()?.let { message = it } }, onExport = { onExport()?.let { message = it } },
                )
                SlidesPanel(state, Modifier.weight(1f).fillMaxHeight(), send, onEdit)
                LivePanel(state, Modifier.width(320.dp).fillMaxHeight(), send)
            }
        }
        if (helpOpen) {
            AlertDialog(
                onDismissRequest = { helpOpen = false },
                title = { Text("Atajos de teclado") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((keys, what) in SHORTCUTS) {
                            Row { Text(keys, fontWeight = FontWeight.Bold, modifier = Modifier.width(170.dp)); Text(what) }
                        }
                    }
                },
                confirmButton = { TextButton({ helpOpen = false }) { Text("Entendido") } },
            )
        }
    }
}

/** Barra superior: lo que se usa de vez en cuando, fuera del camino durante el culto. */
@Composable
private fun TopBar(
    message: String?,
    projectorOn: Boolean,
    connectAttention: Boolean,
    onToggleProjector: () -> Unit,
    onOpenBackgrounds: () -> Unit,
    onOpenConnect: () -> Unit,
    onHelp: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Proyecta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            message.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        )
        OutlinedButton(onOpenBackgrounds) { Text("Fondos y letra") }
        OutlinedButton(onOpenConnect) {
            if (connectAttention) Text("● ", color = MaterialTheme.colorScheme.error)
            Text("Conectar dispositivos")
        }
        FilterChip(
            selected = projectorOn,
            onClick = onToggleProjector,
            label = { Text(if (projectorOn) "Proyección: encendida" else "Proyección: apagada") },
        )
        TextButton(onHelp) { Text("?", style = MaterialTheme.typography.titleMedium) }
    }
}

/** Izquierda: Canciones · Biblia · Lista del culto, en pestañas. */
@Composable
private fun LeftPanel(
    catalog: Catalog,
    plan: ServicePlanState,
    modifier: Modifier,
    send: (ClientMessage) -> Unit,
    onReload: () -> Unit,
    onOpenFolder: () -> Unit,
    onEdit: (songId: String?) -> Unit,
    searchBible: (String) -> Pair<List<BibleHit>, Int>?,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    // Al proyectar algo, el foco sale del campo para que las flechas vuelvan a mover la letra.
    val present: (ClientMessage) -> Unit = { m -> send(m); focusManager.clearFocus(); Typing.active = false }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TabRow(selectedTabIndex = tab) {
            val titles = listOf("Canciones", "Biblia", "Lista" + if (plan.items.isNotEmpty()) " (${plan.items.size})" else "")
            titles.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, modifier = Modifier.hand(), text = { Text(t, maxLines = 1) })
            }
        }
        when (tab) {
            0 -> SongsTab(catalog, Modifier.weight(1f), send, present, onReload, onOpenFolder, onEdit, onImport, onExport)
            1 -> BibleTab(catalog, Modifier.weight(1f), send, present, searchBible)
            else -> PlanPanel(plan, Modifier.weight(1f).fillMaxWidth(), send)
        }
    }
}

@Composable
private fun SongsTab(
    catalog: Catalog,
    modifier: Modifier,
    send: (ClientMessage) -> Unit,
    present: (ClientMessage) -> Unit,
    onReload: () -> Unit,
    onOpenFolder: () -> Unit,
    onEdit: (songId: String?) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, catalog) { catalog.songs.search(query) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            query, { query = it },
            label = { Text("Buscar entre ${catalog.songs.size} canciones") },
            singleLine = true, modifier = Modifier.fillMaxWidth().marksTyping(),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(results, key = { it.id }) { song ->
                Row(
                    Modifier.fillMaxWidth().handClickable { present(ClientMessage.SelectSong(song.id)) }.padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(song.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            song.firstLine, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton({ send(ClientMessage.PlanAddSong(song.id)) }, contentPadding = PaddingValues(6.dp)) { Text("+ Lista") }
                    TextButton({ onEdit(song.id) }, contentPadding = PaddingValues(6.dp)) { Text("Editar") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button({ onEdit(null) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+ Nueva") }
            OutlinedButton(onImport, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Importar…") }
            OutlinedButton(onExport, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Exportar…") }
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            TextButton(onReload) { Text("Recargar") }
            TextButton(onOpenFolder) { Text("Abrir carpeta") }
        }
    }
}

@Composable
private fun BibleTab(
    catalog: Catalog,
    modifier: Modifier,
    send: (ClientMessage) -> Unit,
    present: (ClientMessage) -> Unit,
    searchBible: (String) -> Pair<List<BibleHit>, Int>?,
) {
    var reference by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<Pair<List<BibleHit>, Int>?>(null) }
    val showOrSearch = {
        if (BibleReference.parse(reference) != null) present(ClientMessage.ShowBible(reference))
        else hits = searchBible(reference)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            catalog.bibleName ?: catalog.bibleError ?: "Sin Biblia",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            reference, { reference = it },
            label = { Text("Cita o frase (ej. Juan 3:16 · amó Dios)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().marksTyping(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { showOrSearch() }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button({ present(ClientMessage.ShowBible(reference)) }, Modifier.weight(1f), enabled = reference.isNotBlank(), contentPadding = PaddingValues(6.dp)) {
                Text("Mostrar")
            }
            OutlinedButton({ hits = searchBible(reference) }, Modifier.weight(1f), enabled = reference.isNotBlank(), contentPadding = PaddingValues(6.dp)) {
                Text("Buscar frase")
            }
            OutlinedButton({ send(ClientMessage.PlanAddBible(reference)) }, Modifier.weight(1f), enabled = reference.isNotBlank(), contentPadding = PaddingValues(6.dp)) {
                Text("+ Lista")
            }
        }
        hits?.let { (list, total) ->
            Text(
                if (total == 0) "Sin resultados" else "$total resultados" + if (total > list.size) " (primeros ${list.size})" else "",
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(hits?.first.orEmpty(), key = { it.reference }) { hit ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).handClickable { present(ClientMessage.ShowBible(hit.reference)) }.padding(vertical = 6.dp, horizontal = 4.dp)) {
                        Text(hit.reference, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        Text(hit.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton({ send(ClientMessage.PlanAddBible(hit.reference)) }, contentPadding = PaddingValues(6.dp)) { Text("+ Lista") }
                }
            }
        }
        if (hits == null) {
            Text(
                "Escribe una cita (Juan 3:16, Sal 23, 1 Co 13:4-7) y presiona Enter para mostrarla, o unas palabras para buscarlas en toda la Biblia.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SlidesPanel(
    state: PresentationState,
    modifier: Modifier,
    send: (ClientMessage) -> Unit,
    onEdit: (songId: String?) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.index, state.title) { if (state.index >= 0) listState.animateScrollToItem(state.index) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.title.ifEmpty { "Elige una canción o una cita" },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            state.songId?.let { id -> OutlinedButton({ onEdit(id) }) { Text("Editar canción") } }
        }
        SectionJumps(state, send)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.slides) { i, slide ->
                val selected = i == state.index
                Card(
                    Modifier.fillMaxWidth().handClickable { send(ClientMessage.GoTo(i)) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        if (slide.label.isNotEmpty()) {
                            Text(slide.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(slide.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** Derecha: solo lo "en vivo". Los avisos van plegados para no estorbar. */
@Composable
private fun LivePanel(state: PresentationState, modifier: Modifier, send: (ClientMessage) -> Unit) {
    var avisosOpen by remember { mutableStateOf(false) }
    val avisosActivos = listOfNotNull(
        "alerta".takeIf { state.alert.isNotEmpty() },
        "cuenta regresiva".takeIf { state.countdownEnd != null },
        "mensaje".takeIf { state.stageMessage.isNotEmpty() },
    )
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("En pantalla", style = MaterialTheme.typography.titleMedium)
        Surface(Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = MaterialTheme.shapes.small) {
            ProjectorScreen(state, look = LocalLook.current.copy(preview = true))
        }
        state.next?.let { next ->
            Column {
                Text("SIGUE" + if (next.label.isNotEmpty() && !state.isBible) " · ${next.label}" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(next.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ send(ClientMessage.Prev) }, Modifier.weight(1f).height(56.dp)) { Text("◀", style = MaterialTheme.typography.titleLarge) }
            Button({ send(ClientMessage.Next) }, Modifier.weight(2f).height(56.dp)) { Text("Siguiente ▶", style = MaterialTheme.typography.titleMedium) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ send(ClientMessage.SetBlank(!state.blank)) }, Modifier.weight(1f)) {
                Text(if (state.blank) "Mostrar" else "Negro (B)")
            }
            OutlinedButton({ send(ClientMessage.ShowLogo(!state.logo)) }, Modifier.weight(1f)) {
                Text(if (state.logo) "Quitar logo" else "Logo (L)")
            }
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().handClickable { avisosOpen = !avisosOpen }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (avisosOpen) "▾ " else "▸ ", style = MaterialTheme.typography.titleMedium)
            Text("Avisos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (avisosActivos.isNotEmpty()) {
                Text("● " + avisosActivos.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (avisosOpen) {
            AlertBox(state.alert, send)
            HorizontalDivider()
            CountdownBox(state, send)
            HorizontalDivider()
            StageMessageBox(state.stageMessage, send)
        }
    }
}

/** Botones para saltar a una sección (Coro, Pregón 1…). Se resalta la sección actual. */
@Composable
private fun SectionJumps(state: PresentationState, send: (ClientMessage) -> Unit) {
    val labels = state.sectionLabels
    if (labels.isEmpty()) return
    val current = state.currentLabel
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels) { label ->
            FilterChip(
                selected = label == current,
                onClick = { state.jumpTarget(label)?.let { send(ClientMessage.GoTo(it)) } },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun PlanPanel(plan: ServicePlanState, modifier: Modifier, send: (ClientMessage) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Orden del culto", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (plan.items.isNotEmpty()) TextButton({ send(ClientMessage.PlanClear) }) { Text("Vaciar") }
        }
        Button({ send(ClientMessage.PlanNext) }, Modifier.fillMaxWidth(), enabled = plan.current < plan.items.lastIndex) {
            Text("Siguiente elemento ▶")
        }
        if (plan.items.isEmpty()) {
            Text(
                "Vacía. Usa \"+ Lista\" en las canciones o \"+ A la lista\" en la Biblia para preparar el culto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(plan.items) { i, item ->
                val selected = i == plan.current
                Card(
                    Modifier.fillMaxWidth().handClickable { send(ClientMessage.PlanGo(i)) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text("${i + 1}. ${item.title}", maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(
                                if (item.kind == PlanKind.BIBLE) "Lectura" else "Canción",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton({ send(ClientMessage.PlanMove(i, i - 1)) }, enabled = i > 0, contentPadding = PaddingValues(4.dp)) { Text("▲") }
                        TextButton({ send(ClientMessage.PlanMove(i, i + 1)) }, enabled = i < plan.items.lastIndex, contentPadding = PaddingValues(4.dp)) { Text("▼") }
                        TextButton({ send(ClientMessage.PlanRemove(i)) }, contentPadding = PaddingValues(4.dp)) { Text("✕") }
                    }
                }
            }
        }
    }
}

/** Mensaje para los músicos en la vista de escenario. */
@Composable
private fun StageMessageBox(current: String, send: (ClientMessage) -> Unit) {
    var text by remember { mutableStateOf("") }
    Text("Mensaje al escenario", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        text, { text = it },
        placeholder = { Text("Ej. Repetir coro") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().marksTyping(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send(ClientMessage.StageMessage(text)) }),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ send(ClientMessage.StageMessage(text)) }, Modifier.weight(1f)) { Text("Enviar") }
        OutlinedButton({ text = ""; send(ClientMessage.StageMessage("")) }, enabled = current.isNotEmpty()) { Text("Borrar") }
    }
    if (current.isNotEmpty()) Text("En escenario: $current", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
}

/** Aviso en una franja abajo del videobeam (ej. "Se busca al dueño del carro ABC123"). */
@Composable
private fun AlertBox(current: String, send: (ClientMessage) -> Unit) {
    var text by remember { mutableStateOf("") }
    Text("Alerta en pantalla", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        text, { text = it },
        placeholder = { Text("Ej. Se busca al dueño del carro ABC123") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().marksTyping(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send(ClientMessage.SetAlert(text)) }),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ send(ClientMessage.SetAlert(text)) }, Modifier.weight(1f), enabled = text.isNotBlank()) { Text("Mostrar") }
        OutlinedButton({ send(ClientMessage.SetAlert("")) }, enabled = current.isNotEmpty()) { Text("Quitar") }
    }
    if (current.isNotEmpty()) Text("En pantalla: $current", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
}

/** Cuenta regresiva en el videobeam (ej. "Comenzamos en 05:00"). */
@Composable
private fun CountdownBox(state: PresentationState, send: (ClientMessage) -> Unit) {
    var minutes by remember { mutableStateOf("5") }
    var text by remember { mutableStateOf("Comenzamos en") }
    val running = state.countdownEnd != null && (state.countdownEnd ?: 0) > System.currentTimeMillis()
    Text("Cuenta regresiva", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            minutes, { v -> minutes = v.filter { it.isDigit() }.take(3) },
            label = { Text("Min.") }, singleLine = true, modifier = Modifier.width(80.dp).marksTyping(),
        )
        OutlinedTextField(text, { text = it }, label = { Text("Texto") }, singleLine = true, modifier = Modifier.weight(1f).marksTyping())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            { send(ClientMessage.StartCountdown((minutes.toIntOrNull() ?: 0) * 60, text)) },
            Modifier.weight(1f),
            enabled = (minutes.toIntOrNull() ?: 0) > 0,
        ) { Text(if (running) "Reiniciar" else "Iniciar") }
        OutlinedButton({ send(ClientMessage.StopCountdown) }, enabled = state.countdownEnd != null) { Text("Detener") }
    }
}
