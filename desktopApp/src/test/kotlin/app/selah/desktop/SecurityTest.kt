package app.selah.desktop

import app.selah.shared.ClientMessage
import app.selah.shared.WS_PATH
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityTest {

    @Test
    fun manyAddressesCannotBruteForceThePin() {
        val pin = MutableStateFlow<String?>("4821")
        var now = 0L
        val guard = PinGuard(pin) { now }
        // Alguien cambiándose de IP: 20 fallos en total en un minuto y se cierra para todos
        repeat(PinGuard.MAX_GLOBAL_FAILURES) { i -> assertIs<PinGuard.Result.Wrong>(guard.check("10.0.0.${i + 10}", "0000")) }
        assertIs<PinGuard.Result.Locked>(guard.check("10.0.0.200", "1111"))
        assertIs<PinGuard.Result.Locked>(guard.check("10.0.0.201", "4821")) // ni con la buena, hasta que pase el minuto
        now += PinGuard.WINDOW_MILLIS + 1
        assertEquals(PinGuard.Result.Ok, guard.check("10.0.0.201", "4821"))
    }

    @Test
    fun repeatedLockoutsGetLonger() {
        var now = 0L
        val guard = PinGuard(MutableStateFlow("4821")) { now }
        fun lockFor(): Long {
            repeat(PinGuard.MAX_TRIES - 1) { guard.check("10.0.0.9", "0000") }
            return (guard.check("10.0.0.9", "0000") as PinGuard.Result.Locked).seconds
        }
        assertEquals(60, lockFor())
        now += 61_000 + PinGuard.WINDOW_MILLIS
        assertEquals(120, lockFor())
        now += 121_000 + PinGuard.WINDOW_MILLIS
        assertEquals(240, lockFor())
    }

    @Test
    fun sixDigitPins() {
        repeat(50) {
            assertTrue(Regex("""[1-9]\d{5}""").matches(Settings.randomPin(6)))
            assertTrue(Regex("""[1-9]\d{3}""").matches(Settings.randomPin(4)))
        }
    }

    @Test
    fun onlyOurOwnPagesAndAppAreTrusted() {
        assertTrue(NetGuard.trusted("192.168.40.35:8765", null, "FlorG")) // la app (sin Origin)
        assertTrue(NetGuard.trusted("192.168.40.35:8765", "http://192.168.40.35:8765", "FlorG")) // nuestras páginas
        assertTrue(NetGuard.trusted("florg:8765", "http://florg:8765", "FlorG"))
        assertTrue(NetGuard.trusted("localhost:8765", null, "FlorG"))
        assertFalse(NetGuard.trusted("192.168.40.35:8765", "https://sitio-malo.com", "FlorG")) // otro sitio desde un celular
        assertFalse(NetGuard.trusted("ataque.sitio-malo.com:8765", "http://ataque.sitio-malo.com:8765", "FlorG")) // DNS rebinding
        assertFalse(NetGuard.trusted(null, null, "FlorG"))
    }

    @Test
    fun rateLimiterAndTextLimits() {
        var t = 0L
        val limiter = NetGuard.RateLimiter { t }
        assertEquals(NetGuard.RateLimiter.BURST, (1..100).count { limiter.allow(ClientMessage.Next) }) // ráfaga de 40
        t += 1_000_000_000L // un segundo después: 20 más
        assertEquals(NetGuard.RateLimiter.PER_SECOND, (1..100).count { limiter.allow(ClientMessage.Next) })
        val long = "x".repeat(100_000)
        assertEquals(NetGuard.MAX_TEXT, (NetGuard.clamp(ClientMessage.SetAlert(long)) as ClientMessage.SetAlert).text.length)
    }

    @Test
    fun savedListsAndUpdatesCannotBeUsedToReachOtherFiles() {
        val dir = createTempDirectory("selah-seg").toFile()
        File(dir, "secreto.json").writeText("""{"items":[{"kind":"SONG","ref":"x","title":"secreto"}]}""")
        val commands = Commands.create(Library(dir).also { it.reload() })
        assertNotNull(commands.handle(ClientMessage.PlanOpen("../secreto")))
        assertTrue(commands.plan.state.value.items.isEmpty())
        // Versión con caracteres raros (inyección en el script de instalación): se rechaza
        assertNull(Updater.parseRelease("""{"tag_name": "1.0\" & calc & \"", "assets": [{"name": "a.msi", "browser_download_url": "https://x/a.msi"}]}"""))
        assertNull(Updater.parseRelease("""{"tag_name": "../../x", "assets": [{"name": "a.msi", "browser_download_url": "https://x/a.msi"}]}"""))
        dir.deleteRecursively()
    }

    @Test
    fun connectionsPerDeviceAreCapped() {
        val ip = "10.9.9.9"
        repeat(NetGuard.MAX_CONNECTIONS_PER_ADDRESS) { assertTrue(NetGuard.acquire(ip)) }
        assertFalse(NetGuard.acquire(ip)) // la 11ª se rechaza
        assertTrue(NetGuard.acquire("10.9.9.10")) // otro dispositivo sí puede
        NetGuard.release(ip)
        assertTrue(NetGuard.acquire(ip)) // al cerrarse una, entra otra
        repeat(NetGuard.MAX_CONNECTIONS_PER_ADDRESS) { NetGuard.release(ip) }
        NetGuard.release("10.9.9.10")
    }

    @Test
    fun uploadsOnlyCommonFormats() {
        assertNotNull(RemoteServer.uploadName("foto.webp", 0))
        assertNotNull(RemoteServer.uploadName("video.mov", 0))
        assertNull(RemoteServer.uploadName("video.mkv", 0))
        assertNull(RemoteServer.uploadName("video.avi", 0))
        assertNull(RemoteServer.uploadName("imagen.bmp", 0))
    }

    @Test
    fun serverRejectsForeignOriginsHugeMessagesAndTooManyConnections() = runBlocking {
        val dir = createTempDirectory("selah-seg2").toFile()
        val library = Library(dir).also { it.reload() }
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteServer(Commands.create(library), library, port, MutableStateFlow("4821")).also { it.start() }
        val client = HttpClient(CIO) { install(WebSockets) }
        try {
            withTimeout(20_000) {
                // Otro sitio intentando conectarse por detrás desde un navegador
                var closed = false
                client.webSocket(host = "127.0.0.1", port = port, path = WS_PATH, request = { header("Origin", "https://sitio-malo.com") }) {
                    closed = incoming.receiveCatching().isClosed
                }
                assertTrue(closed, "debió cerrarse")
                // Subir desde otro sitio: prohibido aunque tenga la clave
                val up = client.post("http://127.0.0.1:$port/anuncios?nombre=a.png") {
                    header("Origin", "https://sitio-malo.com"); header("X-Proyecta-Pin", "4821"); setBody(ByteArray(10))
                }
                assertEquals(HttpStatusCode.Forbidden, up.status)
                // Mensaje gigante: el servidor corta la conexión
                var cut = false
                client.webSocket(host = "127.0.0.1", port = port, path = WS_PATH) {
                    send(Frame.Text("x".repeat(200_000)))
                    cut = runCatching { while (true) incoming.receive() }.isFailure
                }
                assertTrue(cut)
            }
        } finally {
            client.close(); server.stop(); dir.deleteRecursively()
        }
    }
}
