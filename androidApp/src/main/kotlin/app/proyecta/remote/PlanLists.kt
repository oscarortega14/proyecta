package app.proyecta.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** "Culto domingo 8 nov 2026": el próximo domingo (o hoy). Igual que en el PC; sin java.time (Android 7). */
fun suggestedListName(now: Calendar = Calendar.getInstance()): String {
    val c = now.clone() as Calendar
    while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) c.add(Calendar.DAY_OF_MONTH, 1)
    return "Culto domingo ${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}"
}

@Composable
fun SavePlanDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guardar la lista") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button({ onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun OpenPlanDialog(saved: List<String>, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abrir una lista guardada") },
        text = {
            Column {
                Text("Reemplaza la lista actual.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(saved, key = { it }) { name ->
                        Text(name, Modifier.fillMaxWidth().clickable { onOpen(name) }.padding(vertical = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar") } },
    )
}
