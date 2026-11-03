package app.proyecta.desktop

import app.proyecta.shared.ClientMessage
import app.proyecta.shared.ServerMessage
import app.proyecta.shared.WS_PATH
import app.proyecta.shared.decodeServerMessage
import app.proyecta.shared.encode
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.ServerSocket
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerTest {
    private lateinit var dir: File
    private lateinit var library: Library
    private lateinit var presenter: Presenter
    private lateinit var server: RemoteServer
    private val port = ServerSocket(0).use { it.localPort }

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("proyecta").toFile()
        File(dir, "canciones").mkdirs()
        File(dir, "canciones/b.txt").writeText("# Beta\n\nuno\n\nCoro:\ndos\n")
        // archivo guardado en ANSI (Windows-1252) con tildes
        File(dir, "canciones/a.txt").writeBytes("# Canción Alfa\n\nAquí está\n".toByteArray(charset("windows-1252")))
        File(dir, "biblia").mkdirs()
        File(dir, "biblia/rv.json").writeText(
            """{"name":"Test","books":[{"name":"Juan","chapters":[["a","b","c"]]}]}""",
        )
        library = Library(dir).also { it.reload() }
        presenter = Presenter(library)
        server = RemoteServer(presenter, library, port).also { it.start() }
    }

    @AfterTest
    fun tearDown() {
        server.stop()
        dir.deleteRecursively()
    }

    @Test
    fun libraryLoadsUtf8AndAnsiSortedByTitle() {
        val songs = library.catalog.value.songs
        assertEquals(listOf("Beta", "Canción Alfa"), songs.map { it.title })
        assertEquals("Aquí está", songs[1].firstLine)
        assertEquals("Test", library.catalog.value.bibleName)
    }

    @Test
    fun remoteControlsThePresentation() = runBlocking {
        val client = HttpClient(CIO) { install(WebSockets) }
        withTimeout(10_000) {
            client.webSocket(host = "127.0.0.1", port = port, path = WS_PATH) {
                suspend fun next(): ServerMessage = decodeServerMessage((incoming.receive() as Frame.Text).readText())
                suspend fun nextState(): app.proyecta.shared.PresentationState {
                    while (true) {
                        val m = next()
                        if (m is ServerMessage.State) return m.state
                    }
                }

                // al conectar llegan biblioteca y estado (en cualquier orden)
                val first = listOf(next(), next())
                val lib = first.filterIsInstance<ServerMessage.Library>().single()
                assertEquals(2, lib.songs.size)
                assertNotNull(first.filterIsInstance<ServerMessage.State>().singleOrNull())

                send(Frame.Text(ClientMessage.SelectSong("b").encode()))
                var s = nextState()
                assertEquals("Beta", s.title)
                assertEquals(0, s.index)

                send(Frame.Text(ClientMessage.Next.encode()))
                s = nextState()
                assertEquals(1, s.index)
                assertEquals("Coro", s.current?.label)

                send(Frame.Text(ClientMessage.ShowBible("jn 1:2-3").encode()))
                s = nextState()
                assertTrue(s.isBible)
                assertEquals("Juan 1:2-3", s.title)
                assertEquals(listOf("b", "c"), s.slides.map { it.text })

                send(Frame.Text(ClientMessage.ShowBible("juan 9").encode()))
                val err = next()
                assertTrue(err is ServerMessage.Error && "capítulo" in err.message)
            }
        }
        client.close()
    }
}
