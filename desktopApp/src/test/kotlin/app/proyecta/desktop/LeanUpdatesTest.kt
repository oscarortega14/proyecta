package app.proyecta.desktop

import app.proyecta.shared.ClientMessage
import app.proyecta.shared.FEATURE_KEEP_SLIDES
import app.proyecta.shared.PresentationState
import app.proyecta.shared.ServerMessage
import app.proyecta.shared.Slide
import app.proyecta.shared.WS_PATH
import app.proyecta.shared.decodeServerMessage
import app.proyecta.shared.encode
import app.proyecta.shared.mergeState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeanUpdatesTest {

    @Test
    fun mergeKeepsTheSlidesTheRemoteAlreadyHas() {
        val slides = listOf(Slide("", "uno"), Slide("", "dos"))
        val before = PresentationState("A", slides, 0)
        val lean = ServerMessage.State(before.copy(slides = emptyList(), index = 1), keepSlides = true)
        assertEquals(before.copy(index = 1), mergeState(before, lean))
        val full = ServerMessage.State(PresentationState("B", listOf(Slide("", "tres")), 0))
        assertEquals("B", mergeState(before, full).title)
    }

    @Test
    fun nextSendsOnlyThePositionToRemotesThatSaidHello() = runBlocking {
        val dir = createTempDirectory("proyecta-liviano").toFile()
        val library = Library(dir).also { it.reload() }
        val commands = Commands.create(library)
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteServer(commands, library, port).also { it.start() }
        val client = HttpClient(CIO) { install(WebSockets) }
        try {
            withTimeout(15_000) {
                // Remoto nuevo: dice Hello y recibe "Siguiente" sin el texto
                client.webSocket(host = "127.0.0.1", port = port, path = WS_PATH) {
                    send(Frame.Text(ClientMessage.Hello(listOf(FEATURE_KEEP_SLIDES)).encode()))
                    var mine = nextState().state
                    commands.handle(ClientMessage.ShowBible("Salmos 119"))
                    var m = nextStateWhere { it.state.title == "Salmos 119" }
                    assertFalse(m.keepSlides) // diapositivas nuevas: van completas
                    mine = mergeState(mine, m)
                    assertEquals(176, mine.slides.size)
                    val fullSize = m.encode().length

                    commands.handle(ClientMessage.Next)
                    m = nextStateWhere { it.state.index == 1 }
                    assertTrue(m.keepSlides)
                    assertTrue(m.state.slides.isEmpty())
                    val leanSize = m.encode().length
                    mine = mergeState(mine, m)
                    assertEquals(176, mine.slides.size)
                    assertEquals("Salmos 119:2", mine.current!!.label)
                    println("SIGUIENTE: completo $fullSize bytes, liviano $leanSize bytes")
                    assertTrue(leanSize * 20 < fullSize, "liviano $leanSize vs completo $fullSize")
                }
                // Remoto viejo (sin Hello): sigue recibiendo todo, como antes
                client.webSocket(host = "127.0.0.1", port = port, path = WS_PATH) {
                    nextState()
                    commands.handle(ClientMessage.Next)
                    val m = nextStateWhere { it.state.index == 2 }
                    assertFalse(m.keepSlides)
                    assertEquals(176, m.state.slides.size)
                }
            }
        } finally {
            client.close(); server.stop(); dir.deleteRecursively()
        }
    }

    @Test
    fun bibleSearchIndexCanBeBuiltAhead() {
        val bible = Library.bundled("Reina-Valera 1909")!!
        assertTrue(bible.warmUp() > 30_000) // ~31.000 versículos con texto
        assertTrue(bible.search("amor").second > 100)
    }

    private suspend fun DefaultClientWebSocketSession.nextState(): ServerMessage.State = nextStateWhere { true }

    private suspend fun DefaultClientWebSocketSession.nextStateWhere(ok: (ServerMessage.State) -> Boolean): ServerMessage.State {
        while (true) {
            val m = decodeServerMessage((incoming.receive() as Frame.Text).readText())
            if (m is ServerMessage.State && ok(m)) return m
        }
    }
}
