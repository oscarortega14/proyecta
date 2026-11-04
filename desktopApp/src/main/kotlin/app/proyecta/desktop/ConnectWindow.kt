package app.proyecta.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Matriz del código QR (sin margen extra: el margen blanco lo pone el dibujo). */
fun qrMatrix(text: String): BitMatrix = QRCodeWriter().encode(
    text, BarcodeFormat.QR_CODE, 0, 0,
    mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"),
)

/** Código QR dibujado en negro sobre blanco (con margen, como exige el estándar para leerse bien). */
@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) { qrMatrix(text) }
    Canvas(modifier.size(size).background(Color.White)) {
        val quiet = 2 // módulos de margen blanco
        val cells = matrix.width + quiet * 2
        val cell = this.size.width / cells
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/**
 * "Conectar dispositivos": todo lo necesario para que tablets, celulares, músicos y la transmisión
 * se conecten, sin tener que escribir direcciones (se escanea el QR).
 */
@Composable
fun ConnectWindow(
    addresses: List<String>,
    port: Int,
    pcName: String,
    firewallOk: Boolean?,
    onAllowFirewall: () -> Unit,
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf(addresses.firstOrNull()) }
    val host = selected
    Window(onCloseRequest = onClose, title = "Conectar dispositivos", state = rememberWindowState(size = DpSize(980.dp, 720.dp))) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. Permiso de Windows
                    when (firewallOk) {
                        false -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Windows todavía no deja que otros equipos se conecten a Proyecta.",
                                    color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f),
                                )
                                Button(onAllowFirewall) { Text("Permitir conexiones") }
                            }
                        }
                        true -> Text("✓ Windows permite las conexiones de la red local.", color = MaterialTheme.colorScheme.primary)
                        null -> Text("Revisando el permiso de Windows…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (host == null) {
                        Text(
                            "Este computador no está conectado a ninguna red. Conéctalo al Wi-Fi o al router de la iglesia.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium,
                        )
                        return@Column
                    }
                    if (addresses.size > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Red:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            for (a in addresses) FilterChip(a == host, { selected = a }, { Text(a) })
                        }
                    }

                    // 2. App Android
                    Section("Tablet o celular con la app Proyecta Remoto") {
                        Text("Solo hay que abrir la app: busca sola este computador ($pcName) y se conecta. No hay que escribir nada.")
                    }

                    // 3 y 4. QR
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        QrCard(
                            "Control desde el navegador",
                            "Cualquier celular (Android o iPhone), sin instalar nada: abre la cámara y escanea.",
                            "http://$host:$port", Modifier.weight(1f),
                        )
                        QrCard(
                            "Vista de escenario",
                            "Para músicos y predicador: estrofa actual, la que sigue, la hora y tus mensajes.",
                            "http://$host:$port/escenario.html", Modifier.weight(1f),
                        )
                    }

                    // 5. OBS
                    Section("Transmisión en vivo (OBS)") {
                        Text("En OBS agrega una \"Fuente de navegador\" con esta dirección (fondo transparente, solo la letra):")
                        CopyableUrl("http://$host:$port/obs.html")
                        Text(
                            "Opcional al final: ?bg=1 (franja oscura detrás del texto), ?pos=top o ?pos=center, ?size=60.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Todos los equipos deben estar en el mismo Wi-Fi que este computador. Las redes de \"invitados\" a veces no dejan que los equipos se vean entre sí.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun QrCard(title: String, subtitle: String, url: String, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = modifier) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.background(Color.White).padding(8.dp)) { QrCode(url, 220.dp) }
            CopyableUrl(url)
        }
    }
}

@Composable
private fun CopyableUrl(url: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(url) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SelectionContainer(Modifier.width(320.dp)) { Text(url, fontWeight = FontWeight.Medium) }
        TextButton({ clipboard.setText(AnnotatedString(url)); copied = true }) { Text(if (copied) "✓ Copiado" else "Copiar") }
    }
}
