package app.proyecta.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.proyecta.shared.PresentationState
import app.proyecta.shared.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Tema actual y cómo encontrar el archivo de una imagen de fondo. */
data class Look(
    val theme: Theme = Theme(),
    val backgroundFile: (String) -> File? = { null },
    /** Si no es null, la animación queda fija en este instante (pruebas y miniaturas). */
    val frozenTime: Float? = null,
    /** Vista previa (miniatura): el video se redibuja menos veces por segundo. */
    val preview: Boolean = false,
)

val LocalLook = staticCompositionLocalOf { Look() }

@Composable
fun ProvideLook(look: Look, content: @Composable () -> Unit) = CompositionLocalProvider(LocalLook provides look, content = content)

/** Lo que se ve en el videobeam. También se usa en miniatura como vista previa. */
@Composable
fun ProjectorScreen(state: PresentationState, modifier: Modifier = Modifier, look: Look = LocalLook.current) {
    val theme = look.theme
    val imageName = state.background ?: theme.image
    val bgFile = imageName?.let(look.backgroundFile)
    // Un video solo se usa si VLC está disponible; si no, se cae al fondo incluido o al color.
    val video = bgFile?.takeIf { isVideoFile(it.name) && VideoBackgrounds.available }
    val image = rememberBackground(bgFile?.takeIf { !isVideoFile(it.name) })

    val logo = rememberBackground(theme.logo?.let(look.backgroundFile))
    val now = rememberClock(active = state.countdownEnd != null)
    val seconds = state.countdownSeconds(now)

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val slide = state.current
        if (!state.blank) {
            Background(theme, image, video, look.frozenTime, look.preview)
            when {
                seconds != null && seconds > 0 -> Countdown(state.countdownText, seconds, theme, maxWidth, maxHeight)
                state.logo -> if (logo != null) Logo(logo, maxHeight)
                slide != null -> {
                    // Con alerta, la letra deja libre la franja de abajo.
                    val w = maxWidth
                    val h = maxHeight
                    val bottom = if (state.alert.isNotEmpty()) alertBarHeight(h) else 0.dp
                    Box(Modifier.fillMaxSize().padding(bottom = bottom)) {
                        SlideText(state, slide.text, slide.label, theme, w, h - bottom)
                    }
                }
            }
            if (state.alert.isNotEmpty()) AlertBar(state.alert, maxHeight)
        }
    }
}

/** Hora actual que se actualiza 4 veces por segundo mientras [active]. */
@Composable
private fun rememberClock(active: Boolean): Long {
    val now by produceState(System.currentTimeMillis(), active) {
        while (active) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(250) }
    }
    return now
}

@Composable
private fun Countdown(text: String, seconds: Long, theme: Theme, width: Dp, height: Dp) {
    val color = hexColor(theme.textColor)
    val shadow = if (theme.shadow) Shadow(Color.Black.copy(alpha = 0.9f), Offset(2f, 3f), blurRadius = 12f) else null
    val clockSize = (height.value * 0.32f).coerceAtMost(width.value * 0.22f)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (text.isNotEmpty()) {
            Text(text, style = TextStyle(color = color, fontSize = (clockSize * 0.28f).sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, shadow = shadow))
        }
        Text(
            app.proyecta.shared.formatClock(seconds),
            style = TextStyle(color = color, fontSize = clockSize.sp, fontWeight = FontWeight.Bold, shadow = shadow,
                fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun Logo(logo: ImageBitmap, height: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(logo, contentDescription = "Logo", modifier = Modifier.height(height * 0.55f), contentScale = ContentScale.Fit)
    }
}

private fun alertFontSize(height: Dp) = (height.value * 0.055f).coerceIn(12f, 60f)

/** Alto de la franja de alerta: letra + relleno arriba y abajo. */
internal fun alertBarHeight(height: Dp): Dp = (alertFontSize(height) * 1.35f + alertFontSize(height) * 0.7f).dp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AlertBar(text: String, height: Dp) {
    val size = alertFontSize(height)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.fillMaxWidth().background(Color(0xE6B71C1C)).padding(vertical = (size * 0.35f).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE).padding(horizontal = (size * 0.6f).dp),
                style = TextStyle(color = Color.White, fontSize = size.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun Background(theme: Theme, image: ImageBitmap?, video: File?, frozenTime: Float?, preview: Boolean) {
    val top = hexColor(theme.color)
    val bottom = theme.color2?.let(::hexColor)
    val fill = if (bottom != null) Modifier.background(Brush.verticalGradient(listOf(top, bottom))) else Modifier.background(top)
    Box(Modifier.fillMaxSize().then(fill))
    // Prioridad: video o imagen (de la canción o del tema) > fondo incluido > color/degradado.
    val builtin = theme.builtin?.takeIf { BuiltinBackgrounds.has(it) }
    when {
        video != null -> VideoBackground(video, maxFps = if (preview) 10 else 30)
        image != null -> Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        builtin != null -> {
            val animated = theme.animate && frozenTime == null
            // Con tarjeta gráfica: dibujo directo a 30 cuadros (60 con "fluidez máxima"). Sin ella: baja resolución a 24.
            val gpu = RenderInfo.hardware && !preview
            val clock = rememberAnimationTime(animated, fps = if (preview) 15 else if (gpu && theme.smooth) 60 else if (gpu) 30 else 24)
            BuiltinBackground(builtin, time = if (frozenTime != null) androidx.compose.runtime.mutableStateOf(frozenTime) else clock, direct = gpu)
        }
    }
    if (video != null || image != null || builtin != null) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = theme.dim)))
    }
}

