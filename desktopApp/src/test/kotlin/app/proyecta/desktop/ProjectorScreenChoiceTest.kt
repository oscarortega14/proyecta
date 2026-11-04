package app.proyecta.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectorScreenChoiceTest {
    private val laptop = ScreenInfo("\\\\Display1", 1, 0, 0, 1920, 1080, primary = true)
    private val beam = ScreenInfo("\\\\Display2", 2, 1920, 0, 1280, 720, primary = false)
    private val tv = ScreenInfo("\\\\Display3", 3, 3200, 0, 3840, 2160, primary = false)

    @Test
    fun automaticUsesTheFirstNonPrimaryScreen() {
        assertEquals(beam, chooseProjectorScreen(listOf(laptop, beam, tv), null))
    }

    @Test
    fun onlyOneMonitorMeansWindow() {
        assertNull(chooseProjectorScreen(listOf(laptop), null))
        assertNull(chooseProjectorScreen(emptyList(), null))
    }

    @Test
    fun chosenScreenWinsWhileConnected() {
        assertEquals(tv, chooseProjectorScreen(listOf(laptop, beam, tv), tv.id))
        // Se puede proyectar incluso en la principal si así lo quiere el operador.
        assertEquals(laptop, chooseProjectorScreen(listOf(laptop, beam), laptop.id))
    }

    @Test
    fun chosenScreenUnpluggedFallsBackToAutomatic() {
        assertEquals(beam, chooseProjectorScreen(listOf(laptop, beam), tv.id))
        assertNull(chooseProjectorScreen(listOf(laptop), tv.id))
    }

    @Test
    fun windowedChoiceIsRespected() {
        assertNull(chooseProjectorScreen(listOf(laptop, beam), Settings.WINDOWED))
    }

    @Test
    fun labelsAreFriendly() {
        assertEquals("Pantalla 1 (principal) · 1920×1080", laptop.label)
        assertEquals("Pantalla 2 · 1280×720", beam.label)
    }

    @Test
    fun settingsAreRememberedBetweenRuns() {
        val dir = createTempDirectory("proyecta-ajustes").toFile()
        val file = java.io.File(dir, "ajustes.json")
        SettingsStore(file).update { it.copy(projectorScreen = tv.id, welcomeDone = true) }
        val again = SettingsStore(file).value
        assertEquals(tv.id, again.projectorScreen)
        assertTrue(again.welcomeDone)
        file.writeText("{esto no es json")
        assertEquals(Settings(), SettingsStore(file).value) // archivo dañado: valores por defecto
        dir.deleteRecursively()
    }

    @Test
    fun f5ReopensTheProjector() {
        assertTrue(reopensProjector(KeyEventType.KeyDown, Key.F5))
        assertFalse(reopensProjector(KeyEventType.KeyUp, Key.F5))
        assertFalse(reopensProjector(KeyEventType.KeyDown, Key.B))
    }
}
