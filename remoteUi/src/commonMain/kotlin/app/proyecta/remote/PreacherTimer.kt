package app.proyecta.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.proyecta.shared.ClientMessage
import app.proyecta.shared.PresentationState
import app.proyecta.shared.formatClock

/** Tiempo del predicador (se ve en la vista de escenario, no en el videobeam). */
@Composable
fun PreacherTimer(state: PresentationState, serverOffset: Long, send: (ClientMessage) -> Unit) {
    var minutes by rememberSaveable { mutableStateOf("30") }
    val now by produceState(nowMillis() + serverOffset, state.timerStart) {
        while (state.timerStart != null) { value = nowMillis() + serverOffset; kotlinx.coroutines.delay(500) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tiempo del predicador", style = MaterialTheme.typography.titleMedium)
        Text("Se ve solo en la vista de escenario.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                minutes, { v -> minutes = v.filter { it.isDigit() }.take(3) }, label = { Text("Min.") },
                singleLine = true, modifier = Modifier.weight(0.35f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Button({ send(ClientMessage.StartTimer(minutes.toIntOrNull() ?: 0)) }, Modifier.weight(0.6f).padding(top = 8.dp)) {
                Text(if (state.timerStart != null) "Reiniciar" else "Iniciar")
            }
            OutlinedButton({ send(ClientMessage.StopTimer) }, Modifier.padding(top = 8.dp), enabled = state.timerStart != null) { Text("Detener") }
        }
        state.timerElapsed(now)?.let { el ->
            val left = state.timerLeft(now)
            Text(
                "Lleva ${formatClock(el)}" + when {
                    left == null -> ""
                    left >= 0 -> " · quedan ${formatClock(left)}"
                    else -> " · se pasó ${formatClock(-left)}"
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (left != null && left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
