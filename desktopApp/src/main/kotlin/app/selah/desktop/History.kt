package app.selah.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Algo que se proyectó: una canción (ref = id) o una lectura (ref = cita). */
data class HistoryEntry(
    val date: LocalDate,
    val time: String,
    val kind: Kind,
    val title: String,
    val ref: String,
) {
    enum class Kind(val label: String) { SONG("Canción"), BIBLE("Lectura"), SERMON("Predicación") }
}

/**
 * Historial de lo que se proyectó, en Selah/historial.csv (se abre con Excel).
 * Cada canción o lectura cuenta una vez por día, aunque se proyecte varias veces.
 */
class History(
    private val file: File,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    /** Volver a leer de disco (después de restaurar un respaldo). */
    @Synchronized
    fun reloadFromDisk() { _entries.value = load() }

    @Synchronized
    fun record(kind: HistoryEntry.Kind, title: String, ref: String) {
        val t = now()
        val date = t.toLocalDate()
        if (_entries.value.any { it.date == date && it.kind == kind && it.ref == ref }) return
        val entry = HistoryEntry(date, t.format(HOUR), kind, title, ref)
        runCatching {
            file.parentFile?.mkdirs()
            if (!file.exists()) file.writeText(HEADER + "\n", Charsets.UTF_8)
            file.appendText(toCsv(entry) + "\n", Charsets.UTF_8)
        }
        _entries.value = _entries.value + entry
    }

    /** Última fecha en que se cantó cada canción (por id). */
    fun lastSung(): Map<String, LocalDate> = _entries.value.asSequence()
        .filter { it.kind == HistoryEntry.Kind.SONG }
        .groupBy { it.ref }
        .mapValues { (_, list) -> list.maxOf { it.date } }

    /** Canciones más cantadas desde [since]: título y cuántos días se cantó. */
    fun topSongs(since: LocalDate): List<Pair<String, Int>> = _entries.value
        .filter { it.kind == HistoryEntry.Kind.SONG && !it.date.isBefore(since) }
        .groupBy { it.ref }
        .map { (_, list) -> list.last().title to list.size }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    fun exportCsv(target: File) {
        target.writeText(HEADER + "\n" + _entries.value.joinToString("") { toCsv(it) + "\n" }, Charsets.UTF_8)
    }

    private fun load(): List<HistoryEntry> = runCatching {
        file.readLines(Charsets.UTF_8).drop(1).mapNotNull { line ->
            val f = parseCsvLine(line)
            if (f.size < 5) return@mapNotNull null
            val kind = HistoryEntry.Kind.entries.firstOrNull { it.label == f[2] } ?: return@mapNotNull null
            runCatching { HistoryEntry(LocalDate.parse(f[0]), f[1], kind, f[3], f[4]) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    companion object {
        const val HEADER = "fecha,hora,tipo,titulo,referencia"
        private val HOUR = DateTimeFormatter.ofPattern("HH:mm")

        internal fun toCsv(e: HistoryEntry) =
            listOf(e.date.toString(), e.time, e.kind.label, e.title, e.ref).joinToString(",") { csvField(it) }

        private fun csvField(s: String) =
            if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

        internal fun parseCsvLine(line: String): List<String> {
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { sb.append('"'); i++ }
                    c == '"' -> quoted = !quoted
                    c == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                    else -> sb.append(c)
                }
                i++
            }
            out += sb.toString()
            return out
        }
    }
}

private val DAYS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
private val MONTHS_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** "domingo 8 nov 2026" */
fun formatDay(d: LocalDate): String = "${DAYS[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]} ${d.year}"

/** "hoy", "ayer", "hace 5 días", "8 nov", "8 nov 2025" — para la lista de canciones. */
fun formatAgo(d: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(d, today)
    return when {
        days <= 0L -> "hoy"
        days == 1L -> "ayer"
        days < 7 -> "hace $days días"
        d.year == today.year -> "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]}"
        else -> "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]} ${d.year}"
    }
}
