package app.proyecta.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Puerto en el que escucha el escritorio. El remoto se conecta a ws://IP:PUERTO/ws */
const val DEFAULT_PORT = 8765
const val WS_PATH = "/ws"

/** Órdenes que manda el control remoto (o la ventana de operador) al presentador. */
@Serializable
sealed interface ClientMessage {
    @Serializable @SerialName("select_song")
    data class SelectSong(val id: String) : ClientMessage

    @Serializable @SerialName("goto")
    data class GoTo(val index: Int) : ClientMessage

    @Serializable @SerialName("next")
    data object Next : ClientMessage

    @Serializable @SerialName("prev")
    data object Prev : ClientMessage

    @Serializable @SerialName("blank")
    data class SetBlank(val blank: Boolean) : ClientMessage

    @Serializable @SerialName("bible")
    data class ShowBible(val reference: String) : ClientMessage

    // --- Lista del culto ---
    @Serializable @SerialName("plan_add_song")
    data class PlanAddSong(val id: String) : ClientMessage

    @Serializable @SerialName("plan_add_bible")
    data class PlanAddBible(val reference: String) : ClientMessage

    @Serializable @SerialName("plan_remove")
    data class PlanRemove(val index: Int) : ClientMessage

    @Serializable @SerialName("plan_move")
    data class PlanMove(val from: Int, val to: Int) : ClientMessage

    /** Proyecta el elemento [index] de la lista. */
    @Serializable @SerialName("plan_go")
    data class PlanGo(val index: Int) : ClientMessage

    @Serializable @SerialName("plan_next")
    data object PlanNext : ClientMessage

    @Serializable @SerialName("plan_clear")
    data object PlanClear : ClientMessage

    /** Mensaje para la vista de escenario ("" lo borra). No se ve en el videobeam. */
    @Serializable @SerialName("stage_message")
    data class StageMessage(val text: String) : ClientMessage

    /** Pantalla de espera con el logo (true) o volver a la letra (false). */
    @Serializable @SerialName("logo")
    data class ShowLogo(val show: Boolean) : ClientMessage

    /** Aviso en la franja de abajo; "" lo quita. */
    @Serializable @SerialName("alert")
    data class SetAlert(val text: String) : ClientMessage

    @Serializable @SerialName("countdown_start")
    data class StartCountdown(val seconds: Int, val text: String = "") : ClientMessage

    @Serializable @SerialName("countdown_stop")
    data object StopCountdown : ClientMessage

    /** Buscar una frase en la Biblia; responde con ServerMessage.BibleResults. */
    @Serializable @SerialName("bible_search")
    data class BibleSearch(val query: String) : ClientMessage
}

@Serializable
enum class PlanKind { SONG, BIBLE }

/** Un elemento de la lista del culto: una canción (ref = id) o una cita (ref = referencia). */
@Serializable
data class PlanItem(val kind: PlanKind, val ref: String, val title: String)

@Serializable
data class ServicePlanState(val items: List<PlanItem> = emptyList(), val current: Int = -1)

/** Mensajes que el escritorio envía a los remotos conectados. */
@Serializable
sealed interface ServerMessage {
    @Serializable @SerialName("library")
    data class Library(val songs: List<SongSummary>, val bibleName: String? = null) : ServerMessage

    @Serializable @SerialName("state")
    data class State(
        val state: PresentationState,
        /** Hora del PC al enviar, para que los remotos corrijan la diferencia de reloj en la cuenta regresiva. */
        val serverNow: Long = 0,
    ) : ServerMessage

    @Serializable @SerialName("bible_results")
    data class BibleResults(val query: String, val hits: List<BibleHit>, val total: Int = hits.size) : ServerMessage

    @Serializable @SerialName("plan")
    data class Plan(val plan: ServicePlanState) : ServerMessage

    @Serializable @SerialName("error")
    data class Error(val message: String) : ServerMessage
}

val ProtocolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}

fun ClientMessage.encode(): String = ProtocolJson.encodeToString(ClientMessage.serializer(), this)
fun ServerMessage.encode(): String = ProtocolJson.encodeToString(ServerMessage.serializer(), this)
fun decodeClientMessage(text: String): ClientMessage = ProtocolJson.decodeFromString(ClientMessage.serializer(), text)
fun decodeServerMessage(text: String): ServerMessage = ProtocolJson.decodeFromString(ServerMessage.serializer(), text)
