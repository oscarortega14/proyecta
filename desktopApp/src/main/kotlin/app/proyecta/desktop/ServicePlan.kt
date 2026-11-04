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
    /** Carpeta de las listas guardadas con nombre (Proyecta/listas). */
    private val listsDir: File = File(file.parentFile, "listas"),
) {
    private val _state = MutableStateFlow(load(file) ?: ServicePlanState())
    val state: StateFlow<ServicePlanState> = _state.asStateFlow()

    private val _saved = MutableStateFlow(scanSaved())
    /** Nombres de las listas guardadas, la más reciente primero. */
    val saved: StateFlow<List<String>> = _saved.asStateFlow()

    @Synchronized
    fun handle(msg: ClientMessage): String? = when (msg) {
        is ClientMessage.PlanAddSong -> {
            val song = library.song(msg.id)
            if (song == null) "Canción no encontrada" else { update { it.copy(items = it.items + PlanItem(PlanKind.SONG, song.id, song.title)) }; null }
        }
        is ClientMessage.PlanAddBible -> addBible(msg.reference)
        is ClientMessage.PlanAddSermon -> {
            val sermon = library.sermon(msg.id)
            if (sermon == null) "Predicación no encontrada"
            else { update { it.copy(items = it.items + PlanItem(PlanKind.SERMON, sermon.id, "Predicación: ${sermon.title}")) }; null }
        }
        is ClientMessage.PlanAddMedia -> {
            if (library.announcementFile(msg.name) == null) "Anuncio no encontrado"
            else { update { it.copy(items = it.items + PlanItem(PlanKind.MEDIA, msg.name, "Anuncio: ${msg.name.substringBeforeLast('.')}")) }; null }
        }
        is ClientMessage.PlanRemove -> { remove(msg.index); null }
        is ClientMessage.PlanMove -> { move(msg.from, msg.to); null }
        is ClientMessage.PlanGo -> go(msg.index)
        ClientMessage.PlanNext -> {
            val next = _state.value.current + 1
            if (next < _state.value.items.size) go(next) else "No hay más elementos en la lista"
        }
        ClientMessage.PlanClear -> { update { ServicePlanState() }; null }
        is ClientMessage.PlanSave -> saveAs(msg.name)
        is ClientMessage.PlanOpen -> open(msg.name)
        else -> null
    }

    /** Guarda la lista actual en listas/<nombre>.json. Devuelve un mensaje de error o null. */
    @Synchronized
    fun saveAs(name: String): String? {
        val clean = cleanListName(name) ?: return "Escribe un nombre para la lista"
        if (_state.value.items.isEmpty()) return "La lista está vacía"
        val saved = _state.value.copy(current = -1, name = clean)
        val ok = runCatching {
            listsDir.mkdirs()
            File(listsDir, "$clean.json").writeText(ProtocolJson.encodeToString(ServicePlanState.serializer(), saved), Charsets.UTF_8)
        }.isSuccess
        if (!ok) return "No pude guardar la lista"
        update { it.copy(name = clean) }
        _saved.value = scanSaved()
        return null
    }

    /** Reemplaza la lista actual por una guardada. */
    @Synchronized
    fun open(name: String): String? {
        val loaded = load(File(listsDir, "$name.json")) ?: return "No encontré la lista \"$name\""
        update { loaded.copy(current = -1, name = name) }
        return null
    }

    /** Volver a leer de disco (después de restaurar un respaldo). */
    @Synchronized
    fun reloadFromDisk() {
        _state.value = load(file) ?: ServicePlanState()
        _saved.value = scanSaved()
    }

    @Synchronized
    fun deleteSaved(name: String) {
        File(listsDir, "$name.json").delete()
        _saved.value = scanSaved()
    }

    private fun scanSaved(): List<String> = listsDir.listFiles { f -> f.isFile && f.extension == "json" }.orEmpty()
        .sortedByDescending { it.lastModified() }
        .map { it.nameWithoutExtension }

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
            PlanKind.MEDIA -> present(ClientMessage.ShowMedia(item.ref))
            PlanKind.SERMON -> present(ClientMessage.ShowSermon(item.ref))
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

    private fun load(f: File): ServicePlanState? = runCatching {
        ProtocolJson.decodeFromString(ServicePlanState.serializer(), f.readText(Charsets.UTF_8))
    }.getOrNull()
}

/** Nombre seguro para archivo (sin / \\ : * ? " < > |), o null si queda vacío. */
fun cleanListName(name: String): String? =
    name.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "-").trim().trim('.').take(80).trim().ifEmpty { null }

private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** Nombre sugerido: el próximo domingo (o hoy si es domingo), ej. "Culto domingo 8 nov 2026". */
fun suggestedListName(today: java.time.LocalDate = java.time.LocalDate.now()): String {
    val sunday = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
    return "Culto domingo ${sunday.dayOfMonth} ${MONTHS[sunday.monthValue - 1]} ${sunday.year}"
}

/** Reparte cada orden: lo de la lista a [ServicePlan], el resto al [Presenter]. */
class Commands(val presenter: Presenter, val plan: ServicePlan, val history: History? = null) {
    fun handle(msg: ClientMessage): String? = when (msg) {
        is ClientMessage.PlanAddSong, is ClientMessage.PlanAddBible, is ClientMessage.PlanRemove,
        is ClientMessage.PlanMove, is ClientMessage.PlanGo, ClientMessage.PlanNext, ClientMessage.PlanClear,
        is ClientMessage.PlanSave, is ClientMessage.PlanOpen, is ClientMessage.PlanAddMedia, is ClientMessage.PlanAddSermon,
        -> plan.handle(msg)
        else -> presenter.handle(msg)
    }

    companion object {
        fun create(library: Library): Commands {
            val presenter = Presenter(library)
            val plan = ServicePlan(File(library.baseDir, "lista-del-culto.json"), library, presenter::handle)
            val history = History(File(library.baseDir, "historial.csv"))
            presenter.onShown = history::record
            return Commands(presenter, plan, history)
        }
    }
}
