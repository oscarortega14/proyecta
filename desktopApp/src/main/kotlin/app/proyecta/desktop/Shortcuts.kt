package app.proyecta.desktop

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import app.proyecta.shared.ClientMessage
import app.proyecta.shared.PresentationState
import app.proyecta.shared.normalizeKey

/** Mientras se escribe en un campo de texto, los atajos de una tecla no actúan. */
object Typing {
    @Volatile var active = false
}

/** Marca un campo de texto para que los atajos no le roben las teclas. */
fun Modifier.marksTyping(): Modifier = onFocusChanged { Typing.active = it.isFocused }

/** Atajos para la ventana de ayuda (?). */
val SHORTCUTS = listOf(
    "→  ↓  Espacio" to "Siguiente estrofa",
    "←  ↑" to "Estrofa anterior",
    "Av Pág / Re Pág" to "Siguiente / anterior (clickers de presentación)",
    "C" to "Ir al coro",
    "1 … 9" to "Ir a la sección 1, 2, 3…",
    "B  o  Ctrl+B" to "Pantalla negra",
    "L" to "Logo de la iglesia",
    "N" to "Siguiente elemento de la lista del culto",
    "Esc" to "Mostrar la letra (o salir de un campo de texto)",
    "F5" to "Volver a encender la proyección",
)

/** Texto de ayuda que se muestra en la ventana del operador. */
const val SHORTCUTS_HELP =
    "Teclado: → ↓ Espacio siguiente · ← ↑ anterior · C coro · 1–9 secciones · B negro · L logo · N siguiente de la lista · Esc mostrar letra"

/** F5: volver a abrir la ventana de proyección (si se cerró o se apagó). */
fun isReopenProjectorKey(e: KeyEvent): Boolean = reopensProjector(e.type, e.key)

internal fun reopensProjector(type: KeyEventType, key: Key): Boolean = type == KeyEventType.KeyDown && key == Key.F5

/**
 * Atajos al estilo Holyrics. Av Pág / Re Pág (los "clickers") y Ctrl+B funcionan siempre;
 * el resto solo cuando no se está escribiendo.
 */
fun handleShortcut(e: KeyEvent, commands: Commands): Boolean {
    if (e.type != KeyEventType.KeyDown || e.isAltPressed) return false
    val state = commands.presenter.state.value
    val msg: ClientMessage? = when {
        e.key == Key.PageDown -> ClientMessage.Next
        e.key == Key.PageUp -> ClientMessage.Prev
        e.isCtrlPressed && e.key == Key.B -> ClientMessage.SetBlank(!state.blank)
        e.isCtrlPressed || Typing.active -> null
        else -> singleKey(e.key, state)
    }
    msg ?: return false
    commands.handle(msg)
    return true
}

internal fun singleKey(key: Key, state: PresentationState): ClientMessage? = when (key) {
    Key.DirectionRight, Key.DirectionDown, Key.Spacebar -> ClientMessage.Next
    Key.DirectionLeft, Key.DirectionUp -> ClientMessage.Prev
    Key.B, Key.Period -> ClientMessage.SetBlank(!state.blank)
    Key.L -> ClientMessage.ShowLogo(!state.logo)
    Key.N -> ClientMessage.PlanNext
    Key.Escape -> if (state.blank) ClientMessage.SetBlank(false) else if (state.logo) ClientMessage.ShowLogo(false) else null
    Key.C -> state.sectionLabels.firstOrNull { normalizeKey(it).startsWith("coro") }
        ?.let(state::jumpTarget)?.let { ClientMessage.GoTo(it) }
    else -> DIGITS[key]?.let { n -> state.sectionLabels.getOrNull(n - 1)?.let(state::jumpTarget)?.let { ClientMessage.GoTo(it) } }
}

private val DIGITS = mapOf(
    Key.One to 1, Key.Two to 2, Key.Three to 3, Key.Four to 4, Key.Five to 5,
    Key.Six to 6, Key.Seven to 7, Key.Eight to 8, Key.Nine to 9,
    Key.NumPad1 to 1, Key.NumPad2 to 2, Key.NumPad3 to 3, Key.NumPad4 to 4, Key.NumPad5 to 5,
    Key.NumPad6 to 6, Key.NumPad7 to 7, Key.NumPad8 to 8, Key.NumPad9 to 9,
)
