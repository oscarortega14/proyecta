package app.proyecta.desktop

import app.proyecta.shared.ClientMessage
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditingTest {
    private lateinit var dir: File
    private lateinit var library: Library
    private lateinit var presenter: Presenter
    private val songs get() = File(dir, "canciones")

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("proyecta-edit").toFile()
        songs.mkdirs()
        File(songs, "Alfa.txt").writeText("# Alfa\n\nuno\n\nCoro:\ndos\n\ntres\n")
        library = Library(dir).also { it.reload() }
        presenter = Presenter(library)
    }

    @AfterTest
    fun tearDown() { dir.deleteRecursively() }

    @Test
    fun fileNamesAreSafeForWindows() {
        assertEquals("Salmo 2-7", Library.fileNameFor("Salmo 2:7"))
        assertEquals("¿Quién como el Señor-", Library.fileNameFor("¿Quién como el Señor?"))
        assertEquals("A b", Library.fileNameFor("  A   b. "))
    }

    @Test
    fun createsANewSongWithoutPuttingItOnScreen() {
        val id = library.save(null, "# Beta\r\n\r\nhola\r\n")
        assertEquals("Beta", id)
        assertEquals("# Beta\n\nhola\n", File(songs, "Beta.txt").readText())
        assertEquals(listOf("Alfa", "Beta"), library.catalog.value.songs.map { it.title })

        presenter.songSaved(null, id)
        assertEquals(-1, presenter.state.value.index)
        assertNull(presenter.state.value.songId)
    }

    @Test
    fun renamesTheFileWhenTheTitleChanges() {
        val id = library.save("Alfa", "# Alfa Nueva\n\nuno\n")
        assertEquals("Alfa Nueva", id)
        assertFalse(File(songs, "Alfa.txt").exists())
        assertTrue(File(songs, "Alfa Nueva.txt").exists())
        assertNull(library.song("Alfa"))
    }

    @Test
    fun rejectsDuplicatesAndMissingTitle() {
        library.save(null, "# Beta\n\nx\n")
        assertTrue("Ya existe" in assertFailsWith<IllegalArgumentException> { library.save(null, "# Beta\n\ny\n") }.message!!)
        assertTrue("Ya existe" in assertFailsWith<IllegalArgumentException> { library.save("Alfa", "# Beta\n\ny\n") }.message!!)
        assertTrue("título" in assertFailsWith<IllegalArgumentException> { library.save(null, "sin título\n") }.message!!)
        // guardar la misma canción sin cambiar el título no es duplicado
        assertEquals("Alfa", library.save("Alfa", "# Alfa\n\notra letra\n"))
    }

    @Test
    fun editingTheSongOnScreenRefreshesItInPlace() {
        presenter.handle(ClientMessage.SelectSong("Alfa"))
        presenter.handle(ClientMessage.GoTo(2))

        val id = library.save("Alfa", "# Alfa\n\nuno\n\nCoro:\nDOS CAMBIADO\n\ntres\n")
        presenter.songSaved("Alfa", id)
        assertEquals(2, presenter.state.value.index)
        assertEquals("DOS CAMBIADO", presenter.state.value.slides[1].text)

        // si se acorta, queda en la última diapositiva que exista
        val id2 = library.save("Alfa", "# Alfa\n\nsolo una\n")
        presenter.songSaved("Alfa", id2)
        assertEquals(0, presenter.state.value.index)

        // renombrar mantiene el seguimiento
        val id3 = library.save("Alfa", "# Alfa 2\n\nsolo una\n")
        presenter.songSaved("Alfa", id3)
        assertEquals("Alfa 2", presenter.state.value.songId)
        assertEquals("Alfa 2", presenter.state.value.title)
    }

    @Test
    fun deletesSongs() {
        library.save(null, "# Beta\n\nx\n")
        library.delete("Beta")
        assertFalse(File(songs, "Beta.txt").exists())
        assertEquals(listOf("Alfa"), library.catalog.value.songs.map { it.title })
        assertEquals("# Alfa\n\nuno\n\nCoro:\ndos\n\ntres\n", library.rawText("Alfa"))
    }
}
