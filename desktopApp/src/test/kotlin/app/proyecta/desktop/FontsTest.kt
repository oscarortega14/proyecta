package app.proyecta.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.use
import app.proyecta.shared.PresentationState
import app.proyecta.shared.Slide
import app.proyecta.shared.Theme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FontsTest {
    private val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
    private val text = "Señor mi Dios, al contemplar los cielos\nel firmamento y las estrellas mil ¿quién? ¡Él!"

    @Test
    fun everyBundledFontLoadsAndFits() {
        val sizes = Fonts.CHOICES.keys.associateWith { name ->
            val family = assertNotNull(Fonts.family(name), name)
            fitFontSizeMeasured(measurer, Density(1f), text, null, 900, 420, family = family)
        }
        println("TAMAÑOS $sizes")
        assertTrue(sizes.values.all { it > 25f })
        // La angosta (Oswald) deja la letra más grande que la de remates (Merriweather)
        assertTrue(sizes.getValue("Oswald") > sizes.getValue("Merriweather"))
        assertNull(Fonts.family(null))
        assertNull(Fonts.family("Comic Sans")) // nombre desconocido: la del sistema
    }

    /** PROYECTA_RENDER_DIR=… para ver cada tipografía */
    @Test
    fun renderFonts() {
        val out = System.getenv("PROYECTA_RENDER_DIR")?.let { File(it) } ?: return
        val s = PresentationState("x", listOf(Slide("Coro", text)), 0)
        for (name in Fonts.CHOICES.keys) {
            ImageComposeScene(640, 360, Density(1f)) { ProjectorScreen(s, look = Look(Theme(font = name), frozenTime = 2f)) }
                .use { File(out, "fuente-$name.png").writeBytes(it.render(0).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
        }
    }
}
