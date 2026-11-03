package app.proyecta.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fondos incluidos en el programa. Se dibujan (no son fotos), así que se ven nítidos en
 * cualquier resolución y no tienen problemas de licencia. Las posiciones "al azar" usan una
 * semilla fija: el fondo siempre se ve igual.
 */
object BuiltinBackgrounds {
    private val painters: Map<String, DrawScope.() -> Unit> = mapOf(
        "luz" to { luz() },
        "aurora" to { aurora() },
        "bokeh" to { bokeh() },
        "atardecer" to { atardecer() },
        "noche" to { noche() },
        "oceano" to { oceano() },
        "calvario" to { calvario() },
    )

    fun has(key: String) = key in painters

    fun draw(scope: DrawScope, key: String) { painters[key]?.invoke(scope) }
}

@Composable
fun BuiltinBackground(key: String, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) { BuiltinBackgrounds.draw(this, key) }
}

private fun c(hex: Long, alpha: Float = 1f) = Color(0xFF000000 or hex).copy(alpha = alpha)

private fun DrawScope.vertical(colors: List<Color>) = drawRect(Brush.verticalGradient(colors))

private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) =
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center, radius), radius, center)

// --- Luz celestial: azul profundo con un resplandor y rayos que bajan desde arriba ---
private fun DrawScope.luz() {
    val (w, h) = size
    vertical(listOf(c(0x081430), c(0x16335F), c(0x26527E)))
    val top = Offset(w * 0.5f, -h * 0.08f)
    for (i in -5..5) {
        rotate(i * 9f, pivot = top) {
            val ray = Path().apply {
                moveTo(top.x, top.y)
                lineTo(top.x - w * 0.035f, h * 1.2f)
                lineTo(top.x + w * 0.035f, h * 1.2f)
                close()
            }
            drawPath(ray, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), 0f, h * 1.1f))
        }
    }
    glow(Offset(w * 0.5f, h * 0.02f), h * 0.95f, c(0xBFD8FF), 0.45f)
    glow(Offset(w * 0.5f, h * 0.02f), h * 0.35f, Color.White, 0.5f)
}

// --- Aurora: franjas verdes, turquesa y violeta sobre un cielo nocturno ---
private fun DrawScope.aurora() {
    val (w, h) = size
    vertical(listOf(c(0x01060F), c(0x061A2C), c(0x0B2638)))
    stars(Random(7), 120, h * 0.0016f, 0.6f)
    val bands = listOf(c(0x2BE7A6) to 0.0, c(0x3FA7FF) to 1.7, c(0x9B5CFF) to 3.4)
    for ((color, phase) in bands) {
        var x = -w * 0.1f
        while (x < w * 1.1f) {
            val t = x / w
            val y = h * (0.32f + 0.10f * sin(t * 2 * PI * 1.2 + phase).toFloat() + 0.04f * sin(t * 2 * PI * 3.1 + phase).toFloat())
            glow(Offset(x, y), h * 0.22f, color, 0.07f)
            x += w / 60f
        }
    }
    // suelo oscuro con montañas suaves
    val ground = Path().apply {
        moveTo(0f, h)
        lineTo(0f, h * 0.86f)
        quadraticTo(w * 0.25f, h * 0.78f, w * 0.5f, h * 0.86f)
        quadraticTo(w * 0.75f, h * 0.93f, w, h * 0.82f)
        lineTo(w, h)
        close()
    }
    drawPath(ground, c(0x02070C))
}

// --- Bokeh dorado: luces desenfocadas cálidas ---
private fun DrawScope.bokeh() {
    val (w, h) = size
    vertical(listOf(c(0x140A04), c(0x2E1708), c(0x120803)))
    glow(Offset(w * 0.3f, h * 0.6f), h * 0.9f, c(0x8A4B12), 0.35f)
    val rnd = Random(42)
    repeat(55) {
        val center = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
        val radius = h * (0.02f + rnd.nextFloat() * 0.10f)
        val color = listOf(c(0xFFC56B), c(0xFF9F43), c(0xFFE3A3), c(0xF5B041))[rnd.nextInt(4)]
        val alpha = 0.08f + rnd.nextFloat() * 0.30f
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.7f), color.copy(alpha = 0f)), center, radius),
            radius, center,
        )
    }
}

