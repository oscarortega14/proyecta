package app.selah.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Un paso de la guía de bienvenida. */
data class WelcomeStep(val title: String, val body: String)

/** La guía que aparece la primera vez (y desde "?" → "Ver la guía"). */
val WELCOME_STEPS = listOf(
    WelcomeStep(
        "Bienvenido a Selah",
        "Selah muestra las letras de las canciones y la Biblia en el videobeam, y se controla desde este computador, " +
            "una tablet o un celular.\n\nSi hay un videobeam o una segunda pantalla conectada, la letra sale ahí sola. " +
            "Para cambiar de pantalla usa el botón \"Proyección\" de arriba.",
    ),
    WelcomeStep(
        "Proyectar una canción",
        "En la pestaña Canciones haz clic en una canción. Avanza con → , Espacio o el clicker de presentaciones; retrocede con ←.\n\n" +
            "Atajos: C va al coro, 1–9 a cada sección, B pone la pantalla en negro y Esc la vuelve a mostrar. " +
            "\"+ Nueva\" e \"Importar…\" agregan canciones (también desde OpenLP y Holyrics). " +
            "Arriba, \"Predicaciones\" guarda los puntos del sermón con sus versículos.",
    ),
    WelcomeStep(
        "La Biblia",
        "En la pestaña Biblia escribe una cita como \"Juan 3:16\" y presiona Enter, o explora por libro, capítulo y versículo.\n\n" +
            "Puedes pedir varias a la vez: \"Juan 3:16; Romanos 5:8\". Arriba eliges la traducción.",
    ),
    WelcomeStep(
        "La lista del culto",
        "Con \"+ Lista\" preparas el orden del culto: canciones y lecturas. Durante el culto presiona N (o \"Siguiente elemento\") para pasar al siguiente.\n\n" +
            "\"Guardar…\" la guarda con nombre para el domingo, y \"Abrir…\" la recupera.",
    ),
    WelcomeStep(
        "Desde el celular o la tablet",
        "En \"Conectar dispositivos\" hay un código QR: escanéalo con la cámara del celular y tendrás el control en el navegador " +
            "(en iPhone se puede agregar a la pantalla de inicio). En Android también está la app Selah Remoto, que encuentra este computador sola.\n\n" +
            "Para controlar se pide una clave de 4 números que está ahí mismo (el QR ya la trae): así nadie más en el Wi-Fi mueve la letra.",
    ),
    WelcomeStep(
        "Últimos detalles",
        "\"Fondos y letra\" cambia el fondo (hay animados) y el tamaño de la letra. \"Historial\" muestra qué se cantó cada domingo.\n\n" +
            "Si la proyección se cierra por error, presiona F5 para volver a abrirla. Esta guía está siempre en el botón \"?\".",
    ),
)

@Composable
fun WelcomeDialog(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val step = WELCOME_STEPS[page]
    val last = page == WELCOME_STEPS.lastIndex
    AlertDialog(
        onDismissRequest = {}, // solo se cierra con los botones
        modifier = Modifier.width(560.dp),
        title = { Text(step.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(step.body, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WELCOME_STEPS.indices.forEach { i ->
                        Box(
                            Modifier.size(8.dp).background(
                                if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button({ if (last) onDone() else page++ }) { Text(if (last) "Empezar" else "Siguiente") }
        },
        dismissButton = {
            Row {
                if (page > 0) TextButton({ page-- }) { Text("Anterior") }
                if (!last) TextButton(onDone) { Text("Saltar guía") }
            }
        },
    )
}
