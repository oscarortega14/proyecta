package app.proyecta.desktop

import app.proyecta.shared.Bible
import app.proyecta.shared.Song
import app.proyecta.shared.SongParser
import app.proyecta.shared.SongSummary
import app.proyecta.shared.normalizeText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Lo que el remoto necesita saber de la biblioteca. */
data class ImportResult(val added: Int, val skipped: Int, val renamed: Int)

data class Catalog(
    val songs: List<SongSummary> = emptyList(),
    val bibleName: String? = null,
    val bibleError: String? = null,
)

/**
 * Biblioteca en disco:
 *   <base>/canciones/  un archivo .txt por canción
 *   <base>/biblia/     archivos .json (se usa el primero en orden alfabético)
 */
class Library(val baseDir: File) {
    val songsDir = File(baseDir, "canciones")
    val bibleDir = File(baseDir, "biblia")
    val backgroundsDir = File(baseDir, "fondos")

    @Volatile private var songs: Map<String, Song> = emptyMap()
    @Volatile var bible: Bible? = null
        private set

    private val _catalog = MutableStateFlow(Catalog())
    val catalog: StateFlow<Catalog> = _catalog.asStateFlow()

    fun song(id: String): Song? = songs[id]

    private fun songFile(id: String) = File(songsDir, "$id.txt")

    /** Texto tal como está en el archivo, para el editor. */
    fun rawText(id: String): String? = songFile(id).takeIf { it.isFile }?.let { readTextSmart(it) }

    /**
     * Guarda una canción y recarga la biblioteca. El nombre del archivo sale del título,
     * así que si el título cambia el archivo se renombra. Devuelve el id (nombre) final.
     * Lanza IllegalArgumentException con un mensaje para el usuario si algo no cuadra.
     */
    @Synchronized
    fun save(oldId: String?, text: String): String {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trimEnd() + "\n"
        if (!normalized.trimStart().startsWith("#")) {
            throw IllegalArgumentException("La primera línea debe ser el título, por ejemplo: # Cuán grande es Él")
        }
        val title = SongParser.parse("", normalized).title.trim()
        if (title.isEmpty()) throw IllegalArgumentException("Falta el título después de #")
        val newId = fileNameFor(title)
        if (newId.isEmpty()) throw IllegalArgumentException("El título no sirve como nombre de archivo")
        val existing = songFile(newId)
        val renaming = oldId != null && !newId.equals(oldId, ignoreCase = true)
        if ((oldId == null || renaming) && existing.exists()) {
            throw IllegalArgumentException("Ya existe una canción llamada \"$title\"")
        }

        songsDir.mkdirs()
        // Escribir primero a un temporal para no dejar el archivo a medias si algo falla.
        val tmp = File(songsDir, ".$newId.tmp")
        tmp.writeText(normalized, Charsets.UTF_8)
        if (oldId != null) songFile(oldId).takeIf { it.exists() }?.delete()
        if (!tmp.renameTo(existing)) {
            existing.writeText(normalized, Charsets.UTF_8)
            tmp.delete()
        }
        reload()
        return newId
    }

    /** Imágenes disponibles en Proyecta/fondos (nombres de archivo, en orden alfabético). */
    fun backgrounds(): List<String> {
        backgroundsDir.mkdirs()
        return backgroundsDir.listFiles { f -> f.isFile && f.extension.lowercase() in IMAGE_EXTENSIONS }
            .orEmpty().map { it.name }.sortedBy { it.lowercase() }
    }

    /** Videos para fondo en Proyecta/fondos (se reproducen con VLC). */
    fun videos(): List<String> {
        backgroundsDir.mkdirs()
        return backgroundsDir.listFiles { f -> f.isFile && isVideoFile(f.name) }
            .orEmpty().map { it.name }.sortedBy { it.lowercase() }
    }

    fun backgroundFile(name: String): File? =
        File(backgroundsDir, name).takeIf { it.isFile && it.parentFile == backgroundsDir }

    /**
     * Pone (o quita, con null) la línea "Fondo:" de una canción, justo debajo del título,
     * conservando el resto del texto. Devuelve el id de la canción.
     */
    @Synchronized
    fun setSongBackground(id: String, background: String?): String {
        val text = rawText(id) ?: throw IllegalArgumentException("Canción no encontrada")
        val lines = text.replace("\r\n", "\n").split('\n').filterNot { FONDO_LINE.matches(it.trim()) }.toMutableList()
        if (background != null) {
            val titleAt = lines.indexOfFirst { it.trimStart().startsWith("#") }
            lines.add(titleAt + 1, "Fondo: $background")
        }
        return save(id, lines.joinToString("\n"))
    }

