package app.proyecta.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fondos incluidos en el programa. Se dibujan (no son fotos), así que se ven nítidos en
 * cualquier resolución y no tienen problemas de licencia. Las posiciones "al azar" usan una
 * semilla fija. Cada fondo recibe el tiempo t (segundos) y se mueve en ciclos suaves;
 * con t = 0 queda quieto (miniaturas y modo sin animación).
 */
object BuiltinBackgrounds {
    private val painters: Map<String, DrawScope.(Float) -> Unit> = mapOf(
        "luz" to { t -> luz(t) },
        "aurora" to { t -> aurora(t) },
        "bokeh" to { t -> bokeh(t) },
        "atardecer" to { t -> atardecer(t) },
        "noche" to { t -> noche(t) },
        "oceano" to { t -> oceano(t) },
        "calvario" to { t -> calvario(t) },
        "destellos" to { t -> destellos(t) },
    )

    fun has(key: String) = key in painters

    fun draw(scope: DrawScope, key: String, t: Float) { painters[key]?.invoke(scope, t) }
}

/** Cómo dibuja la ventana de proyección: con tarjeta gráfica (lo normal) o solo con el procesador. */
object RenderInfo {
    @Volatile var hardware: Boolean = false
}

/** Ancho al que se dibujan los fondos incluidos antes de ampliarlos (son suaves: no se nota). */
private const val RENDER_WIDTH = 640f

/** Los más difusos aguantan aún menos resolución sin que se note. */
private val RENDER_WIDTH_BY_KEY = mapOf("aurora" to 320f)

/** Lienzo reutilizable a baja resolución (uno por fondo en pantalla). */
private class LowResBuffer {
    var bitmap: ImageBitmap? = null
    val scope = CanvasDrawScope()
    fun bitmapFor(w: Int, h: Int): ImageBitmap =
        bitmap?.takeIf { it.width == w && it.height == h } ?: ImageBitmap(w, h).also { bitmap = it }
}

/**
 * Fondo incluido; si [time] es null queda quieto. Se dibuja a ~640 px de ancho y se amplía
 * con suavizado: unas 9 veces menos trabajo por cuadro en 1080p, para que la animación
 * no trabe un PC modesto.
 */
@Composable
fun BuiltinBackground(key: String, modifier: Modifier = Modifier, time: State<Float>? = null, direct: Boolean = false) {
    val buffer = remember { LowResBuffer() }
    Canvas(modifier.fillMaxSize()) {
        // Leer el tiempo dentro del dibujo: solo se repinta, no se recompone la pantalla.
        val t = time?.value ?: 0f
        if (direct) {
            // Con tarjeta gráfica: se dibuja directo a resolución completa (la GPU hace el trabajo).
            BuiltinBackgrounds.draw(this, key, t)
            return@Canvas
        }
        val scale = ((RENDER_WIDTH_BY_KEY[key] ?: RENDER_WIDTH) / size.width).coerceAtMost(1f)
        val bw = (size.width * scale).toInt().coerceAtLeast(1)
        val bh = (size.height * scale).toInt().coerceAtLeast(1)
        val bmp = buffer.bitmapFor(bw, bh)
        buffer.scope.draw(this, layoutDirection, ComposeCanvas(bmp), Size(bw.toFloat(), bh.toFloat())) {
            BuiltinBackgrounds.draw(this, key, t)
        }
        drawImage(bmp, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Medium)
    }
}

/**
 * Reloj de animación en segundos. Con [fps] >= 60 avanza en cada refresco de pantalla (suave en el
 * videobeam); con menos (vistas previas) espera entre cuadros para gastar menos.
 */
@Composable
fun rememberAnimationTime(running: Boolean, fps: Int = 60): State<Float> = produceState(0f, running, fps) {
    if (!running) return@produceState
    val start = withFrameNanos { it }
    // 60: cada refresco de pantalla. Menos: se espera lo que falte para el próximo cuadro.
    val gap = if (fps >= 60) 0L else 1_000L / fps.coerceAtLeast(1) - 8
    while (true) {
        withFrameNanos { value = (it - start) / 1_000_000_000f }
        if (gap > 0) delay(gap)
    }
}

private fun c(hex: Long, alpha: Float = 1f) = Color(0xFF000000 or hex).copy(alpha = alpha)

private fun DrawScope.vertical(colors: List<Color>) = drawRect(Brush.verticalGradient(colors))

private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) =
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center, radius), radius, center)

/** Onda suave entre 0 y 1 con período [period] segundos. */
private fun wave(t: Float, period: Float, phase: Float = 0f) = (0.5 + 0.5 * sin(2 * PI * t / period + phase)).toFloat()

/** Partículas sembradas: posición base, tamaño, velocidad y fase de cada una. */
private class Particle(val x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float, val tone: Int)

private fun particles(seed: Int, count: Int) = Random(seed).let { r ->
    List(count) { Particle(r.nextFloat(), r.nextFloat(), r.nextFloat(), 0.3f + r.nextFloat() * 0.7f, r.nextFloat() * 6.28f, r.nextInt(4)) }
}

