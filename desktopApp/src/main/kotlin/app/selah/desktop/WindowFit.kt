package app.selah.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment

/**
 * Área útil de la pantalla principal (sin la barra de tareas), en dp. Con la pantalla al 125 %
 * un monitor de 1920×1080 da unos 1536×824: ventanas pensadas para 1440×820 ya no caben.
 */
fun usableScreen(): DpSize? = runCatching {
    if (GraphicsEnvironment.isHeadless()) return null
    val b = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    DpSize(b.width.dp, b.height.dp)
}.getOrNull()

/** El tamaño deseado, achicado si hace falta para caber (con un pequeño margen). */
fun fitSize(wanted: DpSize, screen: DpSize?): DpSize {
    if (screen == null) return wanted
    return DpSize(minOf(wanted.width, screen.width * 0.96f), minOf(wanted.height, screen.height * 0.96f))
}

/** ¿La ventana no cabe? Entonces mejor maximizada que cortada. */
fun doesNotFit(wanted: DpSize, screen: DpSize?): Boolean =
    screen != null && (wanted.width > screen.width || wanted.height > screen.height)

/**
 * Estado de ventana que siempre cabe en la pantalla, centrada.
 * [maximizeIfSmall]: si no cabe, se abre maximizada (la ventana principal).
 */
@Composable
fun rememberFittedWindowState(wanted: DpSize, maximizeIfSmall: Boolean = false): WindowState {
    val screen = usableScreen()
    return rememberWindowState(
        placement = if (maximizeIfSmall && doesNotFit(wanted, screen)) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = WindowPosition(Alignment.Center),
        size = fitSize(wanted, screen),
    )
}
