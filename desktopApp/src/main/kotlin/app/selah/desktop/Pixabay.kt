package app.selah.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/** Un video encontrado en Pixabay, con la versión que conviene descargar. */
data class PixabayVideo(
    val id: Long,
    val tags: String,
    val duration: Int,
    val user: String,
    val pageUrl: String,
    val thumbnail: String,
    val downloadUrl: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
) {
    /** Nombre del archivo en Selah/fondos, ej. "Pixabay 29566 - partículas.mp4". */
    val fileName: String
        get() {
            val tag = tags.split(',').firstOrNull()?.trim().orEmpty()
            return Library.fileNameFor("Pixabay $id" + if (tag.isNotEmpty()) " - $tag" else "") + ".mp4"
        }
}

data class PixabayPage(val total: Int, val videos: List<PixabayVideo>)

/**
 * Buscador de fondos de video en Pixabay (licencia libre, sin atribución obligatoria).
 * Reglas de la API que se respetan: se guardan las respuestas 24 h, se descargan los archivos
 * (no se enlazan) y se indica que los resultados vienen de Pixabay.
 */
class PixabayClient(
    private val key: String = buildKey(),
    private val baseUrl: String = "https://pixabay.com/api/videos/",
) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()
    private val cache = ConcurrentHashMap<String, Pair<Long, PixabayPage>>()

    val configured: Boolean get() = key.isNotBlank()

    /** Busca videos; [onlyBackgrounds] limita a la categoría "fondos" (evita gente y marcas). */
    fun search(query: String, onlyBackgrounds: Boolean = true, page: Int = 1): PixabayPage {
        require(configured) { "Falta la clave de Pixabay en esta versión del programa" }
        val params = buildMap {
            put("key", key)
            put("q", query.trim().take(100))
            put("lang", "es")
            put("safesearch", "true")
            put("per_page", "30")
            put("page", page.toString())
            if (onlyBackgrounds) put("category", "backgrounds")
        }
        val url = baseUrl + "?" + params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, Charsets.UTF_8) }
        val cacheKey = url.substringAfter("&") // sin la clave
        cache[cacheKey]?.takeIf { System.currentTimeMillis() - it.first < 24 * 3600_000L }?.let { return it.second }

        val response = http.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString())
        when (response.statusCode()) {
            200 -> Unit
            429 -> throw IllegalStateException("Demasiadas búsquedas seguidas; espera un minuto")
            else -> throw IllegalStateException("Pixabay respondió ${response.statusCode()}")
        }
        return parse(response.body()).also { cache[cacheKey] = System.currentTimeMillis() to it }
    }

    /** Descarga a [target] (primero a un .part). [progress] recibe de 0 a 1. */
    fun download(video: PixabayVideo, target: File, progress: (Float) -> Unit = {}) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        val response = http.send(HttpRequest.newBuilder(URI(video.downloadUrl)).timeout(Duration.ofMinutes(10)).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
        check(response.statusCode() == 200) { "La descarga respondió ${response.statusCode()}" }
        val total = response.headers().firstValueAsLong("Content-Length").orElse(video.sizeBytes).coerceAtLeast(1)
        response.body().use { input ->
            part.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        check(part.length() > 0) { "La descarga quedó vacía" }
        if (target.exists()) target.delete()
        check(part.renameTo(target)) { "No pude guardar ${target.name}" }
        progress(1f)
    }

    companion object {
        /** Ancho mínimo útil: se descarga la versión más liviana que lo cumpla (la app reduce a 1280). */
        const val TARGET_WIDTH = 1280

        fun buildKey(): String = runCatching {
            Properties().apply {
                PixabayClient::class.java.getResourceAsStream("/selah-build.properties")?.use { load(it) }
            }.getProperty("pixabay.key").orEmpty().trim()
        }.getOrDefault("")

        fun parse(body: String): PixabayPage {
            val root = Json.parseToJsonElement(body).jsonObject
            val videos = root["hits"]!!.jsonArray.mapNotNull { el ->
                val h = el.jsonObject
                val renditions = h["videos"]?.jsonObject ?: return@mapNotNull null
                val options = renditions.values.map { it.jsonObject }.filter { it.str("url").isNotEmpty() && it.num("width") > 0 }
                // la más liviana con al menos TARGET_WIDTH de ancho; si ninguna llega, la más grande
                val pick = options.filter { it.num("width") >= TARGET_WIDTH }.minByOrNull { it.num("width") }
                    ?: options.maxByOrNull { it.num("width") } ?: return@mapNotNull null
                val thumb = renditions["medium"]?.jsonObject?.str("thumbnail").orEmpty()
                    .ifEmpty { options.firstNotNullOfOrNull { o -> o.str("thumbnail").ifEmpty { null } }.orEmpty() }
                PixabayVideo(
                    id = h["id"]!!.jsonPrimitive.long,
                    tags = h.str("tags"),
                    duration = h["duration"]?.jsonPrimitive?.int ?: 0,
                    user = h.str("user"),
                    pageUrl = h.str("pageURL"),
                    thumbnail = thumb,
                    downloadUrl = pick.str("url"),
                    width = pick.num("width"),
                    height = pick.num("height"),
                    sizeBytes = pick["size"]?.jsonPrimitive?.long ?: 0,
                )
            }
            return PixabayPage(root["totalHits"]?.jsonPrimitive?.int ?: videos.size, videos)
        }

        private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content.orEmpty()
        private fun JsonObject.num(k: String) = this[k]?.jsonPrimitive?.int ?: 0

        /** Sugerencias de búsqueda (en español; Pixabay traduce con lang=es). */
        val SUGGESTIONS = listOf(
            "partículas", "bokeh", "luces", "rayos de luz", "nubes", "estrellas", "dorado",
            "cruz", "agua", "humo", "abstracto", "galaxia",
        )
    }
}
