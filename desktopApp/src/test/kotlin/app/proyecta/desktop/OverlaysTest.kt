package app.proyecta.desktop

import androidx.compose.ui.input.key.Key
import app.proyecta.shared.ClientMessage
import app.proyecta.shared.ServerMessage
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverlaysTest {
    private lateinit var dir: File
    private lateinit var library: Library
    private var now = 1_000_000L
    private lateinit var presenter: Presenter
    private val st get() = presenter.state.value

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("proyecta-ov").toFile()
        File(dir, "canciones").mkdirs()
        File(dir, "canciones/A.txt").writeText("# A\n\nuno\n\nCoro:\ndos\n\nPregón 1:\ntres\n")
        File(dir, "biblia").mkdirs()
        File(dir, "biblia/b.json").writeText("""{"name":"T","books":[{"name":"Juan","chapters":[["Porque de tal manera amó Dios al mundo","otro"]]}]}""")
        library = Library(dir).also { it.reload() }
        presenter = Presenter(library) { now }
    }

    @AfterTest
    fun tearDown() { dir.deleteRecursively() }

    @Test
    fun logoAlertAndCountdown() {
        presenter.handle(ClientMessage.SetBlank(true))
        presenter.handle(ClientMessage.ShowLogo(true))
        assertTrue(st.logo); assertEquals(false, st.blank) // el logo quita la pantalla negra

        presenter.handle(ClientMessage.SetAlert("  Carro ABC123 "))
        assertEquals("Carro ABC123", st.alert)

        assertNotNull(presenter.handle(ClientMessage.StartCountdown(0)))
        assertNull(presenter.handle(ClientMessage.StartCountdown(300, "Comenzamos en")))
        assertEquals(300, st.countdownSeconds(now)); assertEquals(false, st.logo)
        now += 299_500
        assertEquals(1, st.countdownSeconds(now))

        // elegir una canción sale del logo pero conserva alerta y cuenta regresiva
        presenter.handle(ClientMessage.ShowLogo(true))
        presenter.handle(ClientMessage.SelectSong("A"))
        assertEquals(false, st.logo)
        assertEquals("Carro ABC123", st.alert)
        assertNotNull(st.countdownEnd)

        presenter.handle(ClientMessage.StopCountdown)
        presenter.handle(ClientMessage.SetAlert(""))
        assertNull(st.countdownEnd); assertEquals("", st.alert)
    }

    @Test
    fun keyboardShortcuts() {
        presenter.handle(ClientMessage.SelectSong("A"))
        val s = st
        assertEquals(ClientMessage.Next, singleKey(Key.DirectionRight, s))
        assertEquals(ClientMessage.Next, singleKey(Key.Spacebar, s))
        assertEquals(ClientMessage.Prev, singleKey(Key.DirectionLeft, s))
        assertEquals(ClientMessage.SetBlank(true), singleKey(Key.B, s))
        assertEquals(ClientMessage.ShowLogo(true), singleKey(Key.L, s))
        assertEquals(ClientMessage.PlanNext, singleKey(Key.N, s))
        assertEquals(ClientMessage.GoTo(1), singleKey(Key.C, s))          // Coro
        assertEquals(ClientMessage.GoTo(2), singleKey(Key.Three, s))      // 3ª sección: Pregón 1
        assertEquals(ClientMessage.GoTo(0), singleKey(Key.NumPad1, s))    // Inicio
        assertNull(singleKey(Key.Nine, s))
        assertNull(singleKey(Key.Escape, s))
        assertEquals(ClientMessage.SetBlank(false), singleKey(Key.Escape, s.copy(blank = true)))
        assertEquals(ClientMessage.ShowLogo(false), singleKey(Key.Escape, s.copy(logo = true)))
        assertNull(singleKey(Key.A, s))
    }

    @Test
    fun bibleSearchThroughTheServerHelper() {
        val r = RemoteServer.bibleSearch(library, "amo DIOS")
        assertTrue(r is ServerMessage.BibleResults)
        assertEquals(listOf("Juan 1:1"), r.hits.map { it.reference })
        assertTrue(RemoteServer.bibleSearch(Library(createTempDirectory("v").toFile()).also { it.reload() }, "x") is ServerMessage.Error)
    }
}
