package app.proyecta.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import java.time.LocalDate

/** Qué se cantó y leyó cada día, y las canciones más cantadas (para no repetir o para reportes). */
@Composable
fun HistoryWindow(history: History, onClose: () -> Unit) {
    val entries by history.entries.collectAsState()
    var months by remember { mutableIntStateOf(3) }
    var message by remember { mutableStateOf<String?>(null) }
    Window(onCloseRequest = onClose, title = "Historial", state = rememberFittedWindowState(DpSize(980.dp, 700.dp))) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Por día, lo más reciente arriba
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Por día", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            OutlinedButton({ message = exportHistory(history) }, enabled = entries.isNotEmpty()) { Text("Exportar a Excel (.csv)…") }
                        }
                        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        if (entries.isEmpty()) {
                            Text(
                                "Todavía no hay nada. Cada canción y lectura que se proyecte queda anotada aquí (una vez por día).",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val byDay = remember(entries) { entries.groupBy { it.date }.toSortedMap(compareByDescending { it }).toList() }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            for ((day, list) in byDay) {
                                item(key = day.toString()) {
                                    Text(
                                        formatDay(day).replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                    )
                                }
                                items(list, key = { "$day|${it.kind}|${it.ref}" }) { e ->
                                    Row {
                                        Text(e.time, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
                                        Text(e.kind.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                                        Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    // Ranking
                    Column(Modifier.width(360.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Más cantadas", style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (m in listOf(1, 3, 12)) {
                                FilterChip(months == m, { months = m }, { Text(if (m == 12) "Último año" else if (m == 1) "Último mes" else "$m meses") })
                            }
                        }
                        val top = remember(entries, months) { history.topSongs(LocalDate.now().minusMonths(months.toLong())) }
                        HorizontalDivider()
                        if (top.isEmpty()) Text("Sin canciones en ese tiempo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn {
                            items(top, key = { it.first }) { (title, count) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (count == 1) "1 vez" else "$count veces", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun exportHistory(history: History): String? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Exportar historial", java.awt.FileDialog.SAVE).apply {
        file = "historial-proyecta.csv"
        isVisible = true
    }
    val name = dialog.file ?: return null
    val target = File(dialog.directory, if (name.endsWith(".csv", true)) name else "$name.csv")
    return runCatching { history.exportCsv(target); "Guardado en ${target.absolutePath}" }.getOrElse { "No pude guardar: ${it.message}" }
}
