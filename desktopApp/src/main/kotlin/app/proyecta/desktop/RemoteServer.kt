package app.proyecta.desktop

import app.proyecta.shared.ClientMessage
import app.proyecta.shared.DEFAULT_PORT
import app.proyecta.shared.ServerMessage
import app.proyecta.shared.WS_PATH
import app.proyecta.shared.decodeClientMessage
import app.proyecta.shared.encode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing
import app.proyecta.shared.ProtocolJson
import app.proyecta.shared.DiscoveryReply
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.time.Duration.Companion.seconds

/** Servidor WebSocket para los controles remotos (celulares y tablets en la misma red). */
class RemoteServer(
    private val commands: Commands,
    private val library: Library,
    val port: Int = DEFAULT_PORT,
    /** Clave para controlar (null = no se pide). */
    private val pin: kotlinx.coroutines.flow.StateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow(null),
) {
    private val guard = PinGuard(pin)
    private val presenter get() = commands.presenter
    private val pcName: String = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("Proyecta")
    private var engine: EmbeddedServer<*, *>? = null

    fun start() {
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") { module() }.start(wait = false)
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 200, timeoutMillis = 1000)
        engine = null
    }

    private fun Application.module() {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 30.seconds
        }
        routing {
            // Nombre del PC, para que la página muestre "Conectado a <nombre>".
            get("/info") {
                call.respondText(
                    ProtocolJson.encodeToString(DiscoveryReply.serializer(), DiscoveryReply(pcName, port)),
                    ContentType.Application.Json,
                )
            }
            // Páginas web: control remoto (/), vista de escenario y letras para OBS.
            staticResources("/", "web", index = "index.html")

            webSocket(WS_PATH) {
                val address = call.request.local.remoteAddress
                // Clave con la que esta conexión se autorizó (si la clave cambia, deja de valer).
                var granted: String? = null
                fun auth(message: String? = null) = ServerMessage.Auth(guard.required, guard.allows(granted), message)
                // Envía biblioteca y estado al conectarse, y luego cada vez que cambien.
                val pushJob = launch {
                    launch { pin.collect { send(Frame.Text(auth().encode())) } }
                    launch {
                        library.catalog.collect { c ->
                            send(Frame.Text(ServerMessage.Library(c.songs, c.bibleName, library.bible?.bookInfos().orEmpty(), c.announcements, c.sermons, c.bibleNames, c.secondBibleName).encode()))
                        }
                    }
                    launch {
                        kotlinx.coroutines.flow.combine(commands.plan.state, commands.plan.saved) { p, saved -> ServerMessage.Plan(p, saved) }
                            .collect { m -> send(Frame.Text(m.encode())) }
                    }
                    presenter.state.collect { s -> send(Frame.Text(ServerMessage.State(s, System.currentTimeMillis()).encode())) }
                }
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val msg = runCatching { decodeClientMessage(frame.readText()) }.getOrNull() ?: continue
                        if (msg is ClientMessage.BibleSearch) {
                            // La respuesta va solo a quien buscó.
                            send(Frame.Text(bibleSearch(library, msg.query).encode()))
                            continue
                        }
                        if (msg is ClientMessage.BibleChapter) {
                            send(Frame.Text(bibleChapter(library, msg.book, msg.chapter).encode()))
                            continue
                        }
                        if (msg is ClientMessage.Login) {
                            val reply = when (val r = guard.check(address, msg.pin)) {
                                PinGuard.Result.Ok -> { granted = pin.value; auth() }
                                is PinGuard.Result.Wrong -> auth("Clave incorrecta (te quedan ${r.triesLeft} intentos)")
                                is PinGuard.Result.Locked -> auth("Demasiados intentos. Espera ${r.seconds} segundos.")
                            }
                            send(Frame.Text(reply.encode()))
                            continue
                        }
                        if (!guard.allows(granted)) {
                            send(Frame.Text(auth("Escribe la clave para controlar").encode()))
                            continue
                        }
                        commands.handle(msg)?.let { err -> send(Frame.Text(ServerMessage.Error(err).encode())) }
                    }
                } finally {
                    pushJob.cancel()
                }
            }
        }
    }

    companion object {
        fun bibleSearch(library: Library, query: String): ServerMessage {
            val bible = library.bible ?: return ServerMessage.Error("No hay Biblia cargada")
            val (hits, total) = bible.search(query)
            return ServerMessage.BibleResults(query, hits, total)
        }

        fun bibleChapter(library: Library, book: String, chapter: Int): ServerMessage {
            val bible = library.bible ?: return ServerMessage.Error("No hay Biblia cargada")
            val verses = bible.chapter(book, chapter) ?: return ServerMessage.Error("$book no tiene capítulo $chapter")
            return ServerMessage.BibleChapter(book, chapter, verses)
        }

        /** Direcciones IPv4 de la red local de este equipo (las que se escriben en la tablet). */
        fun localAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
        }.getOrDefault(emptyList())
    }
}
