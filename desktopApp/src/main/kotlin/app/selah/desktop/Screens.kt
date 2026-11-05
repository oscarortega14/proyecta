package app.selah.desktop

import java.awt.GraphicsEnvironment

/** Un monitor o videobeam conectado. */
data class ScreenInfo(
    val id: String,
    val number: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val primary: Boolean,
) {
    val label: String get() = "Pantalla $number" + (if (primary) " (principal)" else "") + " · ${width}×$height"
}

object Screens {
    /** Pantallas conectadas ahora mismo (la lista cambia al enchufar o desenchufar el videobeam). */
    fun list(): List<ScreenInfo> = runCatching {
        val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val primary = env.defaultScreenDevice
        env.screenDevices.mapIndexed { i, d ->
            val b = d.defaultConfiguration.bounds
            ScreenInfo(d.iDstring, i + 1, b.x, b.y, b.width, b.height, d == primary)
        }
    }.getOrDefault(emptyList())
}

/**
 * Dónde va la proyección. null = en una ventana normal (para probar con un solo monitor).
 * - Si el operador eligió una pantalla y está conectada, esa.
 * - Si eligió "en una ventana", ventana.
 * - Si no eligió (o su pantalla no está conectada), la primera que no sea la principal.
 */
fun chooseProjectorScreen(screens: List<ScreenInfo>, saved: String?): ScreenInfo? {
    if (saved == Settings.WINDOWED) return null
    screens.firstOrNull { it.id == saved }?.let { return it }
    return screens.firstOrNull { !it.primary }
}