    /**
     * Importa canciones: archivos .txt sueltos o dentro de .zip. Si ya existe una con el mismo
     * nombre y el mismo texto se omite; si el texto es distinto se guarda como "Nombre (2)".
     */
    @Synchronized
    fun importSongs(sources: List<File>): ImportResult {
        songsDir.mkdirs()
        var added = 0; var skipped = 0; var renamed = 0
        fun take(name: String, bytes: ByteArray) {
            val base = File(name).nameWithoutExtension.trim().ifEmpty { return }
            val text = decodeSmart(bytes)
            var target = File(songsDir, "$base.txt")
            if (target.exists()) {
                if (readTextSmart(target).trim() == text.trim()) { skipped++; return }
                var n = 2
                while (File(songsDir, "$base ($n).txt").exists()) n++
                target = File(songsDir, "$base ($n).txt")
                renamed++
            }
            target.writeText(text, Charsets.UTF_8)
            added++
        }
        for (src in sources) {
            when {
                src.isDirectory -> src.listFiles { f -> f.isFile && f.extension.equals("txt", true) }.orEmpty().forEach { take(it.name, it.readBytes()) }
                src.extension.equals("zip", true) -> java.util.zip.ZipFile(src).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".txt", true) && !it.name.contains("__MACOSX") }
                        .forEach { e -> take(e.name.substringAfterLast('/'), zip.getInputStream(e).readBytes()) }
                }
                src.extension.equals("txt", true) -> take(src.name, src.readBytes())
            }
        }
        reload()
        return ImportResult(added, skipped, renamed)
    }

    /** Guarda todas las canciones en un .zip (respaldo o para pasarlas a otro PC). Devuelve cuántas. */
    fun exportSongs(target: File): Int {
        val files = songsDir.listFiles { f -> f.isSongFile() }.orEmpty().sortedBy { it.name }
        java.util.zip.ZipOutputStream(target.outputStream()).use { zip ->
            for (f in files) {
                zip.putNextEntry(java.util.zip.ZipEntry("canciones/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return files.size
    }

    @Synchronized
    fun delete(id: String) {
        songFile(id).delete()
        reload()
    }

    @Synchronized
    fun reload() {
        songsDir.mkdirs()
        bibleDir.mkdirs()
        backgroundsDir.mkdirs()
        if (songsDir.listFiles { f -> f.isSongFile() }.isNullOrEmpty()) writeExampleSong()

        val loaded = songsDir.listFiles { f -> f.isSongFile() }.orEmpty()
            .map { f -> SongParser.parse(f.nameWithoutExtension, readTextSmart(f), f.nameWithoutExtension) }
            .sortedBy { normalizeText(it.title) }
        songs = loaded.associateBy { it.id }

        var error: String? = null
        // .json (formato propio) o .txt (formato VPL de eBible.org)
        val bibleFile = bibleDir.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("json", "txt") }
            .orEmpty().minByOrNull { it.name.lowercase() }
        bible = bibleFile?.let { f ->
            runCatching {
                val text = readTextSmart(f)
                if (f.extension.equals("json", true)) Bible.fromJson(text, f.nameWithoutExtension)
                else Bible.fromVpl(text, f.nameWithoutExtension)
            }
                .onFailure { error = "No pude leer ${f.name}: ${it.message}" }
                .getOrNull()
        }
        if (bibleFile == null) {
            // Sin Biblia propia en la carpeta: se usa la Reina-Valera 1909 que viene con el programa.
            bible = bundledBible
            if (bible == null) error = "No hay Biblia. Copia un archivo .json o .txt (VPL) en ${bibleDir.absolutePath}"
        }

        _catalog.value = Catalog(loaded.map { it.summary() }, bible?.name, error)
    }

    private fun File.isSongFile() = isFile && extension.equals("txt", true)

    private fun writeExampleSong() {
        File(songsDir, "Canción de ejemplo.txt").writeText(
            """
            # Canción de ejemplo

            Esta es la primera estrofa
            Cada bloque separado por una línea en blanco
            es una diapositiva

            Coro:
            Si la primera línea termina en dos puntos
            se usa como etiqueta (Coro, Verso 2, Puente…)

            Puedes borrar este archivo y crear
            un .txt por cada canción en esta carpeta
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
    }

    companion object {
        /** Reina-Valera 1909 (dominio público) incluida en el programa. */
        val bundledBible: Bible? by lazy {
            Library::class.java.getResourceAsStream("/biblia/Reina-Valera 1909.txt")?.use { input ->
                runCatching { Bible.fromVpl(decodeSmart(input.readBytes()), "Reina-Valera 1909") }.getOrNull()
            }
        }

        fun decodeSmart(bytes: ByteArray): String = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }.removePrefix("\uFEFF")

        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp")
        private val FONDO_LINE = Regex("""^fondo\s*:.*$""", RegexOption.IGNORE_CASE)
        private val FORBIDDEN = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

        /** "Salmo 2:7" -> "Salmo 2-7": caracteres que Windows no permite en nombres de archivo. */
        fun fileNameFor(title: String): String =
            title.replace(FORBIDDEN, "-").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(100).trim()

        /** Lee UTF-8; si el archivo fue guardado en ANSI (Bloc de notas viejo), usa Windows-1252. */
        fun readTextSmart(f: File): String {
            val bytes = f.readBytes()
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                String(bytes, Charset.forName("windows-1252"))
            }.removePrefix("﻿")
        }
    }
}
