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
}

/** Mensajes que el escritorio envía a los remotos conectados. */
@Serializable
sealed interface ServerMessage {
    @Serializable @SerialName("library")
    data class Library(val songs: List<SongSummary>, val bibleName: String? = null) : ServerMessage

    @Serializable @SerialName("state")
    data class State(val state: PresentationState) : ServerMessage

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
