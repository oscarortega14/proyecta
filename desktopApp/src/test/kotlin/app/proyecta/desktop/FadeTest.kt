package app.proyecta.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import app.proyecta.shared.PresentationState
import app.proyecta.shared.ProtocolJson
import app.proyecta.shared.Slide
import app.proyecta.shared.Theme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.test.assertTrue

class FadeTest {
    private val state = PresentationState("A", listOf(Slide("", "UNO"), Slide("", "DOS")), 0)
    private val plain = Theme(builtin = null, color = "#000000", shadow = false)

    /** Brillo promedio de la imagen (letra blanca sobre negro). */
    private fun brightness(img: org.jetbrains.skia.Image): Double {
        val bmp = org.jetbrains.skia.Bitmap.makeFromImage(img)
        var sum = 0L
        for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) sum += (bmp.getColor(x, y) shr 8) and 0xFF
        return sum.toDouble() / ((bmp.width / 2) * (bmp.height / 2))
    }

    @Test
    fun slidesFadeIntoEachOther() {
        var s by mutableStateOf(state)
        ImageComposeScene(320, 180, Density(1f)) { ProjectorScreen(s, look = Look(plain)) }.use { scene ->
            val start = brightness(scene.render(0))
            s = state.copy(index = 1)
            // Cuadro a cuadro (cada 20 ms): durante el fundido las dos letras están a medias.
            val frames = (1..40).map { i -> brightness(scene.render(i * 20_000_000L)) }
            val end = frames.last()
            val dip = frames.min()
            assertTrue(dip < minOf(start, end) * 0.97, "debería haber un fundido: inicio=$start mínimo=$dip final=$end")
            // y termina en no más de medio segundo
            assertTrue(frames.drop(25).all { kotlin.math.abs(it - end) < 0.5 })
        }
    }

    @Test
    fun canBeTurnedOffAndOldThemesKeepItOn() {
        val old = ProtocolJson.decodeFromString(Theme.serializer(), """{"color":"#000000"}""")
        assertTrue(old.fade)
        var s by mutableStateOf(state)
        ImageComposeScene(320, 180, Density(1f)) { ProjectorScreen(s, look = Look(plain.copy(fade = false))) }.use { scene ->
            scene.render(0)
            s = state.copy(index = 1)
            // Ningún cuadro en negro ni a medias: cambia de una vez a la nueva
            val frames = (1..10).map { i -> brightness(scene.render(i * 20_000_000L)) }
            val later = brightness(scene.render(1_000_000_000L))
            assertTrue(frames.all { it > 1 }, "sin parpadeo en negro: $frames")
            assertTrue(frames.drop(1).all { kotlin.math.abs(it - later) < 0.5 }, "sin fundido: $frames vs $later")
        }
    }
}
