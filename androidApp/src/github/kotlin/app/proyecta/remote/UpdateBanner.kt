package app.proyecta.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Variante "github": la app se actualiza sola desde GitHub Releases.
/** "Hay una versión nueva de la app": se revisa un rato después de abrir, una vez por apertura. */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val updater = remember { AppUpdater(context.applicationContext) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5_000)
        update = updater.check()
    }
    val u = update ?: return
    if (hidden) return
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Hay una versión nueva de la app: ${u.version}", fontWeight = FontWeight.Medium)
            progress?.let { androidx.compose.material3.LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            // Mensaje corto (como mucho 3 líneas): que nunca tape el botón Actualizar.
            message?.let { Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    message = null
                    progress = 0f
                    scope.launch {
                        runCatching { updater.download(u) { progress = it } }
                            .onSuccess { apk ->
                                progress = null
                                message = if (updater.install(apk)) "Confirma la instalación en la ventana de Android."
                                else "Permite instalar apps de Proyecta Remoto y vuelve a tocar Actualizar."
                            }
                            .onFailure { progress = null; message = (it as? DownloadException)?.message ?: Downloader.friendly(it as? Exception) }
                    }
                }, enabled = progress == null) { Text("Actualizar") }
                TextButton({ hidden = true }) { Text("Más tarde") }
            }
        }
    }
}
