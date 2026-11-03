package app.proyecta.desktop

import app.proyecta.shared.ClientMessage
import app.proyecta.shared.PresentationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Única fuente de verdad de lo que se proyecta. La ventana de operador y los
 * remotos mandan las mismas órdenes (ClientMessage); todos ven el mismo estado.
 */
class Presenter(private val library: Library) {
    private val _state = MutableStateFlow(PresentationState())
    val state: StateFlow<PresentationState> = _state.asStateFlow()

    /** Aplica una orden. Devuelve un mensaje de error para mostrar, o null si salió bien. */
    fun handle(msg: ClientMessage): String? = when (msg) {
        is ClientMessage.SelectSong -> selectSong(msg.id)
        is ClientMessage.GoTo -> { _state.update { s -> if (msg.index in s.slides.indices) s.copy(index = msg.index) else s }; null }
        ClientMessage.Next -> { _state.update { s -> s.copy(index = (s.index + 1).coerceAtMost(s.slides.lastIndex)) }; null }
        ClientMessage.Prev -> { _state.update { s -> s.copy(index = (s.index - 1).coerceAtLeast(if (s.slides.isEmpty()) -1 else 0)) }; null }
        is ClientMessage.SetBlank -> { _state.update { it.copy(blank = msg.blank) }; null }
        is ClientMessage.ShowBible -> showBible(msg.reference)
        is ClientMessage.StageMessage -> { _state.update { it.copy(stageMessage = msg.text.trim()) }; null }
        // Las órdenes de la lista del culto las atiende ServicePlan (ver Commands).
        else -> "Orden no soportada aquí"
    }

    private fun selectSong(id: String): String? {
        val song = library.song(id) ?: return "Canción no encontrada (¿se borró el archivo?)"
        _state.update {
            PresentationState(
                song.title, song.slides, if (song.slides.isEmpty()) -1 else 0, blank = it.blank,
                songId = song.id, stageMessage = it.stageMessage, background = song.background,
            )
        }
        return null
    }

    /**
     * Llamar después de guardar una canción en el editor: si es la que está en pantalla,
     * se actualiza el texto sin moverse de la diapositiva actual (o la última que exista).
     */
    fun songSaved(oldId: String?, newId: String) {
        val song = library.song(newId) ?: return
        _state.update { s ->
            val onScreen = !s.isBible && s.songId != null && (s.songId == oldId || s.songId == newId)
            if (!onScreen) return@update s
            val idx = when {
                song.slides.isEmpty() -> -1
                else -> s.index.coerceIn(0, song.slides.lastIndex)
            }
            s.copy(title = song.title, slides = song.slides, index = idx, songId = song.id, background = song.background)
        }
    }

    private fun showBible(reference: String): String? {
        val bible = library.bible ?: return "No hay Biblia cargada"
        val passage = try {
            bible.lookup(reference)
        } catch (e: IllegalArgumentException) {
            return e.message
        }
        _state.update { PresentationState(passage.title, passage.slides, 0, blank = it.blank, isBible = true, stageMessage = it.stageMessage) }
        return null
    }
}
