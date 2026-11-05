package app.selah.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import app.selah.shared.ClientMessage
import app.selah.shared.PlanKind
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SermonsTest {
    private lateinit var dir: File
    private lateinit var library: Library
    private lateinit var commands: Commands

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("selah-predica").toFile()
        library = Library(dir).also { it.reload() }
        commands = Commands.create(library)
    }

    @AfterTest
    fun tearDown() { dir.deleteRecursively() }

    @Test
    fun exampleSermonIsCreatedAndShownWithRealVerses() {
        assertEquals(listOf("El buen pastor"), library.catalog.value.sermons.map { it.title })
        assertNull(commands.handle(ClientMessage.ShowSermon("Ejemplo de predicación")))
        val s = commands.presenter.state.value
        assertTrue(s.sermon)
        assertEquals("El buen pastor", s.title)
        assertEquals("El buen pastor", s.slides.first().text) // portada
        val verse = s.slides.first { it.label == "Juan 10:11" }
        assertTrue(verse.text.contains("buen pastor"), verse.text) // RV1909 de verdad
        assertEquals(listOf("Inicio", "Introducción", "Punto 1", "Punto 2", "Conclusión"), s.sectionLabels)
        assertEquals("Predicación", commands.history!!.entries.value.last().kind.label)
    }

    @Test
    fun sermonsAreSavedSeparatelyFromSongsAndGoInThePlan() {
        val id = library.save(null, "# Fe y obras\n\nPunto 1:\nLa fe obra\n\nBiblia: Santiago 2:17\n", sermon = true)
        assertTrue(File(library.sermonsDir, "$id.txt").isFile)
        assertNull(library.song(id)) // no es una canción
        assertTrue(library.catalog.value.songs.none { it.title == "Fe y obras" })
        assertFailsWith<IllegalArgumentException> { library.save(null, "# Fe y obras\n\notra\n", sermon = true) }

        assertNull(commands.handle(ClientMessage.PlanAddSermon(id)))
        assertNotNull(commands.handle(ClientMessage.PlanAddSermon("no existe")))
        val item = commands.plan.state.value.items.single()
        assertEquals(PlanKind.SERMON, item.kind)
        commands.handle(ClientMessage.PlanGo(0))
        assertEquals("Fe y obras", commands.presenter.state.value.title)

        // Editar mientras está en pantalla: se actualiza sin moverse
        commands.handle(ClientMessage.Next)
        val newId = library.save(id, "# Fe y obras\n\nPunto 1:\nLa fe sin obras está muerta\n\nBiblia: Santiago 2:17\n", sermon = true)
        commands.presenter.sermonSaved(id, newId)
        val s = commands.presenter.state.value
        assertEquals(1, s.index)
        assertEquals("La fe sin obras está muerta", s.current!!.text)

        library.delete(newId, sermon = true)
        assertTrue(library.catalog.value.sermons.none { it.id == newId })
    }

    /** SELAH_RENDER_DIR=… para ver un punto y un versículo de la predicación */
    @Test
    fun renderSermonSlides() {
        val out = System.getenv("SELAH_RENDER_DIR")?.let { File(it) } ?: return
        commands.handle(ClientMessage.ShowSermon("Ejemplo de predicación"))
        val s = commands.presenter.state.value
        for ((name, index) in listOf("predica-punto.png" to 1, "predica-versiculo.png" to 2)) {
            ImageComposeScene(960, 540, Density(1f)) {
                ProjectorScreen(s.copy(index = index), look = Look(frozenTime = 2f))
            }.use { File(out, name).writeBytes(it.render(0).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
        }
    }
}
