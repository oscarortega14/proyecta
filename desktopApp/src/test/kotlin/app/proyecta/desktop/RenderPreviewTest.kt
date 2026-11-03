package app.proyecta.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import app.proyecta.shared.PresentationState
import app.proyecta.shared.Slide
import app.proyecta.shared.Theme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Dibuja la proyección fuera de pantalla y guarda PNGs para revisarlos a ojo.
 * Se omite si no se indica carpeta:
 *   PROYECTA_RENDER_DIR=/tmp/out PROYECTA_BG=/ruta/fondo.png ./gradlew :desktopApp:test --tests '*RenderPreviewTest*'
 */
class RenderPreviewTest {
    @Test
    fun renderPreviews() {
        val outDir = System.getenv("PROYECTA_RENDER_DIR")?.let(::File) ?: return
        outDir.mkdirs()
        val bg = System.getenv("PROYECTA_BG")?.let(::File)
        bg?.let { BackgroundImages.load(it) } // precargar para que se vea en el primer cuadro

        val song = PresentationState(
            "Alabad a Jehová",
            listOf(Slide("Coro", "Alabad a Jehová con todo tu corazón\nViviré para Él, Señor, cantando salmos de gratitud")),
            index = 0,
        )
        val verse = PresentationState("Juan 3:16", listOf(Slide("Juan 3:16", "Porque de tal manera amó Dios al mundo, que ha dado á su Hijo unigénito, para que todo aquel que en él cree, no se pierda, mas tenga vida eterna.")), 0, isBible = true)

        val cases = listOf(
            "1-negro" to (song to Theme()),
            "2-azul-noche" to (song to Theme.PRESETS[1].second),
            "3-imagen" to (song to Theme(image = "bg", dim = 0.35f)),
            "4-imagen-crema-biblia" to (verse to Theme(image = "bg", dim = 0.5f, textColor = "#FFF4D6")),
            "5-letra-pequena-sin-sombra" to (song to Theme.PRESETS[2].second.copy(fontScale = 0.7f, shadow = false)),
            "6-cuenta-regresiva" to (song.copy(countdownEnd = System.currentTimeMillis() + 300_000, countdownText = "Comenzamos en") to Theme(image = "bg")),
            "7-logo" to (song.copy(logo = true) to Theme(image = "bg", logo = "bg")),
            "8-alerta" to (song.copy(alert = "Se busca al dueño del carro de placas ABC123, favor acercarse a la entrada") to Theme.PRESETS[1].second),
        )
        for ((name, pair) in cases) {
            val (state, theme) = pair
            val look = Look(theme) { bg }
            ImageComposeScene(1280, 720, Density(1f)) { ProjectorScreen(state, look = look) }.use { scene ->
                scene.render(0)
                val png = scene.render(100_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(outDir, "$name.png").writeBytes(png)
            }
        }
    }
}
