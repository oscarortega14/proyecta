package app.proyecta.remote

import app.proyecta.shared.BibleBookInfo
import app.proyecta.shared.ClientMessage
import java.net.SocketTimeoutException
import java.net.InetAddress
import java.net.DatagramSocket
import java.net.DatagramPacket
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import app.proyecta.shared.ProtocolJson
import app.proyecta.shared.DiscoveryReply
import app.proyecta.shared.DISCOVERY_REQUEST
import app.proyecta.shared.DISCOVERY_PORT
import app.proyecta.shared.DEFAULT_PORT
import app.proyecta.shared.PresentationState
import app.proyecta.shared.ServerMessage
import app.proyecta.shared.ServicePlanState
import app.proyecta.shared.SongSummary
import app.proyecta.shared.WS_PATH
import app.proyecta.shared.decodeServerMessage
import app.proyecta.shared.encode
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class FoundServer(val name: String, val host: String)

sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data class Connecting(val host: String) : ConnectionStatus
    data class Connected(val host: String) : ConnectionStatus
    data class Failed(val host: String, val reason: String) : ConnectionStatus
}

/** Conexión WebSocket con el escritorio. Se reconecta sola si se cae el Wi-Fi. */
class RemoteClient(private val scope: CoroutineScope) {
    private val http = HttpClient(OkHttp) {
        install(WebSockets) { pingInterval = 10.seconds }
    }

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _state = MutableStateFlow(PresentationState())
    val state: StateFlow<PresentationState> = _state.asStateFlow()

    private val _songs = MutableStateFlow<List<SongSummary>>(emptyList())
    val songs: StateFlow<List<SongSummary>> = _songs.asStateFlow()

    private val _bibleResults = MutableStateFlow<ServerMessage.BibleResults?>(null)
    val bibleResults: StateFlow<ServerMessage.BibleResults?> = _bibleResults.asStateFlow()

    /** Hora del PC - hora de la tablet, para mostrar bien la cuenta regresiva. */
    @Volatile var serverOffset: Long = 0
        private set

    private val _plan = MutableStateFlow(ServicePlanState())
    val plan: StateFlow<ServicePlanState> = _plan.asStateFlow()

    private val _bibleName = MutableStateFlow<String?>(null)
    val bibleName: StateFlow<String?> = _bibleName.asStateFlow()

    /** Libros de la Biblia del PC (para el explorador). */
    private val _bibleBooks = MutableStateFlow<List<BibleBookInfo>>(emptyList())
    val bibleBooks: StateFlow<List<BibleBookInfo>> = _bibleBooks.asStateFlow()

    /** Último capítulo pedido con [ClientMessage.BibleChapter]. */
    private val _bibleChapter = MutableStateFlow<ServerMessage.BibleChapter?>(null)
    val bibleChapter: StateFlow<ServerMessage.BibleChapter?> = _bibleChapter.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val queue = Channel<ClientMessage>(Channel.UNLIMITED)
    private var job: Job? = null

    /** Se llama cuando el PC cambió de dirección y se encontró de nuevo en la red (para guardarla). */
    var onHostChanged: ((String) -> Unit)? = null

    fun connect(initialHost: String) {
        job?.cancel()
        job = scope.launch {
            var host = initialHost
            var failures = 0
            while (isActive) {
                _status.value = ConnectionStatus.Connecting(host)
                try {
                    http.webSocket(host = host, port = DEFAULT_PORT, path = WS_PATH) {
                        _status.value = ConnectionStatus.Connected(host)
                        failures = 0
                        // descartar órdenes que se acumularon mientras no había conexión
                        while (queue.tryReceive().isSuccess) Unit
                        val sender = launch { for (m in queue) send(Frame.Text(m.encode())) }
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) onMessage(decodeServerMessage(frame.readText()))
                            }
                        } finally {
                            sender.cancel()
                        }
                    }
                    _status.value = ConnectionStatus.Failed(host, "Se cerró la conexión")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _status.value = ConnectionStatus.Failed(host, e.message ?: e::class.simpleName.orEmpty())
                }
                // Si falla varias veces seguidas, puede que el router le haya dado otra IP al PC: buscarlo.
                if (++failures % 3 == 0) {
                    val found = discover()
                    if (found.size == 1 && found[0].host != host) {
                        host = found[0].host
                        onHostChanged?.invoke(host)
                        continue
                    }
                }
                delay(2_000)
            }
        }
    }

    /**
     * Busca a Proyecta en la red local: envía un mensaje a todos (broadcast) y espera respuestas.
     * Devuelve los PCs encontrados (normalmente uno).
     */
    suspend fun discover(timeoutMs: Long = 1_500): List<FoundServer> = withContext(Dispatchers.IO) {
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 300
                val request = DISCOVERY_REQUEST.toByteArray()
                socket.send(DatagramPacket(request, request.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT))
                val found = LinkedHashMap<String, FoundServer>()
                val buf = ByteArray(512)
                val end = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < end) {
                    val packet = DatagramPacket(buf, buf.size)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                    val reply = runCatching {
                        ProtocolJson.decodeFromString(DiscoveryReply.serializer(), String(packet.data, 0, packet.length))
                    }.getOrNull() ?: continue
                    val host = packet.address.hostAddress ?: continue
                    found[host] = FoundServer(reply.name, host)
                }
                found.values.toList()
            }
        }.getOrDefault(emptyList())
    }

    /** Nombre del PC (consulta /info), o null si no responde. */
    suspend fun pcName(host: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val text = http.get("http://$host:$DEFAULT_PORT/info").bodyAsText()
            ProtocolJson.decodeFromString(DiscoveryReply.serializer(), text).name.ifBlank { null }
        }.getOrNull()
    }

    fun clearBibleResults() { _bibleResults.value = null }

    fun send(msg: ClientMessage) {
        if (_status.value is ConnectionStatus.Connected) queue.trySend(msg)
    }

    private fun onMessage(m: ServerMessage) {
        when (m) {
            is ServerMessage.Library -> {
                // Otra traducción: el capítulo guardado ya no sirve.
                if (m.bibleName != _bibleName.value) _bibleChapter.value = null
                _songs.value = m.songs; _bibleName.value = m.bibleName; _bibleBooks.value = m.bibleBooks
            }
            is ServerMessage.BibleChapter -> _bibleChapter.value = m
            is ServerMessage.State -> {
                if (m.serverNow > 0) serverOffset = m.serverNow - System.currentTimeMillis()
                _state.value = m.state
            }
            is ServerMessage.BibleResults -> _bibleResults.value = m
            is ServerMessage.Plan -> _plan.value = m.plan
            is ServerMessage.Error -> _errors.tryEmit(m.message)
        }
    }

    fun close() {
        job?.cancel()
        http.close()
    }
}