// --- Atardecer: cielo violeta a naranja, sol y colinas ---
private fun DrawScope.atardecer() {
    val (w, h) = size
    vertical(listOf(c(0x24104F), c(0x6A2C86), c(0xC9536E), c(0xF29A5A)))
    glow(Offset(w * 0.5f, h * 0.74f), h * 0.75f, c(0xFFD08A), 0.55f)
    drawCircle(c(0xFFE7B0, 0.9f), h * 0.07f, Offset(w * 0.5f, h * 0.74f))
    hills(h * 0.80f, c(0x2A1238), w, h, 0.05f)
    hills(h * 0.88f, c(0x170A20), w, h, -0.04f)
}

// --- Noche estrellada: muchas estrellas y una vía láctea tenue ---
private fun DrawScope.noche() {
    val (w, h) = size
    vertical(listOf(c(0x00040E), c(0x0A1736), c(0x1A2853)))
    var t = 0f
    while (t <= 1f) {
        glow(Offset(w * (0.1f + 0.8f * t), h * (0.85f - 0.75f * t)), h * 0.16f, c(0xB9C7FF), 0.05f)
        t += 0.02f
    }
    stars(Random(3), 320, h * 0.0022f, 1f)
}

// --- Océano: turquesa con luz desde arriba y ondas suaves ---
private fun DrawScope.oceano() {
    val (w, h) = size
    vertical(listOf(c(0x02222F), c(0x054B63), c(0x0A7484)))
    glow(Offset(w * 0.5f, -h * 0.1f), h * 0.9f, c(0x9FF3FF), 0.30f)
    for (k in 0 until 6) {
        val base = h * (0.45f + k * 0.1f)
        val wave = Path().apply {
            moveTo(0f, h)
            var x = 0f
            lineTo(0f, base)
            while (x <= w) {
                lineTo(x, base + h * 0.025f * sin((x / w) * 2 * PI * (1.5 + k * 0.3) + k).toFloat())
                x += w / 80f
            }
            lineTo(w, h)
            close()
        }
        drawPath(wave, c(0x7FE3F0, 0.05f + k * 0.012f))
    }
}

// --- Calvario: amanecer con tres cruces sobre una colina ---
private fun DrawScope.calvario() {
    val (w, h) = size
    vertical(listOf(c(0x1A1036), c(0x5E2A5E), c(0xD8705A), c(0xF5C27A)))
    glow(Offset(w * 0.62f, h * 0.62f), h * 0.8f, c(0xFFE0A0), 0.5f)
    val hill = Path().apply {
        moveTo(0f, h)
        lineTo(0f, h * 0.84f)
        quadraticTo(w * 0.42f, h * 0.70f, w * 0.62f, h * 0.68f)
        quadraticTo(w * 0.85f, h * 0.70f, w, h * 0.80f)
        lineTo(w, h)
        close()
    }
    val dark = c(0x160A16)
    drawPath(hill, dark)
    cross(Offset(w * 0.62f, h * 0.685f), h * 0.26f, dark)
    cross(Offset(w * 0.545f, h * 0.70f), h * 0.18f, dark)
    cross(Offset(w * 0.695f, h * 0.70f), h * 0.18f, dark)
}

private fun DrawScope.cross(base: Offset, height: Float, color: Color) {
    val thick = height * 0.07f
    drawRect(color, Offset(base.x - thick / 2, base.y - height), Size(thick, height + thick))
    val arm = height * 0.55f
    drawRect(color, Offset(base.x - arm / 2, base.y - height * 0.75f), Size(arm, thick))
}

private fun DrawScope.hills(y: Float, color: Color, w: Float, h: Float, tilt: Float) {
    val p = Path().apply {
        moveTo(0f, h)
        lineTo(0f, y + h * tilt)
        quadraticTo(w * 0.3f, y - h * 0.06f, w * 0.55f, y)
        quadraticTo(w * 0.8f, y + h * 0.05f, w, y - h * tilt)
        lineTo(w, h)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.stars(rnd: Random, count: Int, maxRadius: Float, brightness: Float) {
    val (w, h) = size
    repeat(count) {
        val r = max(0.6f, rnd.nextFloat() * maxRadius)
        drawCircle(Color.White.copy(alpha = (0.25f + rnd.nextFloat() * 0.75f) * brightness), r, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.85f))
    }
}
