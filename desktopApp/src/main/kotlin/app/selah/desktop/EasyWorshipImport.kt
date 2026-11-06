package app.selah.desktop

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Trae las canciones de EasyWorship 6 y 7.
 *
 * EasyWorship guarda el cancionero en dos bases de datos SQLite, normalmente en
 * `C:\Users\Public\Documents\Softouch\EasyWorship\<perfil>\v6.1\Databases\Data\`:
 * - `Songs.db`     → tabla `song` (título, autor, copyright, número CCLI…)
 * - `SongWords.db` → tabla `word` (la letra de cada canción, en formato RTF)
 * Un respaldo de canciones (.ewsx) es un .zip con una base que tiene las dos tablas juntas.
 *
 * Se leen copias en una carpeta temporal: así no se toca nada de EasyWorship (aunque esté abierto).
 */
object EasyWorshipImport {

    /** Dónde suele estar EasyWorship en Windows (para buscarlo solo). */
    val defaultRoots: List<File>
        get() = listOfNotNull(System.getenv("PUBLIC")?.let { File(it, "Documents/Softouch/EasyWorship") }, File("C:/Users/Public/Documents/Softouch/EasyWorship"))

    /** Busca Songs.db de EasyWorship en las carpetas de siempre (el más reciente si hay varios perfiles). */
    fun findInstalled(roots: List<File> = defaultRoots): File? = roots.asSequence()
        .filter { it.isDirectory }
        .flatMap { root -> root.walkTopDown().maxDepth(6).filter { it.isFile && it.name.equals("Songs.db", true) } }
        .filter { File(it.parentFile, "SongWords.db").isFile }
        .maxByOrNull { it.lastModified() }

    /** ¿Este archivo es algo de EasyWorship que sabemos leer? */
    fun isSource(name: String): Boolean {
        val n = name.lowercase()
        return n == "songs.db" || n == "songwords.db" || n.endsWith(".ewsx")
    }

    /**
     * Lee las canciones a partir de lo que eligió la persona: `Songs.db` (o `SongWords.db`, se busca
     * el otro al lado) o un respaldo `.ewsx`.
     */
    fun read(source: File): List<ImportedSong> {
        val tmp = kotlin.io.path.createTempDirectory("selah-easyworship").toFile()
        try {
            if (source.extension.equals("ewsx", true)) {
                // Respaldo: un .zip con la base adentro.
                java.util.zip.ZipFile(source).use { zip ->
                    for (e in zip.entries().asSequence().filter { !it.isDirectory && it.size < 500_000_000 }) {
                        val f = File(tmp, "base-${e.name.hashCode()}.db")
                        zip.getInputStream(e).use { input -> f.outputStream().use { input.copyTo(it) } }
                        val songs = runCatching { readDatabases(f, f) }.getOrNull()
                        if (!songs.isNullOrEmpty()) return songs
                    }
                }
                return emptyList()
            }
            val dir = source.parentFile ?: return emptyList()
            val songsDb = dir.listFiles().orEmpty().firstOrNull { it.name.equals("Songs.db", true) } ?: return emptyList()
            val wordsDb = dir.listFiles().orEmpty().firstOrNull { it.name.equals("SongWords.db", true) } ?: songsDb
            return readDatabases(copyDb(songsDb, tmp), if (wordsDb == songsDb) null else copyDb(wordsDb, tmp))
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** Copia la base y sus archivos de diario (-wal/-shm), por si EasyWorship estaba abierto. */
    private fun copyDb(db: File, into: File): File {
        val target = File(into, db.name)
        db.copyTo(target, overwrite = true)
        for (suffix in listOf("-wal", "-shm")) File(db.path + suffix).takeIf { it.isFile }?.copyTo(File(target.path + suffix), true)
        return target
    }

    private fun open(f: File): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:${f.absolutePath}")
    }

    private fun Connection.columns(table: String): Set<String> =
        createStatement().use { st -> st.executeQuery("PRAGMA table_info($table)").use { rs ->
            buildSet { while (rs.next()) add(rs.getString("name").lowercase()) }
        } }

    /** songsDb tiene la tabla `song`; wordsDb la tabla `word` (null: está en la misma base). */
    fun readDatabases(songsDb: File, wordsDb: File?): List<ImportedSong> = open(songsDb).use { songs ->
        val cols = songs.columns("song")
        if ("title" !in cols) return emptyList()
        fun pick(vararg names: String) = names.firstOrNull { it in cols }
        val select = listOf("rowid", "title", pick("author"), pick("copyright"), pick("vendor_id", "reference_number")).filterNotNull()
        val words = (wordsDb?.let(::open) ?: songs)
        try {
            val wordCols = words.columns("word")
            if ("song_id" !in wordCols || "words" !in wordCols) return emptyList()
            val lyrics = HashMap<Long, String>()
            words.createStatement().use { st ->
                st.executeQuery("SELECT song_id, words FROM word").use { rs ->
                    while (rs.next()) lyrics.putIfAbsent(rs.getLong(1), rs.getString(2).orEmpty())
                }
            }
            val out = mutableListOf<ImportedSong>()
            songs.createStatement().use { st ->
                st.executeQuery("SELECT ${select.joinToString()} FROM song").use { rs ->
                    while (rs.next()) {
                        val id = rs.getLong("rowid")
                        val title = rs.getString("title")?.trim().orEmpty()
                        val ccli = pick("vendor_id", "reference_number")?.let { rs.getString(it) }
                        toSelah(
                            title = title,
                            author = pick("author")?.let { rs.getString(it) },
                            ccli = ccli,
                            words = rtfToText(lyrics[id].orEmpty()),
                        )?.let(out::add)
                    }
                }
            }
            out
        } finally {
            if (words !== songs) words.close()
        }
    }

    // --- Letra: de los bloques de EasyWorship al formato de Selah ---

    /** Nombres de sección de EasyWorship (inglés o español) → como se escriben en Selah. */
    private val SECTION = Regex(
        """^(verse|estrofa|verso|chorus|coro|pre-?chorus|pre-?coro|precoro|bridge|puente|tag|ending|final|intro|introducci[oó]n|outro|interludio|interlude|misc|other|pregón|pregon)\.?\s*(\d{0,2})\s*:?$""",
        RegexOption.IGNORE_CASE,
    )

    private fun sectionLabel(line: String): String? {
        val m = SECTION.matchEntire(line.trim()) ?: return null
        val n = m.groupValues[2].let { if (it.isEmpty()) "" else " $it" }
        val name = when (m.groupValues[1].lowercase().replace("-", "")) {
            "verse", "estrofa", "verso" -> "Estrofa"
            "chorus", "coro" -> "Coro"
            "prechorus", "precoro" -> "Pre-coro"
            "bridge", "puente" -> "Puente"
            "tag", "ending", "final", "outro" -> "Final"
            "intro", "introducción", "introduccion" -> "Intro"
            "interlude", "interludio" -> "Interludio"
            "pregón", "pregon" -> "Pregón"
            else -> "Otro"
        }
        return name + n
    }

    /** Arma el .txt de Selah. null si no tiene título o letra. */
    fun toSelah(title: String, author: String?, ccli: String?, words: String): ImportedSong? {
        if (title.isBlank()) return null
        val blocks = words.replace("\r\n", "\n").split(Regex("\n\\s*\n")).map { b ->
            b.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }.filter { it.isNotEmpty() }
        if (blocks.isEmpty()) return null
        val text = buildString {
            append("# ").append(title.trim()).append('\n')
            author?.trim()?.takeIf { it.isNotEmpty() }?.let { append("Autor: ").append(it.replace('\n', ' ')).append('\n') }
            ccli?.trim()?.takeIf { it.matches(Regex("\\d{1,10}")) && it.trimStart('0').isNotEmpty() }?.let { append("CCLI: ").append(it).append('\n') }
            for (block in blocks) {
                val label = sectionLabel(block.first())
                val lines = if (label != null) block.drop(1) else block
                if (lines.isEmpty()) continue // una etiqueta sola, sin letra
                append('\n')
                if (label != null) append(label).append(":\n")
                lines.forEach { append(it).append('\n') }
            }
        }
        return ImportedSong(title.trim(), text.replace(Regex("\n{3,}"), "\n\n"))
    }

    /**
     * RTF → texto. Lo justo para la letra de EasyWorship: párrafos, saltos de línea, tildes
     * (\'e1 y \u225) y se descartan tablas de fuentes, colores y otros grupos especiales.
     */
    fun rtfToText(rtf: String): String {
        if (!rtf.trimStart().startsWith("{\\rtf")) return rtf // ya es texto
        val out = StringBuilder()
        val skipStack = ArrayDeque<Boolean>()
        var skipping = false
        var ucSkip = 1
        var pendingSkip = 0 // caracteres de reemplazo después de \uN
        val bytes = java.io.ByteArrayOutputStream() // \'hh seguidos (para letras de varios bytes)
        var charset = charset("windows-1252")

        fun flushBytes() {
            if (bytes.size() > 0) { if (!skipping) out.append(String(bytes.toByteArray(), charset)); bytes.reset() }
        }
        fun emit(c: Char) {
            flushBytes()
            if (pendingSkip > 0) { pendingSkip--; return }
            if (!skipping) out.append(c)
        }

        var i = 0
        val n = rtf.length
        while (i < n) {
            val c = rtf[i]
            when (c) {
                '{' -> {
                    flushBytes(); skipStack.addLast(skipping); i++
                    // {\* …} es un destino que se puede ignorar
                    if (rtf.startsWith("\\*", i)) skipping = true
                    continue
                }
                '}' -> { flushBytes(); skipping = skipStack.removeLastOrNull() ?: false; i++; continue }
                '\r', '\n' -> { i++; continue }
                '\\' -> {
                    if (i + 1 >= n) { i++; continue }
                    val next = rtf[i + 1]
                    when {
                        next == '\\' || next == '{' || next == '}' -> { emit(next); i += 2; continue }
                        next == '\'' -> {
                            val hex = rtf.substring(i + 2, minOf(i + 4, n)).toIntOrNull(16)
                            if (pendingSkip > 0) pendingSkip--
                            else if (hex != null) bytes.write(hex)
                            i += 4; continue
                        }
                        next == '~' -> { emit(' '); i += 2; continue }
                        next == '-' || next == '_' -> { i += 2; continue }
                        next == '\n' || next == '\r' -> { emit('\n'); i += 2; continue }
                        !next.isLetter() -> { i += 2; continue }
                    }
                    // palabra de control: \nombre[-]123 y un espacio opcional
                    var j = i + 1
                    while (j < n && rtf[j].isLetter()) j++
                    val word = rtf.substring(i + 1, j)
                    var k = j
                    if (k < n && (rtf[k] == '-' || rtf[k].isDigit())) { k++; while (k < n && rtf[k].isDigit()) k++ }
                    val param = rtf.substring(j, k).toIntOrNull()
                    if (k < n && rtf[k] == ' ') k++
                    i = k
                    when (word) {
                        "par", "line", "sect", "page" -> emit('\n')
                        "tab" -> emit(' ')
                        "u" -> if (param != null) {
                            flushBytes()
                            val cp = if (param < 0) param + 65536 else param
                            if (!skipping) out.append(cp.toChar())
                            pendingSkip = ucSkip
                        }
                        "uc" -> ucSkip = param ?: 1
                        "ansicpg" -> param?.let { cp -> runCatching { charset = charset("windows-$cp") } }
                        "fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "listtable",
                        "listoverridetable", "rsidtbl", "generator", "themedata", "latentstyles", "datastore",
                        -> skipping = true
                    }
                    continue
                }
                else -> { emit(c); i++ }
            }
        }
        flushBytes()
        return out.toString().lines().joinToString("\n") { it.trim() }.trim()
    }
}