private val BOKEH = particles(42, 55)
private val STARS = particles(3, 320)
private val AURORA_STARS = particles(7, 120)
private val EMBERS = particles(11, 45)
private val SPARKS = particles(21, 70)
private val DUST = particles(5, 40)

// --- Luz celestial: azul profundo, resplandor que respira, rayos que se mecen y polvo de luz ---
private fun DrawScope.luz(t: Float) {
    val (w, h) = size
    vertical(listOf(c(0x081430), c(0x16335F), c(0x26527E)))
    val top = Offset(w * 0.5f, -h * 0.08f)
    val sway = 3f * sin(2 * PI * t / 24).toFloat()
    for (i in -5..5) {
        val a = 0.07f + 0.05f * wave(t, 7f, i * 0.9f)
        rotate(i * 9f + sway, pivot = top) {
            val ray = Path().apply {
                moveTo(top.x, top.y)
                lineTo(top.x - w * 0.035f, h * 1.2f)
                lineTo(top.x + w * 0.035f, h * 1.2f)
                close()
            }
            drawPath(ray, Brush.verticalGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), 0f, h * 1.1f))
        }
    }
    glow(Offset(w * 0.5f, h * 0.02f), h * 0.95f, c(0xBFD8FF), 0.38f + 0.10f * wave(t, 9f))
    glow(Offset(w * 0.5f, h * 0.02f), h * 0.35f, Color.White, 0.45f + 0.08f * wave(t, 6f))
    floatingDust(t, DUST, c(0xDDE9FF), rise = true)
}

// --- Aurora: franjas que ondulan despacio sobre un cielo con estrellas ---
private fun DrawScope.aurora(t: Float) {
    val (w, h) = size
    vertical(listOf(c(0x01060F), c(0x061A2C), c(0x0B2638)))
    twinkle(t, AURORA_STARS, h * 0.0016f, 0.6f)
    val bands = listOf(c(0x2BE7A6) to 0.0, c(0x3FA7FF) to 1.7, c(0x9B5CFF) to 3.4)
    val drift = t * 0.06
    for ((color, phase) in bands) {
        var x = -w * 0.1f
        while (x < w * 1.1f) {
            val tt = x / w
            val y = h * (0.32f + 0.10f * sin(tt * 2 * PI * 1.2 + phase + drift).toFloat() + 0.04f * sin(tt * 2 * PI * 3.1 + phase - drift * 1.7).toFloat())
            glow(Offset(x, y), h * 0.24f, color, 0.09f + 0.035f * wave(t, 11f, (tt * 6).toFloat() + phase.toFloat()))
            x += w / 32f
        }
    }
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

// --- Bokeh dorado: luces desenfocadas que suben despacio y respiran ---
private fun DrawScope.bokeh(t: Float) {
    val (w, h) = size
    vertical(listOf(c(0x140A04), c(0x2E1708), c(0x120803)))
    glow(Offset(w * 0.3f, h * 0.6f), h * 0.9f, c(0x8A4B12), 0.30f + 0.08f * wave(t, 10f))
    val tones = listOf(c(0xFFC56B), c(0xFF9F43), c(0xFFE3A3), c(0xF5B041))
    for (p in BOKEH) {
        val radius = h * (0.02f + p.size * 0.10f)
        // sube y vuelve a entrar por abajo; se mece de lado a lado
        val y = ((p.y - t * 0.012f * p.speed) % 1f + 1f) % 1f
        val x = p.x + 0.015f * sin(t * 0.4f * p.speed + p.phase)
        val center = Offset(x * w, y * (h + 2 * radius) - radius)
        val color = tones[p.tone]
        val alpha = (0.08f + p.speed * 0.25f) * (0.65f + 0.35f * wave(t, 5f + p.speed * 4f, p.phase))
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.7f), color.copy(alpha = 0f)), center, radius),
            radius, center,
        )
    }
}

// --- Atardecer: cielo violeta a naranja, sol que late y colinas ---
private fun DrawScope.atardecer(t: Float) {
    val (w, h) = size
    vertical(listOf(c(0x24104F), c(0x6A2C86), c(0xC9536E), c(0xF29A5A)))
    glow(Offset(w * 0.5f, h * 0.74f), h * 0.75f, c(0xFFD08A), 0.48f + 0.10f * wave(t, 8f))
    drawCircle(c(0xFFE7B0, 0.9f), h * 0.07f, Offset(w * 0.5f, h * 0.74f))
    floatingDust(t, DUST, c(0xFFE2B8), rise = true)
    hills(h * 0.80f, c(0x2A1238), w, h, 0.05f)
    hills(h * 0.88f, c(0x170A20), w, h, -0.04f)
}

// --- Noche estrellada: estrellas que titilan y una vía láctea tenue ---
private fun DrawScope.noche(t: Float) {
    val (w, h) = size
    vertical(listOf(c(0x00040E), c(0x0A1736), c(0x1A2853)))
    var k = 0f
    while (k <= 1f) {
        glow(Offset(w * (0.1f + 0.8f * k), h * (0.85f - 0.75f * k)), h * 0.16f, c(0xB9C7FF), 0.045f + 0.01f * wave(t, 12f, k * 9))
        k += 0.02f
    }
    twinkle(t, STARS, h * 0.0022f, 1f)
}

