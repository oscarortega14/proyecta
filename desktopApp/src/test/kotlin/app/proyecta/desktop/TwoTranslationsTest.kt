package app.proyecta.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.use
import app.proyecta.shared.ClientMessage
import app.proyecta.shared.ServerMessage
import app.proyecta.shared.decodeServerMessage
import app.proyecta.shared.encode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwoTranslationsTest {
    private lateinit var dir: File
    private lateinit var library: Library
    private lateinit var commands: Commands

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("proyecta-dos").toFile()
        library = Library(dir).also { it.reload() }
        commands = Commands.create(library)
    }

    @AfterTest
    fun tearDown() { dir.deleteRecursively() }

    @Test
    fun eachVerseCarriesTheSecondTranslation() {
        assertNull(commands.handle(ClientMessage.SetSecondBible("Versión Biblia Libre")))
        assertEquals("Versión Biblia Libre", library.catalog.value.secondBibleName)
        commands.handle(ClientMessage.ShowBible("Juan 3:16-17"))
        val slides = commands.presenter.state.value.slides
        assertEquals(2, slides.size)
        assertTrue(slides[0].text.contains("de tal manera amó")) // RV1909
        assertTrue(slides[0].text2.isNotEmpty() && slides[0].text2 != slides[0].text) // Biblia Libre
        assertTrue(slides.all { it.text2.isNotEmpty() })

        // Quitarla
        commands.handle(ClientMessage.SetSecondBible(null))
        commands.handle(ClientMessage.ShowBible("Juan 3:16"))
        assertEquals("", commands.presenter.state.value.slides.single().text2)
    }

    @Test
    fun secondCannotBeTheSameAsTheFirstAndRemotesSeeTheChoice() {
        commands.handle(ClientMessage.SelectBible("Versión Biblia Libre"))
        commands.handle(ClientMessage.SetSecondBible("Versión Biblia Libre"))
        assertNull(library.catalog.value.secondBibleName) // la misma no tiene sentido
        commands.handle(ClientMessage.SetSecondBible("Reina-Valera 1909"))
        val c = library.catalog.value
        val msg = ServerMessage.Library(c.songs, c.bibleName, bibleNames = c.bibleNames, secondBible = c.secondBibleName)
        val back = decodeServerMessage(msg.encode()) as ServerMessage.Library
        assertEquals("Versión Biblia Libre", back.bibleName)
        assertEquals("Reina-Valera 1909", back.secondBible)
        assertEquals(listOf("Reina-Valera 1909", "Versión Biblia Libre"), back.bibleNames)
    }

    @Test
    fun bothTranslationsFitOnScreen() {
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val text = "Porque de tal manera amó Dios al mundo, que ha dado á su Hijo unigénito, para que todo aquel que en él cree, no se pierda, mas tenga vida eterna."
        val alone = fitFontSizeMeasured(measurer, Density(1f), text, "Juan 3:16", 1728, 918)
        val both = fitFontSizeMeasured(measurer, Density(1f), text, "Juan 3:16", 1728, 918, text2 = text)
        assertTrue(both < alone, "con dos traducciones la letra se achica para que quepan las dos")
        assertTrue(both > 30f, "pero sigue legible ($both)")
    }

    /** PROYECTA_RENDER_DIR=… para ver un versículo en dos traducciones */
    @Test
    fun renderTwoTranslations() {
        val out = System.getenv("PROYECTA_RENDER_DIR")?.let { File(it) } ?: return
        commands.handle(ClientMessage.SetSecondBible("Versión Biblia Libre"))
        commands.handle(ClientMessage.ShowBible("Juan 3:16"))
        val s = commands.presenter.state.value
        ImageComposeScene(960, 540, Density(1f)) { ProjectorScreen(s, look = Look(frozenTime = 2f)) }
            .use { File(out, "dos-traducciones.png").writeBytes(it.render(0).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
    }
}
