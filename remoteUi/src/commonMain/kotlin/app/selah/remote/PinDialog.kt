package app.selah.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pide la clave de 4 números que aparece en el PC (Conectar dispositivos). */
@Composable
fun PinDialog(pcName: String, message: String?, onDismiss: () -> Unit, onPin: (String) -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    val ok = pin.length in 4..6
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clave para controlar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Está en el computador${if (pcName.isNotEmpty()) " ($pcName)" else ""}, en \"Conectar dispositivos\". Se pide una sola vez.")
                OutlinedTextField(
                    pin, { v -> pin = v.filter(Char::isDigit).take(6) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 32.sp, textAlign = TextAlign.Center, letterSpacing = 12.sp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ok) { onPin(pin); pin = "" } }),
                )
                message?.takeIf { "Escribe la clave" !in it }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button({ onPin(pin); pin = "" }, enabled = ok) { Text("Entrar") } },
        dismissButton = { TextButton(onDismiss) { Text("Solo mirar") } },
    )
}
