package app.proyecta.shared

/**
 * Formato de las canciones (.txt):
 *
 *     # Título de la canción
 *
 *     Primera estrofa, línea 1
 *     Primera estrofa, línea 2
 *
 *     Coro:
 *     Línea del coro
 *     Línea del coro
 *
 * - La línea que empieza con "#" es el título (si falta, se usa el nombre del archivo).
 * - Cada bloque separado por una línea en blanco es una diapositiva.
 * - Si la primera línea de un bloque es corta y termina en ":" (ej. "Coro:"), es la etiqueta.
 * - Opcional, antes de la primera estrofa: "Orden: Inicio, Coro, Pregón 1, Coro" arma la
 *   secuencia repitiendo secciones sin copiar el texto. "Inicio" es lo que va antes de la
 *   primera etiqueta. Los nombres que no existen se ignoran (y se reportan en unknownOrder).
 */
object SongParser {
    private const val MAX_LABEL = 30
    private val ORDER_LINE = Regex("""^orden\s*:(.*)$""", RegexOption.IGNORE_CASE)
    const val START_LABEL = "Inicio"

    fun parse(id: String, text: String, fallbackTitle: String = id): Song {
        var title = fallbackTitle
        var titleFound = false
        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var order: List<String>? = null

        for (raw in text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = raw.trim()
            if (!titleFound && line.startsWith("#")) {
                title = line.trimStart('#').trim().ifEmpty { fallbackTitle }
                titleFound = true
                continue
            }
            val orderMatch = ORDER_LINE.matchEntire(line)
            if (orderMatch != null && order == null && blocks.isEmpty() && current.isEmpty()) {
                order = orderMatch.groupValues[1].split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                continue
            }
            if (line.isEmpty()) {
                if (current.isNotEmpty()) { blocks += current; current = mutableListOf() }
            } else {
                current += line
            }
        }
        if (current.isNotEmpty()) blocks += current

        val slides = blocks.map { block ->
            val first = block.first()
            if (block.size > 1 && first.endsWith(":") && first.length <= MAX_LABEL) {
                Slide(label = first.dropLast(1).trim(), text = block.drop(1).joinToString("\n"))
            } else {
                Slide(text = block.joinToString("\n"))
            }
        }
        if (order.isNullOrEmpty()) return Song(id, title, slides)
        return expand(id, title, slides, order)
    }

    private fun expand(id: String, title: String, slides: List<Slide>, order: List<String>): Song {
        // Agrupa las diapositivas por sección (clave sin tildes ni mayúsculas).
        val groups = LinkedHashMap<String, List<Slide>>()
        var key: String? = null
        var group = mutableListOf<Slide>()
        fun flush() { key?.let { k -> if (k !in groups) groups[k] = group } }
        for (s in slides) {
            if (s.label.isNotEmpty() || key == null) {
                flush()
                key = normalizeKey(s.label.ifEmpty { START_LABEL })
                group = mutableListOf()
            }
            group += s
        }
        flush()

        val unknown = mutableListOf<String>()
        val expanded = order.flatMap { name ->
            val g = groups[normalizeKey(name)]
            if (g == null) { unknown += name; emptyList() } else {
                // La primera diapositiva de cada repetición lleva la etiqueta (para los saltos).
                listOf(g.first().copy(label = g.first().label.ifEmpty { START_LABEL })) + g.drop(1)
            }
        }
        return Song(id, title, expanded.ifEmpty { slides }, unknownOrder = unknown)
    }
}
