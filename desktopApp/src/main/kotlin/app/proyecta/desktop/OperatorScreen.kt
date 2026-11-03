package app.proyecta.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    addresses: List<String>,
    port: Int,
    serverError: String?,
    projectorOn: Boolean,
    onToggleProjector: () -> Unit,
    onCommand: (ClientMessage) -> String?,
    onReload: () -> Unit,
    onOpenFolder: () -> Unit,
    onEdit: (songId: String?) -> Unit,
) {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { if (message != null) { delay(5000); message = null } }
    val send: (ClientMessage) -> Unit = { m -> message = onCommand(m) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LibraryPanel(catalog, Modifier.width(300.dp).fillMaxHeight(), send, onReload, onOpenFolder, onEdit)
            PlanPanel(plan, Modifier.width(250.dp).fillMaxHeight(), send)
            SlidesPanel(state, Modifier.weight(1f).fillMaxHeight(), send, onEdit)
            ControlPanel(
                state, addresses, port, serverError ?: message, projectorOn, onToggleProjector,
                Modifier.width(300.dp).fillMaxHeight(), send,
            )
        }
    }
}

@Composable
private fun LibraryPanel(
    catalog: Catalog,
    modifier: Modifier,
    send: (ClientMessage) -> Unit,
    onReload: () -> Unit,
    onOpenFolder: () -> Unit,
    onEdit: (songId: String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    val results = remember(query, catalog) { catalog.songs.search(query) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Biblia", style = MaterialTheme.typography.titleMedium)
        Text(
            catalog.bibleName ?: catalog.bibleError ?: "Sin Biblia",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            reference, { reference = it },
            label = { Text("Cita (ej. Juan 3:16)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { send(ClientMessage.ShowBible(reference)) }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ send(ClientMessage.ShowBible(reference)) }, Modifier.weight(1f), enabled = reference.isNotBlank()) {
                Text("Mostrar")
            }
            OutlinedButton({ send(ClientMessage.PlanAddBible(reference)) }, Modifier.weight(1f), enabled = reference.isNotBlank()) {
                Text("+ A la lista")
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Canciones (${catalog.songs.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton({ onEdit(null) }) { Text("+ Nueva") }
        }
        OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(results, key = { it.id }) { song ->
                Row(
                    Modifier.fillMaxWidth().clickable { send(ClientMessage.SelectSong(song.id)) }.padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(song.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            song.firstLine, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton({ send(ClientMessage.PlanAddSong(song.id)) }) { Text("+ Lista") }
                    TextButton({ onEdit(song.id) }) { Text("Editar") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onReload, Modifier.weight(1f)) { Text("Recargar") }
            OutlinedButton(onOpenFolder, Modifier.weight(1f)) { Text("Abrir carpeta") }
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
                        Text(slide.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlPanel(
    state: PresentationState,
    addresses: List<String>,
    port: Int,
    message: String?,
    projectorOn: Boolean,
    onToggleProjector: () -> Unit,
    modifier: Modifier,
    send: (ClientMessage) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("En pantalla", style = MaterialTheme.typography.titleMedium)
        Surface(Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = MaterialTheme.shapes.small) {
            ProjectorScreen(state)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ send(ClientMessage.Prev) }, Modifier.weight(1f)) { Text("◀ Anterior") }
            Button({ send(ClientMessage.Next) }, Modifier.weight(1f)) { Text("Siguiente ▶") }
        }
        OutlinedButton({ send(ClientMessage.SetBlank(!state.blank)) }, Modifier.fillMaxWidth()) {
            Text(if (state.blank) "Mostrar texto" else "Pantalla negra")
        }
        OutlinedButton(onToggleProjector, Modifier.fillMaxWidth()) {
            Text(if (projectorOn) "Cerrar ventana de proyección" else "Abrir ventana de proyección")
        }
        Text(
            "Teclado: Re Pág / Av Pág para moverse, Ctrl+B pantalla negra",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))
        if (message != null) {
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        StageMessageBox(state.stageMessage, send)
        HorizontalDivider()
        Text("Control remoto", style = MaterialTheme.typography.titleMedium)
        if (addresses.isEmpty()) {
            Text("No encontré una red local. Conecta este equipo al Wi-Fi o al router.")
        } else {
            Text("App de la tablet: escribe", style = MaterialTheme.typography.bodySmall)
            for (a in addresses) Text(a, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val a = addresses.first()
            Text("Desde cualquier navegador:", style = MaterialTheme.typography.bodySmall)
            SelectionContainer {
                Column {
                    Text("http://$a:$port  (control)", style = MaterialTheme.typography.bodySmall)
                    Text("http://$a:$port/escenario.html", style = MaterialTheme.typography.bodySmall)
                    Text("http://$a:$port/obs.html  (OBS)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
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
            Text("Lista del culto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
                    Modifier.fillMaxWidth().clickable { send(ClientMessage.PlanGo(i)) },
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
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send(ClientMessage.StageMessage(text)) }),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ send(ClientMessage.StageMessage(text)) }, Modifier.weight(1f)) { Text("Enviar") }
        OutlinedButton({ text = ""; send(ClientMessage.StageMessage("")) }, enabled = current.isNotEmpty()) { Text("Borrar") }
    }
    if (current.isNotEmpty()) Text("En escenario: $current", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
}
