package app.proyecta.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

/** Una versión publicada en GitHub (Releases) con su instalador .msi. */
data class Release(
    val version: String,
    val msiUrl: String,
    val notes: String,
    /** SHA-256 que publica GitHub para el archivo (si lo trae), para verificar la descarga. */
    val sha256: String? = null,
    val size: Long = 0,
)

/**
 * Actualizaciones: se pregunta a GitHub por la última versión publicada; si es más nueva,
 * se descarga el .msi, se verifica y se instala (reemplaza la anterior gracias al upgradeUuid).
 */
object Updater {
    const val LATEST_URL = "https://api.github.com/repos/oscarortega14/proyecta/releases/latest"

    /** Versión de este programa (la escribe el build en proyecta-build.properties). */
    val currentVersion: String by lazy {
        runCatching {
            Properties().apply {
                Updater::class.java.getResourceAsStream("/proyecta-build.properties")?.use { load(it) }
            }.getProperty("version")
        }.getOrNull()?.trim().orEmpty().ifEmpty { "0" }
    }

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

    /** ¿Avisar de esta versión? Solo si es más nueva y el operador no pidió saltarla. */
    fun shouldOffer(release: Release?, current: String, skipped: String?): Boolean =
        release != null && isNewer(release.version, current) && release.version != skipped

    /** Lee la respuesta de GitHub (releases/latest). null si no hay .msi o no se entiende. */
    fun parseRelease(json: String): Release? {
        val o = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
        if ((o["draft"] as? JsonPrimitive)?.contentOrNull == "true" || (o["prerelease"] as? JsonPrimitive)?.contentOrNull == "true") return null
        val version = o.str("tag_name")?.trim()?.removePrefix("v")?.ifEmpty { null } ?: return null
        val msi = (o["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .firstOrNull { it.str("name").orEmpty().endsWith(".msi", ignoreCase = true) } ?: return null
        val url = msi.str("browser_download_url")?.takeIf { it.startsWith("https://") } ?: return null
        val sha = msi.str("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
        val size = msi.str("size")?.toLongOrNull() ?: 0
        return Release(version, url, o.str("body").orEmpty().trim(), sha, size)
    }

    /** Pregunta a GitHub. null si no hay internet, no hay versiones publicadas o algo falla. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URI(LATEST_URL).toURL().openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "Proyecta/$currentVersion")
            if (c.responseCode != 200) return@runCatching null
            parseRelease(c.inputStream.use { it.readBytes().decodeToString() })
        }.getOrNull()
    }

    /** Descarga el instalador a la carpeta temporal, verificando el SHA-256 si GitHub lo publica. */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val target = File(System.getProperty("java.io.tmpdir"), "Proyecta-${release.version}.msi")
        val c = URI(release.msiUrl).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "Proyecta/$currentVersion")
        check(c.responseCode == 200) { "GitHub respondió ${c.responseCode}" }
        val total = c.contentLengthLong.takeIf { it > 0 } ?: release.size
        val digest = MessageDigest.getInstance("SHA-256")
        c.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n); digest.update(buf, 0, n); done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (release.sha256 != null && sha != release.sha256) {
            target.delete()
            error("La descarga llegó dañada (la firma no coincide). Intenta de nuevo.")
        }
        target
    }

    /** Ruta del Proyecta.exe instalado (solo existe en la versión instalada, no al probar con Gradle). */
    val installedExe: String? get() = System.getProperty("jpackage.app-path")

    val canInstall: Boolean get() = System.getProperty("os.name").startsWith("Windows") && installedExe != null

    /**
     * Instala el .msi con la barra de progreso de Windows (sin preguntas) y vuelve a abrir Proyecta.
     * Quien llama debe cerrar el programa justo después (el instalador reemplaza sus archivos).
     */
    fun installAndRestart(msi: File) {
        val exe = installedExe ?: error("Solo se puede actualizar la versión instalada")
        val script = File(System.getProperty("java.io.tmpdir"), "proyecta-actualizar.cmd")
        script.writeText(
            "@echo off\r\n" +
                "timeout /t 2 /nobreak >nul\r\n" +
                "msiexec /i \"${msi.absolutePath}\" /passive /norestart\r\n" +
                "start \"\" \"$exe\"\r\n",
        )
        ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", script.absolutePath).start()
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
}
