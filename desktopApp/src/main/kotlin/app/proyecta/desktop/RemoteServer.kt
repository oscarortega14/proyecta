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
) {
    private val presenter get() = commands.presenter
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
            // Páginas web: control remoto (/), vista de escenario y letras para OBS.
            staticResources("/", "web", index = "index.html")

            webSocket(WS_PATH) {
                // Envía biblioteca y estado al conectarse, y luego cada vez que cambien.
                val pushJob = launch {
                    launch {
                        library.catalog.collect { c ->
                            send(Frame.Text(ServerMessage.Library(c.songs, c.bibleName).encode()))
                        }
                    }
                    launch {
                        commands.plan.state.collect { p -> send(Frame.Text(ServerMessage.Plan(p).encode())) }
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
