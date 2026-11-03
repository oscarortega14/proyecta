package app.proyecta.shared

private val ACCENTS = mapOf(
    'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
    'à' to 'a', 'è' to 'e', 'ì' to 'i', 'ò' to 'o', 'ù' to 'u', 'â' to 'a', 'ê' to 'e',
    'î' to 'i', 'ô' to 'o', 'û' to 'u', 'ç' to 'c',
)

/** Minúsculas y sin tildes, conservando espacios: para buscar canciones. */
fun normalizeText(s: String): String = buildString(s.length) {
    for (c in s.lowercase()) append(ACCENTS[c] ?: c)
}

/** Solo letras y números, sin tildes ni espacios: para comparar nombres de libros. */
fun normalizeKey(s: String): String = buildString(s.length) {
    for (c in s.lowercase()) {
        val m = ACCENTS[c] ?: c
        if (m in 'a'..'z' || m in '0'..'9') append(m)
    }
}

/** Filtra canciones por título o primera línea; todas las palabras deben aparecer. */
fun List<SongSummary>.search(query: String): List<SongSummary> {
    val words = normalizeText(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return this
    return filter { s ->
        val hay = normalizeText(s.title + " " + s.firstLine)
        words.all { it in hay }
    }
}
