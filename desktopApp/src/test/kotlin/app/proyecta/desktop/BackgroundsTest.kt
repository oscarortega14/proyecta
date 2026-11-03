package app.proyecta.desktop

import app.proyecta.shared.ClientMessage
import app.proyecta.shared.Theme
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackgroundsTest {
    private lateinit var dir: File
    private lateinit var library: Library

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("proyecta-bg").toFile()
        File(dir, "canciones").mkdirs()
        File(dir, "canciones/A.txt").writeText("# A\nOrden: Inicio, Coro\n\nuno\n\nCoro:\ndos\n")
        library = Library(dir).also { it.reload() }
        File(library.backgroundsDir, "b.JPG").writeBytes(byteArrayOf(1))
        File(library.backgroundsDir, "a.png").writeBytes(byteArrayOf(1))
        File(library.backgroundsDir, "notas.txt").writeText("x")
    }

    @AfterTest
    fun tearDown() { dir.deleteRecursively() }

    @Test
    fun listsOnlyImagesAndBlocksPathTricks() {
        assertEquals(listOf("a.png", "b.JPG"), library.backgrounds())
        assertEquals("a.png", library.backgroundFile("a.png")?.name)
        assertNull(library.backgroundFile("../canciones/A.txt"))
        assertNull(library.backgroundFile("no-existe.jpg"))
    }

    @Test
    fun setsAndClearsTheSongBackgroundKeepingTheRest() {
        val commands = Commands.create(library)
        commands.handle(ClientMessage.SelectSong("A"))
        val id = library.setSongBackground("A", "a.png")
        commands.presenter.songSaved("A", id)
        assertEquals("# A\nFondo: a.png\nOrden: Inicio, Coro\n\nuno\n\nCoro:\ndos\n", library.rawText("A"))
        assertEquals("a.png", commands.presenter.state.value.background)

        library.setSongBackground("A", "b.JPG") // reemplaza, no duplica
        assertEquals(1, library.rawText("A")!!.lines().count { it.startsWith("Fondo:") })

        val id2 = library.setSongBackground("A", null)
        commands.presenter.songSaved("A", id2)
        assertEquals("# A\nOrden: Inicio, Coro\n\nuno\n\nCoro:\ndos\n", library.rawText("A"))
        assertNull(commands.presenter.state.value.background)
    }

    @Test
    fun themeIsSavedAndRestored() {
        val file = File(dir, "tema.json")
        val store = ThemeStore(file)
        store.update { it.copy(color = "#0B1E3F", color2 = "#1E4A7A", fontScale = 5f) }
        assertEquals(1.4f, store.theme.value.fontScale)
        assertEquals(store.theme.value, ThemeStore(file).theme.value)
        file.writeText("esto no es json")
        assertEquals(Theme(), ThemeStore(file).theme.value)
    }

    @Test
    fun hexColorsParse() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0B1E3F), hexColor("#0B1E3F"))
        assertEquals(androidx.compose.ui.graphics.Color.Black, hexColor("nope"))
    }
}