@Composable
private fun SlideText(state: PresentationState, text: String, label: String, theme: Theme, width: Dp, height: Dp) {
    val pad = width * 0.05f
    val showLabel = state.isBible && label.isNotEmpty()
    val color = hexColor(theme.textColor)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val availW = with(density) { (width - pad * 2).roundToPx() }.coerceAtLeast(1)
    val availH = with(density) { (height - pad * 2).roundToPx() }.coerceAtLeast(1)
    // fontScale > 1 solo sube el tope (si el texto cabe); < 1 achica lo calculado.
    val maxSize = 110f * theme.fontScale.coerceAtLeast(1f)
    val size = remember(text, label, showLabel, availW, availH, maxSize, density) {
        fitFontSizeMeasured(measurer, density, text, if (showLabel) label else null, availW, availH, maxSize)
    } * theme.fontScale.coerceAtMost(1f)
    val shadow = if (theme.shadow) Shadow(Color.Black.copy(alpha = 0.9f), Offset(2f, 3f), blurRadius = size / 5) else null

    Column(
        Modifier.fillMaxSize().padding(pad),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = slideStyle(size).copy(color = color, shadow = shadow))
        if (showLabel) {
            Spacer(Modifier.height(with(density) { (size * LABEL_GAP).sp.toDp() }))
            Text(label, style = labelStyle(size).copy(color = color.copy(alpha = 0.75f), shadow = shadow))
        }
    }
}

private const val LABEL_GAP = 0.4f

internal fun slideStyle(size: Float) = TextStyle(
    fontSize = size.sp,
    lineHeight = (size * 1.2f).sp,
    fontWeight = FontWeight.SemiBold,
    textAlign = TextAlign.Center,
)

internal fun labelStyle(size: Float) = TextStyle(fontSize = (size * 0.55f).sp, textAlign = TextAlign.Center)

/**
 * El tamaño de letra más grande (en sp) con el que el texto cabe de verdad en [widthPx]×[heightPx].
 * Mide con el mismo motor que dibuja, así que el corte por palabras está incluido. Además
 * exige que ninguna palabra sea más ancha que el área, para no partir palabras a la mitad.
 */
internal fun fitFontSizeMeasured(
    measurer: TextMeasurer,
    density: Density,
    text: String,
    label: String?,
    widthPx: Int,
    heightPx: Int,
    maxSize: Float = 110f,
    minSize: Float = 8f,
): Float {
    val longestWords = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.sortedByDescending { it.length }.take(3)
    val unbounded = Constraints()
    fun fits(sp: Float): Boolean {
        val style = slideStyle(sp)
        if (longestWords.any { measurer.measure(it, style, constraints = unbounded, density = density).size.width > widthPx }) return false
        var h = measurer.measure(text, style, constraints = Constraints(maxWidth = widthPx), density = density).size.height
        if (label != null) {
            h += measurer.measure(label, labelStyle(sp), constraints = Constraints(maxWidth = widthPx), density = density).size.height
            h += with(density) { (sp * LABEL_GAP).sp.roundToPx() }
        }
        return h <= heightPx
    }
    if (fits(maxSize)) return maxSize
    var lo = minSize
    var hi = maxSize
    repeat(12) {
        val mid = (lo + hi) / 2
        if (fits(mid)) lo = mid else hi = mid
    }
    return lo
}

/** "#RRGGBB" -> Color (negro si está mal escrito). */
fun hexColor(hex: String): Color =
    hex.removePrefix("#").toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: Color.Black

/** Imágenes de fondo ya decodificadas (se vuelven a leer si el archivo cambia). */
object BackgroundImages {
    private val cache = ConcurrentHashMap<String, Pair<Long, ImageBitmap>>()

    fun cached(file: File): ImageBitmap? = cache[file.absolutePath]?.takeIf { it.first == file.lastModified() }?.second

    fun load(file: File): ImageBitmap? {
        val key = file.absolutePath
        val modified = file.lastModified()
        cache[key]?.takeIf { it.first == modified }?.let { return it.second }
        return runCatching { org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap() }
            .getOrNull()
            ?.also { cache[key] = modified to it }
    }
}

/** Carga la imagen fuera del hilo de la interfaz para no trabar la proyección. */
@Composable
fun rememberBackground(file: File?): ImageBitmap? {
    val image by produceState(file?.let { BackgroundImages.cached(it) }, file) {
        value = file?.let { withContext(Dispatchers.IO) { BackgroundImages.load(it) } }
    }
    return image
}
