package app.selah.desktop

import app.selah.shared.ClientMessage
import app.selah.shared.ServerMessage
import app.selah.shared.WS_PATH
import app.selah.shared.decodeServerMessage
import app.selah.shared.encode
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.ServerSocket
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinTest {

    @Test
    fun guardAcceptsTheRightPinAndLocksAfterFiveTries() {
        val pin = MutableStateFlow<String?>("4821")
        var now = 0L
        val guard = PinGuard(pin) { now }
        assertTrue(guard.required)
        assertFalse(guard.allows(null))
        assertEquals(PinGuard.Result.Ok, guard.check("10.0.0.5", " 4821 "))
        assertTrue(guard.allows("4821"))

        repeat(4) { assertIs<PinGuard.Result.Wrong>(guard.check("10.0.0.9", "0000")) }
        assertIs<PinGuard.Result.Locked>(guard.check("10.0.0.9", "1111")) // 5º intento
        assertIs<PinGuard.Result.Locked>(guard.check("10.0.0.9", "4821")) // ni la buena mientras está bloqueado
        assertEquals(PinGuard.Result.Ok, guard.check("10.0.0.5", "4821")) // otro dispositivo no se ve afectado
        now += PinGuard.LOCK_MILLIS + 1
        assertEquals(PinGuard.Result.Ok, guard.check("10.0.0.9", "4821"))

        // Al cambiar la clave, la anterior deja de valer
        pin.value = "7777"
        assertFalse(guard.allows("4821"))
        // Sin clave, todos pueden
        pin.value = null
        assertTrue(guard.allows(null))
    }

    @Test
    fun onlyLookingIsAllowedWithoutThePin() {
        assertTrue(PinGuard.isReadOnly(ClientMessage.BibleSearch("amor")))
        assertTrue(PinGuard.isReadOnly(ClientMessage.BibleChapter("Juan", 3)))
        assertFalse(PinGuard.isReadOnly(ClientMessage.Next))
        assertFalse(PinGuard.isReadOnly(ClientMessage.SetBlank(true)))
    }

    @Test
    fun pinIsCreatedOnceAndCanBeTurnedOff() {
        val dir = createTempDirectory("selah-pin").toFile()
        val store = SettingsStore(File(dir, "ajustes.json"))
        assertNull(store.value.pin)
        store.update { it.copy(pin = Settings.randomPin()) }
        val pin = store.value.pin!!
        assertTrue(Regex("""\d{4}""").matches(pin))
        assertEquals(pin, store.pin.value)
        assertEquals(pin, SettingsStore(File(dir, "ajustes.json")).value.pin) // se recuerda
        store.update { it.copy(pinOff = true) }
        assertNull(store.pin.value)
        dir.deleteRecursively()
    }

    @Test
    fun qrCarriesThePinOutsideWhatTheServerSees() {
        assertEquals("http://192.168.40.35:8765/#pin=4821", controlUrl("192.168.40.35", 8765, "4821"))
        assertEquals("http://192.168.40.35:8765", controlUrl("192.168.40.35", 8765, null))
    }

    @Test
    fun remoteNeedsThePinToControl() = runBlocking {
        val dir = createTempDirectory("selah-pin-srv").toFile()
        File(dir, "canciones").mkdirs()
        File(dir, "canciones/b.txt").writeText("# Beta\n\nuno\n")
        val library = Library(dir).also { it.reload() }
        val commands = Commands.create(library)
        val pin = MutableStateFlow<String?>("4821")
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteServer(commands, library, port, pin).also { it.start() }
        val client = HttpClient(CIO) { install(WebSockets) }
        try {
            withTimeout(15_000) {
                client.webSocket(host = "127.0.0.1", port = port, path = WS_PATH) {
                    assertEquals(ServerMessage.Auth(required = true, ok = false), nextOf<ServerMessage.Auth>())

                    // Sin clave: no puede cambiar lo que se proyecta…
                    send(ClientMessage.SelectSong("b"))
                    assertFalse(nextOf<ServerMessage.Auth>().ok)
                    assertEquals("", commands.presenter.state.value.title)
                    // …pero sí buscar en la Biblia
                    send(ClientMessage.BibleSearch("principio"))
                    nextOf<ServerMessage.BibleResults>()

                    send(ClientMessage.Login("1234"))
                    val wrong = nextOf<ServerMessage.Auth>()
                    assertFalse(wrong.ok)
                    assertTrue(wrong.message!!.contains("incorrecta"))

                    send(ClientMessage.Login("4821"))
                    assertTrue(nextOf<ServerMessage.Auth>().ok)
                    send(ClientMessage.SelectSong("b"))
                    while (nextOf<ServerMessage.State>().state.title != "Beta") Unit

                    // El operador cambia la clave: este dispositivo debe pedirla de nuevo
                    pin.value = "9999"
                    assertFalse(nextOf<ServerMessage.Auth>().ok)
                    send(ClientMessage.Next)
                    assertFalse(nextOf<ServerMessage.Auth>().ok)
                }
            }
        } finally {
            client.close()
            server.stop()
            dir.deleteRecursively()
        }
    }

    private suspend fun DefaultClientWebSocketSession.send(m: ClientMessage) = send(Frame.Text(m.encode()))

    private suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.nextOf(): T {
        while (true) {
            val m = decodeServerMessage((incoming.receive() as Frame.Text).readText())
            if (m is T) return m
        }
    }
}
