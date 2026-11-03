package app.proyecta.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.proyecta.shared.PresentationState
import kotlin.math.ceil

/** Lo que se ve en el videobeam. También se usa en miniatura como vista previa. */
@Composable
fun ProjectorScreen(state: PresentationState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val slide = state.current
        if (state.blank || slide == null) return@BoxWithConstraints

        val pad = maxWidth * 0.05f
        val showLabel = state.isBible && slide.label.isNotEmpty()
        val size = fitFontSize(slide.text, maxWidth - pad * 2, maxHeight - pad * 2, showLabel)

        Column(
            Modifier.fillMaxSize().padding(pad),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                slide.text,
                color = Color.White,
                fontSize = size.sp,
                lineHeight = (size * 1.2f).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (showLabel) {
                Spacer(Modifier.height((size * 0.4f).dp))
                Text(slide.label, color = Color(0xFFBBBBBB), fontSize = (size * 0.55f).sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Tamaño de letra más grande con el que el texto cabe en el área.
 * Estimación: un carácter mide ~0.55 del tamaño de la fuente y una línea 1.2.
 */
internal fun fitFontSize(text: String, width: Dp, height: Dp, withLabel: Boolean): Float {
    val lines = text.split('\n')
    val maxSize = (height.value / 3f).coerceAtMost(110f)
    var size = maxSize
    while (size > 10f) {
        val charsPerLine = (width.value / (size * 0.55f)).coerceAtLeast(1f)
        val wrapped = lines.sumOf { ceil(it.length.coerceAtLeast(1) / charsPerLine).toInt() }
        val needed = wrapped * size * 1.2f + if (withLabel) size * 1.1f else 0f
        if (needed <= height.value) return size
        size -= 2f
    }
    return 10f
}