// --- Océano: ondas que avanzan y luz que cae desde arriba ---
private fun DrawScope.oceano(t: Float) {
    val (w, h) = size
    vertical(listOf(c(0x02222F), c(0x054B63), c(0x0A7484)))
    glow(Offset(w * 0.5f, -h * 0.1f), h * 0.9f, c(0x9FF3FF), 0.26f + 0.08f * wave(t, 9f))
    for (k in 0 until 6) {
        val base = h * (0.45f + k * 0.1f)
        val shift = t * (0.15f + k * 0.05f)
        val wv = Path().apply {
            moveTo(0f, h)
            lineTo(0f, base)
            var x = 0f
            while (x <= w) {
                lineTo(x, base + h * 0.025f * sin((x / w) * 2 * PI * (1.5 + k * 0.3) + k + shift).toFloat())
                x += w / 80f
            }
            lineTo(w, h)
            close()
        }
        drawPath(wv, c(0x7FE3F0, 0.05f + k * 0.012f))
    }
}

// --- Calvario: amanecer que respira, brasas que suben y tres cruces ---
private fun DrawScope.calvario(t: Float) {
    val (w, h) = size
    vertical(listOf(c(0x1A1036), c(0x5E2A5E), c(0xD8705A), c(0xF5C27A)))
    glow(Offset(w * 0.62f, h * 0.62f), h * 0.8f, c(0xFFE0A0), 0.42f + 0.10f * wave(t, 8f))
    floatingDust(t, EMBERS, c(0xFFB066), rise = true, big = true)
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

// --- Destellos: brillos en forma de estrella que aparecen y se apagan sobre un fondo profundo ---
private fun DrawScope.destellos(t: Float) {
    val (w, h) = size
    vertical(listOf(c(0x0B0620), c(0x1C1046), c(0x2A1458)))
    glow(Offset(w * 0.5f, h * 0.5f), h * 0.9f, c(0x7A5CFF), 0.22f + 0.06f * wave(t, 10f))
    glow(Offset(w * 0.2f, h * 0.85f), h * 0.6f, c(0xFF6FB5), 0.12f)
    val tones = listOf(Color.White, c(0xFFE7A8), c(0xC9B8FF), c(0xA8E6FF))
    for (p in SPARKS) {
        // cada destello se enciende y se apaga a su ritmo; casi todo el tiempo está tenue
        val life = wave(t, 3f + p.speed * 4f, p.phase)
        val a = life * life * life
        if (a < 0.02f) continue
        val center = Offset(p.x * w, p.y * h)
        val r = h * (0.006f + p.size * 0.022f) * (0.6f + 0.4f * life)
        sparkle(center, r, tones[p.tone], a)
    }
}

/** Estrella de cuatro puntas con halo. */
private fun DrawScope.sparkle(center: Offset, r: Float, color: Color, alpha: Float) {
    glow(center, r * 3f, color, alpha * 0.35f)
    val p = Path().apply {
        moveTo(center.x, center.y - r * 2.2f)
        quadraticTo(center.x, center.y, center.x + r * 2.2f, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r * 2.2f)
        quadraticTo(center.x, center.y, center.x - r * 2.2f, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r * 2.2f)
        close()
    }
    drawPath(p, color.copy(alpha = alpha))
    drawCircle(Color.White.copy(alpha = alpha), r * 0.35f, center)
}

private fun DrawScope.twinkle(t: Float, stars: List<Particle>, maxRadius: Float, brightness: Float) {
    val (w, h) = size
    for (s in stars) {
        val r = max(0.6f, s.size * maxRadius)
        val base = 0.25f + s.speed * 0.75f
        val a = base * (0.55f + 0.45f * wave(t, 2f + s.speed * 5f, s.phase)) * brightness
        drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), r, Offset(s.x * w, s.y * h * 0.85f))
    }
}

/** Partículas pequeñas que suben (o bajan) despacio y vuelven a empezar. */
private fun DrawScope.floatingDust(t: Float, list: List<Particle>, color: Color, rise: Boolean, big: Boolean = false) {
    val (w, h) = size
    for (p in list) {
        val travel = t * 0.02f * p.speed
        val y = if (rise) ((p.y - travel) % 1f + 1f) % 1f else (p.y + travel) % 1f
        val x = p.x + 0.01f * sin(t * 0.5f * p.speed + p.phase)
        val r = h * (if (big) 0.002f + p.size * 0.004f else 0.0012f + p.size * 0.0025f)
        val a = (0.15f + 0.45f * p.speed) * wave(t, 4f + p.speed * 3f, p.phase)
        glow(Offset(x * w, y * h), r * 4f, color, a * 0.35f)
        drawCircle(color.copy(alpha = a), r, Offset(x * w, y * h))
    }
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
