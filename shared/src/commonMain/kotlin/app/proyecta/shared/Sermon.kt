package app.proyecta.shared

/**
 * Predicación: se escribe como una canción (mismo formato de texto) y se proyecta así:
 *
 *     # El buen pastor               ← portada con el título
 *
 *     Introducción:                  ← "Etiqueta:" sale como encabezado arriba del texto
 *     Dios nos busca primero
 *
 *     Biblia: Juan 10:11             ← se reemplaza por los versículos (en la traducción elegida)
 *
 *     Punto 1:
 *     Él conoce a sus ovejas
 */
object Sermon {
    private val BIBLE_LINE = Regex("""^(biblia|lectura|texto)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)

    /** La cita de un bloque "Biblia: Juan 10:11" (de una sola línea), o null si es texto normal. */
    fun bibleReference(slideText: String): String? {
        val line = slideText.trim().takeIf { '\n' !in it } ?: return null
        return BIBLE_LINE.matchEntire(line)?.groupValues?.get(2)?.trim()?.ifEmpty { null }
    }

    /** Diapositivas listas para proyectar y los problemas encontrados (citas que no existen). */
    data class Expanded(val slides: List<Slide>, val problems: List<String>)

    fun expand(sermon: Song, bible: Bible?): Expanded {
        val out = mutableListOf(Slide("", sermon.title))
        val problems = mutableListOf<String>()
        for (slide in sermon.slides) {
            val ref = bibleReference(slide.text)
            if (ref == null) { out += slide; continue }
            if (bible == null) {
                problems += "No hay Biblia cargada para \"$ref\""
                out += Slide(slide.label, ref)
                continue
            }
            try {
                out += bible.lookup(ref).slides
            } catch (e: IllegalArgumentException) {
                problems += e.message ?: "No encontré \"$ref\""
                out += Slide(slide.label, ref) // se ve la cita escrita, para no perder el lugar
            }
        }
        return Expanded(out, problems)
    }
}
