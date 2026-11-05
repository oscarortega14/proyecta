package app.selah.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import kotlin.test.Test
import kotlin.test.assertTrue

/** La letra debe caber SIEMPRE en el área, sin importar el texto ni el tamaño de ventana. */
class FitTextTest {
    private fun withMeasurer(block: (TextMeasurer) -> Unit) {
        var m: TextMeasurer? = null
        ImageComposeScene(100, 100) { m = rememberTextMeasurer() }.use { it.render() }
        block(m!!)
    }

    private val cases = listOf(
        // el caso reportado: palabras largas al final que la estimación por caracteres no veía
        "Con la fe en Jesucristo tengo el poder\nUn poder como ninguno, tesoro invaluable" to null,
        "Porque de tal manera amó Dios al mundo, que ha dado á su Hijo unigénito, para que todo aquel que en él cree, no se pierda, mas tenga vida eterna." to "Juan 3:16",
        "Te alabaré oh Jehová con todo mi corazón\nCantaré y contaré tus maravillas, me alegraré y me regocijaré\nCantaré a tu Nombre Altísimo" to null,
        "Bienaventurados\nlos de limpio corazón" to null,
        "Inconmensurablemente" to null,
    )

    @Test
    fun alwaysFitsInsideTheArea() = withMeasurer { measurer ->
        for (density in listOf(Density(1f), Density(1.25f), Density(1.5f))) {
            for ((w, h) in listOf(1180 to 630, 1920 to 1080, 800 to 450, 1024 to 768, 400 to 300)) {
                for ((text, label) in cases) {
                    val size = fitFontSizeMeasured(measurer, density, text, label, w, h)
                    val style = slideStyle(size)
                    var height = measurer.measure(text, style, constraints = Constraints(maxWidth = w), density = density).size.height
                    if (label != null) {
                        height += measurer.measure(label, labelStyle(size), constraints = Constraints(maxWidth = w), density = density).size.height
                        height += with(density) { (size * 0.4f).sp.roundToPx() }
                    }
                    assertTrue(height <= h, "No cabe: ${w}x$h d=${density.density} size=$size alto=$height «${text.take(30)}»")
                    for (word in text.split(Regex("\\s+"))) {
                        val ww = measurer.measure(word, style, constraints = Constraints(), density = density).size.width
                        assertTrue(ww <= w, "La palabra «$word» se partiría (${ww}px > ${w}px)")
                    }
                }
            }
        }
    }

    @Test
    fun usesTheSpaceWell() = withMeasurer { measurer ->
        // una línea corta en pantalla grande llega al tope; no se queda pequeña
        assertTrue(fitFontSizeMeasured(measurer, Density(1f), "Aleluya", null, 1820, 980) >= 109f)
        // el caso reportado sigue siendo grande y legible
        val size = fitFontSizeMeasured(measurer, Density(1f), cases[0].first, null, 1062, 567)
        assertTrue(size in 40f..110f, "tamaño $size")
    }
}
