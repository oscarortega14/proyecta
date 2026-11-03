package app.proyecta.desktop

import app.proyecta.shared.ClientMessage
import app.proyecta.shared.PlanItem
import app.proyecta.shared.PlanKind
import app.proyecta.shared.ProtocolJson
import app.proyecta.shared.ServicePlanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Lista del culto: el orden de canciones y lecturas preparado de antemano.
 * Se guarda en [file] después de cada cambio para sobrevivir a un cierre del programa.
 */
class ServicePlan(
    private val file: File,
    private val library: Library,
    private val present: (ClientMessage) -> String?,
) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ServicePlanState> = _state.asStateFlow()

    @Synchronized
    fun handle(msg: ClientMessage): String? = when (msg) {
        is ClientMessage.PlanAddSong -> {
            val song = library.song(msg.id)
            if (song == null) "Canción no encontrada" else { update { it.copy(items = it.items + PlanItem(PlanKind.SONG, song.id, song.title)) }; null }
        }
        is ClientMessage.PlanAddBible -> addBible(msg.reference)
        is ClientMessage.PlanRemove -> { remove(msg.index); null }
        is ClientMessage.PlanMove -> { move(msg.from, msg.to); null }
        is ClientMessage.PlanGo -> go(msg.index)
        ClientMessage.PlanNext -> {
            val next = _state.value.current + 1
            if (next < _state.value.items.size) go(next) else "No hay más elementos en la lista"
        }
        ClientMessage.PlanClear -> { update { ServicePlanState() }; null }
        else -> null
    }

    private fun addBible(reference: String): String? {
        val bible = library.bible ?: return "No hay Biblia cargada"
        val passage = try { bible.lookup(reference) } catch (e: IllegalArgumentException) { return e.message }
        update { it.copy(items = it.items + PlanItem(PlanKind.BIBLE, passage.title, passage.title)) }
        return null
    }

    private fun go(index: Int): String? {
        val item = _state.value.items.getOrNull(index) ?: return null
        val error = when (item.kind) {
            PlanKind.SONG -> present(ClientMessage.SelectSong(item.ref))
            PlanKind.BIBLE -> present(ClientMessage.ShowBible(item.ref))
        }
        if (error == null) update { it.copy(current = index) }
        return error
    }

    private fun remove(index: Int) = update { s ->
        if (index !in s.items.indices) return@update s
        val current = when {
            index < s.current -> s.current - 1
            index == s.current -> -1
            else -> s.current
        }
        ServicePlanState(s.items.filterIndexed { i, _ -> i != index }, current)
    }

    /** Mueve un elemento; el "actual" sigue apuntando al mismo elemento. */
    private fun move(from: Int, to: Int) = update { s ->
        if (from !in s.items.indices || to !in s.items.indices || from == to) return@update s
        val items = s.items.toMutableList().apply { add(to, removeAt(from)) }
        val current = when (s.current) {
            from -> to
            in minOf(from, to)..maxOf(from, to) -> if (from < to) s.current - 1 else s.current + 1
            else -> s.current
        }
        ServicePlanState(items, current)
    }

    private fun update(f: (ServicePlanState) -> ServicePlanState) {
        val new = f(_state.value)
        if (new == _state.value) return
        _state.value = new
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(ProtocolJson.encodeToString(ServicePlanState.serializer(), new), Charsets.UTF_8)
        }
    }

    private fun load(): ServicePlanState = runCatching {
        ProtocolJson.decodeFromString(ServicePlanState.serializer(), file.readText(Charsets.UTF_8))
    }.getOrDefault(ServicePlanState())
}

/** Reparte cada orden: lo de la lista a [ServicePlan], el resto al [Presenter]. */
class Commands(val presenter: Presenter, val plan: ServicePlan) {
    fun handle(msg: ClientMessage): String? = when (msg) {
        is ClientMessage.PlanAddSong, is ClientMessage.PlanAddBible, is ClientMessage.PlanRemove,
        is ClientMessage.PlanMove, is ClientMessage.PlanGo, ClientMessage.PlanNext, ClientMessage.PlanClear,
        -> plan.handle(msg)
        else -> presenter.handle(msg)
    }

    companion object {
        fun create(library: Library): Commands {
            val presenter = Presenter(library)
            val plan = ServicePlan(File(library.baseDir, "lista-del-culto.json"), library, presenter::handle)
            return Commands(presenter, plan)
        }
    }
}
