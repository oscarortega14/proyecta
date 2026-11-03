package app.proyecta.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlin.math.ceil

/** Tema actual y cómo encontrar el archivo de una imagen de fondo. */
data class Look(val theme: Theme = Theme(), val backgroundFile: (String) -> File? = { null })

val LocalLook = staticCompositionLocalOf { Look() }

@Composable
fun ProvideLook(look: Look, content: @Composable () -> Unit) = CompositionLocalProvider(LocalLook provides look, content = content)

/** Lo que se ve en el videobeam. También se usa en miniatura como vista previa. */
@Composable
fun ProjectorScreen(state: PresentationState, modifier: Modifier = Modifier, look: Look = LocalLook.current) {
    val theme = look.theme
    val imageName = state.background ?: theme.image
    val image = rememberBackground(imageName?.let(look.backgroundFile))

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val slide = state.current
        if (!state.blank) {
            Background(theme, image)
            if (slide != null) SlideText(state, slide.text, slide.label, theme, maxWidth, maxHeight)
        }
    }
}

@Composable
private fun Background(theme: Theme, image: ImageBitmap?) {
    val top = hexColor(theme.color)
    val bottom = theme.color2?.let(::hexColor)
    val fill = if (bottom != null) Modifier.background(Brush.verticalGradient(listOf(top, bottom))) else Modifier.background(top)
    Box(Modifier.fillMaxSize().then(fill))
    if (image != null) {
        Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = theme.dim)))
    }
}

@Composable
private fun SlideText(state: PresentationState, text: String, label: String, theme: Theme, width: Dp, height: Dp) {
    val pad = width * 0.05f
    val showLabel = state.isBible && label.isNotEmpty()
    // fontScale > 1 solo sube el tope (si el texto cabe); < 1 achica lo calculado.
    val size = fitFontSize(text, width - pad * 2, height - pad * 2, showLabel, maxSize = 110f * theme.fontScale.coerceAtLeast(1f)) *
        theme.fontScale.coerceAtMost(1f)
    val color = hexColor(theme.textColor)
    val shadow = if (theme.shadow) Shadow(Color.Black.copy(alpha = 0.9f), Offset(2f, 3f), blurRadius = size / 5) else null

    Column(
        Modifier.fillMaxSize().padding(pad),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = TextStyle(
                color = color,
                fontSize = size.sp,
                lineHeight = (size * 1.2f).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                shadow = shadow,
            ),
        )
        if (showLabel) {
            Spacer(Modifier.height((size * 0.4f).dp))
            Text(
                label,
                style = TextStyle(color = color.copy(alpha = 0.75f), fontSize = (size * 0.55f).sp, textAlign = TextAlign.Center, shadow = shadow),
            )
        }
    }
}

/**
 * Tamaño de letra más grande con el que el texto cabe en el área.
 * Estimación: un carácter mide ~0.55 del tamaño de la fuente y una línea 1.2.
 */
internal fun fitFontSize(text: String, width: Dp, height: Dp, withLabel: Boolean, maxSize: Float = 110f): Float {
    val lines = text.split('\n')
    var size = (height.value / 3f).coerceAtMost(maxSize)
    while (size > 10f) {
        val charsPerLine = (width.value / (size * 0.55f)).coerceAtLeast(1f)
        val wrapped = lines.sumOf { ceil(it.length.coerceAtLeast(1) / charsPerLine).toInt() }
        val needed = wrapped * size * 1.2f + if (withLabel) size * 1.1f else 0f
        if (needed <= height.value) return size
        size -= 2f
    }
    return 10f
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
