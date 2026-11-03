package app.proyecta.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Una cita ya interpretada, ej. "1 Corintios 13:4-7". */
data class BibleReference(
    val book: String,
    val chapter: Int,
    val verseFrom: Int? = null,
    val verseTo: Int? = null,
) {
    companion object {
        // libro (puede empezar con 1-3), capítulo, y opcionalmente versículo o rango
        private val RX = Regex("""^\s*([1-3]?\s*[^\d\s][^\d]*?)\.?\s*(\d+)(?:\s*[:.,\s]\s*(\d+)(?:\s*[-–]\s*(\d+))?)?\s*$""")

        fun parse(query: String): BibleReference? {
            val m = RX.matchEntire(query) ?: return null
            val (book, ch, from, to) = m.destructured
            return BibleReference(
                book = book.trim(),
                chapter = ch.toInt(),
                verseFrom = from.toIntOrNull(),
                verseTo = to.toIntOrNull(),
            )
        }
    }
}

class Bible(val name: String, val books: List<Book>) {

    class Book(val name: String, val chapters: List<List<String>>) {
        internal val key = normalizeKey(name)
    }

    data class Passage(val title: String, val slides: List<Slide>)

    /** Busca una cita como "Juan 3:16", "sal 23", "1 co 13 4-7". Lanza IllegalArgumentException con un mensaje claro. */
    fun lookup(query: String): Passage {
        val ref = BibleReference.parse(query)
            ?: throw IllegalArgumentException("No entiendo la cita \"$query\". Ejemplo: Juan 3:16")
        val bookIndex = findBook(ref.book)
            ?: throw IllegalArgumentException("No encontré el libro \"${ref.book}\"")
        val book = books[bookIndex]
        val chapter = book.chapters.getOrNull(ref.chapter - 1)
            ?: throw IllegalArgumentException("${book.name} no tiene capítulo ${ref.chapter}")
        val from = ref.verseFrom ?: 1
        val to = (ref.verseTo ?: ref.verseFrom ?: chapter.size).coerceAtMost(chapter.size)
        if (from !in 1..chapter.size || to < from) {
            throw IllegalArgumentException("${book.name} ${ref.chapter} no tiene el versículo $from")
        }
        // Algunas traducciones dejan versículos vacíos (numeración distinta); no se proyectan.
        val slides = (from..to).mapNotNull { v ->
            chapter[v - 1].trim().takeIf { it.isNotEmpty() }?.let { Slide("${book.name} ${ref.chapter}:$v", it) }
        }
        if (slides.isEmpty()) {
            throw IllegalArgumentException("${book.name} ${ref.chapter}:$from no tiene texto en esta traducción")
        }
        val title = buildString {
            append(book.name).append(' ').append(ref.chapter)
            if (ref.verseFrom != null) {
                append(':').append(from)
                if (to != from) append('-').append(to)
            }
        }
        return Passage(title, slides)
    }

    /** Índice de búsqueda (texto normalizado de cada versículo), se arma la primera vez. */
    private val searchIndex: List<Pair<BibleHit, String>> by lazy {
        books.flatMap { book ->
            book.chapters.flatMapIndexed { c, verses ->
                verses.mapIndexedNotNull { v, text ->
                    text.trim().takeIf { it.isNotEmpty() }?.let { BibleHit("${book.name} ${c + 1}:${v + 1}", it) to normalizeText(it) }
                }
            }
        }
    }

    /**
     * Busca versículos que contengan todas las palabras (sin importar tildes ni mayúsculas).
     * Primero los que tienen la frase exacta, luego el resto en orden bíblico.
     * Devuelve hasta [limit] resultados y el total encontrado.
     */
    fun search(query: String, limit: Int = 50): Pair<List<BibleHit>, Int> {
        val phrase = normalizeText(query).split(' ').filter { it.isNotBlank() }.joinToString(" ")
        val words = phrase.split(' ').filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList<BibleHit>() to 0
        val matches = searchIndex.filter { (_, norm) -> words.all { it in norm } }
        val (exact, rest) = matches.partition { (_, norm) -> phrase in norm }
        return (exact + rest).take(limit).map { it.first } to matches.size
    }

    fun findBook(query: String): Int? {
        val q = normalizeKey(query)
        if (q.isEmpty()) return null
        ALIASES[q]?.let { alias -> books.indexOfFirst { it.key == alias }.takeIf { it >= 0 }?.let { return it } }
        books.indexOfFirst { it.key == q }.takeIf { it >= 0 }?.let { return it }
        return books.indexOfFirst { it.key.startsWith(q) }.takeIf { it >= 0 }
    }

