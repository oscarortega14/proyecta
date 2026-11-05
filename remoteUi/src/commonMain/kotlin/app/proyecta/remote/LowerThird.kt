package app.proyecta.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.proyecta.shared.ClientMessage
import app.proyecta.shared.PresentationState

/** Nombre en la transmisión (OBS), ej. "Pastor Juan Pérez · Predicación". */
@Composable
fun LowerThird(state: PresentationState, send: (ClientMessage) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var subtitle by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nombre en la transmisión (OBS)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(title, { title = it }, label = { Text("Nombre (ej. Pastor Juan Pérez)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(subtitle, { subtitle = it }, label = { Text("Debajo (ej. Predicación)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ send(ClientMessage.SetLowerThird(title, subtitle)) }, Modifier.weight(1f), enabled = title.isNotBlank()) { Text("Mostrar") }
            OutlinedButton({ send(ClientMessage.SetLowerThird("", "")) }, enabled = state.lowerThird.isNotEmpty()) { Text("Quitar") }
        }
        if (state.lowerThird.isNotEmpty()) Text("En OBS: ${state.lowerThird}", color = MaterialTheme.colorScheme.primary)
    }
}
