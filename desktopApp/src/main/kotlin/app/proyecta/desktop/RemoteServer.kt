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
import io.ktor.utils.io.readAvailable
import java.io.File
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveChannel
import io.ktor.server.routing.post
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
            // Subir un anuncio (foto o video) desde el celular: POST /anuncios?nombre=foto.jpg con la clave en X-Proyecta-Pin.
            post("/anuncios") {
                val address = call.request.local.remoteAddress
                if (guard.required) {
                    val attempt = call.request.headers["X-Proyecta-Pin"].orEmpty()
                    if (guard.check(address, attempt) != PinGuard.Result.Ok) {
                        call.respondText("Falta la clave o es incorrecta", status = HttpStatusCode.Unauthorized)
                        return@post
                    }
                }
                val name = uploadName(call.request.queryParameters["nombre"].orEmpty(), System.currentTimeMillis())
                if (name == null) {
                    call.respondText("Solo imágenes o videos", status = HttpStatusCode.BadRequest)
                    return@post
                }
                val declared = call.request.headers["Content-Length"]?.toLongOrNull()
                if (declared != null && declared > MAX_UPLOAD) {
                    call.respondText("El archivo es muy grande (máximo ${MAX_UPLOAD / 1_000_000} MB)", status = HttpStatusCode.PayloadTooLarge)
                    return@post
                }
                val tmpDir = kotlin.io.path.createTempDirectory("proyecta-subida").toFile()
                try {
                    val tmp = File(tmpDir, name)
                    val channel = call.receiveChannel()
                    var total = 0L
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = channel.readAvailable(buf, 0, buf.size)
                            if (n < 0) break
                            total += n
                            if (total > MAX_UPLOAD) {
                                call.respondText("El archivo es muy grande", status = HttpStatusCode.PayloadTooLarge)
                                return@post
                            }
                            out.write(buf, 0, n)
                        }
                    }
                    if (total == 0L) { call.respondText("Llegó vacío", status = HttpStatusCode.BadRequest); return@post }
                    library.addAnnouncements(listOf(tmp))
                    call.respondText(ProtocolJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),
                        kotlinx.serialization.json.buildJsonObject { put("ok", kotlinx.serialization.json.JsonPrimitive(true)); put("nombre", kotlinx.serialization.json.JsonPrimitive(name)) }),
                        ContentType.Application.Json)
                } finally {
                    tmpDir.deleteRecursively()
                }
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
        /** Tope de lo que se puede subir desde un celular (un video corto). */
        const val MAX_UPLOAD = 300_000_000L

        /**
         * Nombre seguro para un anuncio subido. Los nombres genéricos de las cámaras
         * ("image.jpg", "IMG_1234.HEIC", "VID_…") pasan a "Anuncio 2026-11-08 10-30.jpg".
         * null si no es imagen ni video.
         */
        fun uploadName(requested: String, now: Long): String? {
            val base = File(requested.replace('\\', '/').substringAfterLast('/')).name
            val ext = base.substringAfterLast('.', "").lowercase()
            if (!Library.isMediaFile("x.$ext")) return null
            val stem = cleanListName(base.substringBeforeLast('.')) ?: ""
            val generic = stem.isEmpty() || Regex("""^(image|img|photo|foto|video|vid|pxl|screenshot|captura)[ _-]?\d*.*$""", RegexOption.IGNORE_CASE).matches(stem)
            val finalStem = if (generic) "Anuncio " + java.text.SimpleDateFormat("yyyy-MM-dd HH-mm-ss", java.util.Locale.ROOT).format(java.util.Date(now)) else stem
            return "$finalStem.$ext"
        }

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