    companion object {
        val BOOK_NAMES = listOf(
            "Génesis", "Éxodo", "Levítico", "Números", "Deuteronomio", "Josué", "Jueces", "Rut",
            "1 Samuel", "2 Samuel", "1 Reyes", "2 Reyes", "1 Crónicas", "2 Crónicas", "Esdras",
            "Nehemías", "Ester", "Job", "Salmos", "Proverbios", "Eclesiastés", "Cantares", "Isaías",
            "Jeremías", "Lamentaciones", "Ezequiel", "Daniel", "Oseas", "Joel", "Amós", "Abdías",
            "Jonás", "Miqueas", "Nahúm", "Habacuc", "Sofonías", "Hageo", "Zacarías", "Malaquías",
            "Mateo", "Marcos", "Lucas", "Juan", "Hechos", "Romanos", "1 Corintios", "2 Corintios",
            "Gálatas", "Efesios", "Filipenses", "Colosenses", "1 Tesalonicenses", "2 Tesalonicenses",
            "1 Timoteo", "2 Timoteo", "Tito", "Filemón", "Hebreos", "Santiago", "1 Pedro", "2 Pedro",
            "1 Juan", "2 Juan", "3 Juan", "Judas", "Apocalipsis",
        )

        private val ALIASES = mapOf(
            "jn" to "juan", "mt" to "mateo", "mc" to "marcos", "mr" to "marcos", "lc" to "lucas",
            "sal" to "salmos", "sl" to "salmos", "salmo" to "salmos", "stg" to "santiago",
            "heb" to "hebreos", "ap" to "apocalipsis", "apoc" to "apocalipsis", "flm" to "filemon",
            "cnt" to "cantares", "ec" to "eclesiastes", "pr" to "proverbios", "prov" to "proverbios",
        )

        private val VPL_LINE = Regex("""^(\S+)\s+(\d+):(\d+)\s+(.*)$""")
        // En la RV1909 "JEHOVÁ" va en mayúsculas siempre; todo lo demás en mayúsculas al inicio es letra capital.
        private val KEEP_UPPERCASE = setOf("JEHOVÁ", "JEHOVA")

        /**
         * Formato VPL de eBible.org (un versículo por línea), ej. spaRV1909_vpl.txt:
         *     GEN 1:1 EN el principio crió Dios los cielos y la tierra.
         * Los libros se toman en el orden en que aparecen (deben ser los 66 en orden canónico).
         */
        fun fromVpl(text: String, name: String): Bible {
            val order = mutableListOf<String>()
            val verses = mutableMapOf<String, MutableMap<Int, MutableMap<Int, String>>>()
            for (raw in text.removePrefix("﻿").lineSequence()) {
                val m = VPL_LINE.matchEntire(raw.trim()) ?: continue
                val (code, ch, v, body) = m.destructured
                if (code !in verses) { order += code; verses[code] = mutableMapOf() }
                val chapter = ch.toInt()
                val verse = v.toInt()
                verses.getValue(code).getOrPut(chapter) { mutableMapOf() }[verse] =
                    if (verse == 1) fixDropCap(body) else body
            }
            if (order.size != BOOK_NAMES.size) {
                throw IllegalArgumentException("Se esperaban 66 libros y el archivo tiene ${order.size}")
            }
            val books = order.mapIndexed { i, code ->
                val chapters = verses.getValue(code)
                Book(
                    BOOK_NAMES[i],
                    (1..chapters.keys.max()).map { c ->
                        val vs = chapters[c].orEmpty()
                        if (vs.isEmpty()) emptyList() else (1..vs.keys.max()).map { vs[it].orEmpty() }
                    },
                )
            }
            return Bible(name, books)
        }

        /** "EN el principio" -> "En el principio" (letra capital de la edición impresa). */
        internal fun fixDropCap(s: String): String {
            val first = s.substringBefore(' ')
            val isAllCaps = first.length > 1 && first.any { it.isLetter() } && first == first.uppercase()
            if (!isAllCaps || first.trimEnd(',', '.', ':', ';') in KEEP_UPPERCASE) return s
            return first[0] + first.substring(1).lowercase() + s.substring(first.length)
        }

        /**
         * Acepta dos formatos JSON:
         *  - {"name": "...", "books": [{"name": "Génesis", "chapters": [["v1", "v2"], ...]}, ...]}
         *  - [{"abbrev": "gn", "chapters": [[...]]}, ...]   (66 libros en orden canónico)
         */
        fun fromJson(text: String, fallbackName: String): Bible {
            val root = Json.parseToJsonElement(text.removePrefix("﻿"))
            val (name, array) = when (root) {
                is JsonObject -> (root["name"]?.jsonPrimitive?.contentOrNull ?: fallbackName) to
                    (root["books"]?.jsonArray ?: throw IllegalArgumentException("Falta \"books\" en el JSON"))
                is JsonArray -> fallbackName to root
                else -> throw IllegalArgumentException("Formato de Biblia no reconocido")
            }
            val books = array.mapIndexed { i, el ->
                val o = el.jsonObject
                val bookName = o["name"]?.jsonPrimitive?.contentOrNull ?: BOOK_NAMES.getOrElse(i) { "Libro ${i + 1}" }
                val chapters = (o["chapters"] ?: throw IllegalArgumentException("Falta \"chapters\" en $bookName"))
                    .jsonArray.map { ch -> ch.jsonArray.map { it.jsonPrimitive.content } }
                Book(bookName, chapters)
            }
            return Bible(name, books)
        }
    }
}
