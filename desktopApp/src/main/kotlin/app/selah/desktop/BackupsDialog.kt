package app.selah.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Respaldos: hacer uno ahora, elegir una carpeta extra (USB) y restaurar. */
@Composable
fun BackupsDialog(backups: Backups, settings: Settings, onChooseCopyDir: (String?) -> Unit, onRestored: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val list = remember(refresh) { backups.list() }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<File?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        modifier = Modifier.width(620.dp),
        title = { Text("Respaldos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Una vez por semana Selah guarda solo una copia de las canciones, predicaciones, listas del culto e historial " +
                        "(se guardan las últimas ${Backups.KEEP}). Las imágenes y videos no entran: guárdalos aparte.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Copia extra en: " + (settings.backupCopyDir ?: "ninguna"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton({ chooseFolder()?.let(onChooseCopyDir) }) { Text("Elegir carpeta…") }
                    if (settings.backupCopyDir != null) TextButton({ onChooseCopyDir(null) }) { Text("Quitar") }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(list, key = { it.name }) { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(describe(f), Modifier.weight(1f).padding(vertical = 6.dp))
                            if (confirm == f) {
                                TextButton({
                                    confirm = null; busy = true
                                    scope.launch {
                                        val n = withContext(Dispatchers.IO) { runCatching { backups.restore(f) } }
                                        busy = false; refresh++
                                        message = n.fold({ onRestored(); "Restaurado ($it archivos). Antes se guardó un respaldo de cómo estaba." }, { "No pude restaurar: ${it.message}" })
                                    }
                                }) { Text("Sí, restaurar", color = MaterialTheme.colorScheme.error) }
                            } else {
                                TextButton({ confirm = f }, enabled = !busy) { Text("Restaurar…") }
                            }
                        }
                    }
                }
                if (list.isEmpty()) Text("Todavía no hay respaldos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button({
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { backups.create() } }
                    busy = false; refresh++
                    message = r.fold({ "Respaldo hecho: ${it.name}" }, { "No pude hacer el respaldo: ${it.message}" })
                }
            }, enabled = !busy) { Text("Hacer un respaldo ahora") }
        },
        dismissButton = { TextButton(onClose, enabled = !busy) { Text("Cerrar") } },
    )
}

/** "Selah-respaldo-2026-11-08_103000.zip" → "domingo 8 nov 2026, 10:30 · 120 KB" */
internal fun describe(f: File): String {
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})_(\d{2})(\d{2})""").find(f.name)
    val date = m?.let { val (y, mo, d, h, mi) = it.destructured; formatDay(java.time.LocalDate.of(y.toInt(), mo.toInt(), d.toInt())) + ", $h:$mi" } ?: f.name
    return "$date · ${(f.length() + 1023) / 1024} KB"
}

private fun chooseFolder(): String? {
    val chooser = javax.swing.JFileChooser().apply {
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Carpeta para la copia extra de los respaldos (por ejemplo una memoria USB)"
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
}
