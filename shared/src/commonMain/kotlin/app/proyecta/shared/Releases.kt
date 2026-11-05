package app.proyecta.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Dónde se publican las versiones (la app del PC y la de Android preguntan aquí). */
const val RELEASES_LATEST_URL = "https://api.github.com/repos/oscarortega14/proyecta/releases/latest"

/** Un archivo de una versión publicada (instalador, app, firma). */
data class ReleaseAsset(val name: String, val url: String, val sha256: String?, val size: Long)

/** Una versión publicada en GitHub. */
data class ReleaseInfo(val version: String, val notes: String, val assets: List<ReleaseAsset>) {
    /** El archivo que termina en [suffix] (".msi", ".apk", ".msi.sig"…), o null. */
    fun asset(suffix: String): ReleaseAsset? = assets.firstOrNull { it.name.endsWith(suffix, ignoreCase = true) && !(suffix == ".msi" && it.name.endsWith(".sig")) }
}

object Releases {
    /** "1.10.0" > "1.9.2"; ignora una "v" al inicio y sufijos como "-beta". */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Lee la respuesta de GitHub (releases/latest). null si es borrador, de prueba o no se entiende.
     * La versión debe ser solo números y puntos (termina en nombres de archivo); los enlaces, https.
     */
    fun parse(json: String): ReleaseInfo? {
        val o = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
        if (o.str("draft") == "true" || o.str("prerelease") == "true") return null
        val version = o.str("tag_name")?.trim()?.removePrefix("v")?.takeIf { Regex("""\d{1,4}(\.\d{1,4}){1,3}""").matches(it) } ?: return null
        val assets = (o["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { a ->
            val url = a.str("browser_download_url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            ReleaseAsset(
                name = a.str("name").orEmpty(),
                url = url,
                sha256 = a.str("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
                size = a.str("size")?.toLongOrNull() ?: 0,
            )
        }
        return ReleaseInfo(version, o.str("body").orEmpty().trim(), assets)
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
}
