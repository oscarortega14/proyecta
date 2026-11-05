package app.proyecta.desktop

import app.proyecta.shared.ClientMessage
import app.proyecta.shared.PresentationState
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File

class PreacherTimerTest {
    @Test
    fun countsUpAndDownAndGoesNegativeWhenOver() {
        val s = PresentationState(timerStart = 1_000_000L, timerMinutes = 30)
        assertEquals(0, s.timerElapsed(1_000_000L))
        assertEquals(600, s.timerElapsed(1_600_000L)) // 10 min
        assertEquals(1200, s.timerLeft(1_600_000L)) // quedan 20
        assertEquals(-90, s.timerLeft(1_000_000L + 1_890_000L)) // 31,5 min: se pasó 1:30
        val noLimit = s.copy(timerMinutes = 0)
        assertNull(noLimit.timerLeft(2_000_000L))
        assertNull(PresentationState().timerElapsed(5))
    }

    @Test
    fun presenterStartsAndStopsAndKeepsItWhileSlidesChange() {
        val dir = createTempDirectory("proyecta-tiempo").toFile()
        val library = Library(dir).also { it.reload() }
        var now = 50_000L
        val p = Presenter(library) { now }
        assertNull(p.handle(ClientMessage.StartTimer(25)))
        assertEquals(50_000L, p.state.value.timerStart)
        assertEquals(25, p.state.value.timerMinutes)
        now += 5000
        // Cambiar de canción o de cita no lo reinicia
        p.handle(ClientMessage.SelectSong("Canción de ejemplo"))
        p.handle(ClientMessage.ShowBible("Juan 3:16"))
        assertEquals(50_000L, p.state.value.timerStart)
        assertNotNull(p.handle(ClientMessage.StartTimer(9999)))
        p.handle(ClientMessage.StopTimer)
        assertNull(p.state.value.timerStart)
        dir.deleteRecursively()
    }
}

class LowerThirdTest {
    @Test
    fun nameForTheStreamStaysWhileSlidesChangeAndNeverReachesTheProjector() {
        val dir = kotlin.io.path.createTempDirectory("proyecta-nombre").toFile()
        val p = Presenter(Library(dir).also { it.reload() })
        assertNull(p.handle(ClientMessage.SetLowerThird(" Pastor Juan Pérez ", "Predicación")))
        p.handle(ClientMessage.SelectSong("Canción de ejemplo"))
        p.handle(ClientMessage.ShowBible("Juan 3:16"))
        assertEquals("Pastor Juan Pérez", p.state.value.lowerThird)
        assertEquals("Predicación", p.state.value.lowerThirdSub)
        // Textos enormes desde la red se recortan
        val clamped = NetGuard.clamp(ClientMessage.SetLowerThird("x".repeat(5000))) as ClientMessage.SetLowerThird
        assertEquals(NetGuard.MAX_TEXT, clamped.title.length)
        p.handle(ClientMessage.SetLowerThird("", ""))
        assertEquals("", p.state.value.lowerThird)
        // El videobeam no lo dibuja: ProjectorScreen no usa lowerThird
        val src = File("src/main/kotlin/app/proyecta/desktop/ProjectorScreen.kt").readText()
        assertTrue("lowerThird" !in src)
        dir.deleteRecursively()
    }
}
