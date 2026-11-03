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
 */
object SongParser {
    private const val MAX_LABEL = 30

    fun parse(id: String, text: String, fallbackTitle: String = id): Song {
        var title = fallbackTitle
        var titleFound = false
        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()

        for (raw in text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = raw.trim()
            if (!titleFound && line.startsWith("#")) {
                title = line.trimStart('#').trim().ifEmpty { fallbackTitle }
                titleFound = true
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
        return Song(id, title, slides)
    }
}
