package app.proyecta.desktop

import androidx.compose.ui.use
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WelcomeTest {
    @Test
    fun guideCoversTheMainThings() {
        assertTrue(WELCOME_STEPS.size in 4..8, "la guía debe ser corta")
        val all = WELCOME_STEPS.joinToString(" ") { it.title + " " + it.body }
        for (must in listOf("Canciones", "Biblia", "Lista", "QR", "F5", "Proyección")) {
            assertTrue(must in all, "la guía debería mencionar $must")
        }
        // Lo que la guía promete existe en los atajos
        val keys = SHORTCUTS.joinToString(" ") { it.first }
        for (k in listOf("C", "B", "N", "F5", "Esc")) assertTrue(k in keys, k)
    }

    @Test
    fun shownOnlyOnFirstRun() {
        val dir = createTempDirectory("proyecta-bienvenida").toFile()
        val file = java.io.File(dir, "ajustes.json")
        assertFalse(SettingsStore(file).value.welcomeDone) // primera vez: se muestra
        SettingsStore(file).update { it.copy(welcomeDone = true) }
        assertTrue(SettingsStore(file).value.welcomeDone) // después ya no
        dir.deleteRecursively()
    }

    /** PROYECTA_RENDER_DIR=… para ver la guía */
    @Test
    fun renderWelcome() {
        val out = System.getenv("PROYECTA_RENDER_DIR")?.let { java.io.File(it) } ?: return
        androidx.compose.ui.ImageComposeScene(900, 600, androidx.compose.ui.unit.Density(1f)) {
            androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                androidx.compose.material3.Surface { WelcomeDialog {} }
            }
        }.use { java.io.File(out, "bienvenida.png").writeBytes(it.render(0).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes) }
    }
}
