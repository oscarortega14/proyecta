package app.proyecta.remote

import app.proyecta.shared.ClientMessage
import app.proyecta.shared.DEFAULT_PORT
import app.proyecta.shared.PresentationState
import app.proyecta.shared.ServerMessage
import app.proyecta.shared.ServicePlanState
import app.proyecta.shared.SongSummary
import app.proyecta.shared.WS_PATH
import app.proyecta.shared.decodeServerMessage
import app.proyecta.shared.encode
import io.ktor.client.HttpClient
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

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val queue = Channel<ClientMessage>(Channel.UNLIMITED)
    private var job: Job? = null

    fun connect(host: String) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                _status.value = ConnectionStatus.Connecting(host)
                try {
                    http.webSocket(host = host, port = DEFAULT_PORT, path = WS_PATH) {
                        _status.value = ConnectionStatus.Connected(host)
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
                delay(2_000)
            }
        }
    }

    fun send(msg: ClientMessage) {
        if (_status.value is ConnectionStatus.Connected) queue.trySend(msg)
    }

    private fun onMessage(m: ServerMessage) {
        when (m) {
            is ServerMessage.Library -> { _songs.value = m.songs; _bibleName.value = m.bibleName }
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
