package app.selah.desktop

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
    /** Firma Ed25519 del instalador (Selah-x.msi.sig). Sin firma válida no se instala. */
    val sigUrl: String? = null,
)

/**
 * Actualizaciones: se pregunta a GitHub por la última versión publicada; si es más nueva,
 * se descarga el .msi, se verifica y se instala (reemplaza la anterior gracias al upgradeUuid).
 */
object Updater {
    const val LATEST_URL = app.selah.shared.RELEASES_LATEST_URL

    /**
     * Llave pública de las versiones oficiales (Ed25519). La privada solo la tiene quien publica
     * (~/.config/selah/firma-ed25519.key, fuera del repositorio). Aunque alguien entrara a la cuenta
     * de GitHub y publicara un instalador falso, Selah no lo instalaría.
     */
    const val PUBLIC_KEY = "MCowBQYDK2VwAyEABTkEXD35az+WB+TSi6dYSfcoYyUfQNcUb1ZBjJvIiUM="

    /** Lo que se firma: "proyecta-msi-v1:" + SHA-256 del instalador (igual que tools/Firma.java). */
    fun signedMessage(sha256Hex: String) = "proyecta-msi-v1:$sha256Hex".toByteArray()

    /** ¿[signatureB64] es una firma válida de las versiones oficiales para un instalador con este SHA-256? */
    fun verifySignature(sha256Hex: String, signatureB64: String, publicKey: String = PUBLIC_KEY): Boolean = runCatching {
        val key = java.security.KeyFactory.getInstance("Ed25519")
            .generatePublic(java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(publicKey)))
        java.security.Signature.getInstance("Ed25519").run {
            initVerify(key)
            update(signedMessage(sha256Hex))
            verify(java.util.Base64.getDecoder().decode(signatureB64.trim()))
        }
    }.getOrDefault(false)

    /** Versión de este programa (la escribe el build en selah-build.properties). */
    val currentVersion: String by lazy {
        runCatching {
            Properties().apply {
                Updater::class.java.getResourceAsStream("/selah-build.properties")?.use { load(it) }
            }.getProperty("version")
        }.getOrNull()?.trim().orEmpty().ifEmpty { "0" }
    }

    /** "1.10.0" > "1.9.2"; ignora una "v" al inicio y sufijos como "-beta". */
    fun isNewer(candidate: String, current: String): Boolean = app.selah.shared.Releases.isNewer(candidate, current)

    /** ¿Avisar de esta versión? Solo si es más nueva y el operador no pidió saltarla. */
    fun shouldOffer(release: Release?, current: String, skipped: String?): Boolean =
        release != null && isNewer(release.version, current) && release.version != skipped

    /** Lee la respuesta de GitHub (releases/latest). null si no hay .msi o no se entiende. */
    fun parseRelease(json: String): Release? {
        val r = app.selah.shared.Releases.parse(json) ?: return null
        val msi = r.asset(".msi") ?: return null
        return Release(r.version, msi.url, r.notes, msi.sha256, msi.size, r.asset(".msi.sig")?.url)
    }

    /** Pregunta a GitHub. null si no hay internet, no hay versiones publicadas o algo falla. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            // SELAH_UPDATE_URL: solo para probar actualizaciones con un servidor local (la firma se exige igual).
            val url = System.getenv("SELAH_UPDATE_URL")?.takeIf { it.startsWith("http://127.0.0.1") || it.startsWith("http://localhost") } ?: LATEST_URL
            val c = URI(url).toURL().openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "Selah/$currentVersion")
            if (c.responseCode != 200) return@runCatching null
            parseRelease(c.inputStream.use { it.readBytes().decodeToString() })
        }.getOrNull()
    }

    /** Descarga el instalador a la carpeta temporal, verificando el SHA-256 si GitHub lo publica. */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val target = File(System.getProperty("java.io.tmpdir"), "Selah-${release.version}.msi")
        val sha = resumableDownload(release.msiUrl, target, release.size.takeIf { it > 0 }, release.sha256, onProgress)
        // Firma oficial: sin ella (o si no corresponde) no se instala, venga de donde venga.
        val signature = release.sigUrl?.let { url ->
            runCatching {
                val s = URI(url).toURL().openConnection() as HttpURLConnection
                s.connectTimeout = 15000; s.readTimeout = 15000
                s.setRequestProperty("User-Agent", "Selah/$currentVersion")
                if (s.responseCode == 200) s.inputStream.use { it.readBytes().decodeToString() } else null
            }.getOrNull()
        }
        if (signature == null || !verifySignature(sha, signature)) {
            target.delete()
            Log.error("Actualización ${release.version} RECHAZADA: falta la firma oficial o no corresponde")
            error("Esta versión no tiene la firma oficial de Selah, así que no se instala por seguridad.")
        }
        Log.info("Actualización ${release.version} descargada y con firma válida")
        target
    }

    /**
     * Descarga resistente a un internet malo: espera hasta 30 s sin datos, reintenta hasta 6 veces
     * siguiendo desde donde quedó (Range) y, si el archivo ya estaba entero (mismo SHA-256), no lo
     * vuelve a bajar. Devuelve el SHA-256 del archivo. Los errores llegan con un mensaje corto.
     */
    internal fun resumableDownload(url: String, target: File, expectedSize: Long?, sha256: String?, onProgress: (Float) -> Unit, pauseMs: Long = 2_000): String {
        if (sha256 != null && target.isFile && sha256Of(target) == sha256) { onProgress(1f); return sha256 }
        var last: Exception? = null
        for (attempt in 1..6) {
            try {
                val have = if (target.isFile) target.length() else 0L
                val c = URI(url).toURL().openConnection() as HttpURLConnection
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "Selah/$currentVersion")
                if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                val code = c.responseCode
                val resuming = code == 206
                if (code != 200 && !resuming) throw java.io.IOException("HTTP $code")
                var done = if (resuming) have else 0L
                c.inputStream.use { input ->
                    java.io.FileOutputStream(target, resuming).use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); done += n
                            if (expectedSize != null) onProgress((done.toFloat() / expectedSize).coerceIn(0f, 1f))
                        }
                    }
                }
                if (expectedSize != null && target.length() < expectedSize) throw java.io.IOException("incompleto")
                val sha = sha256Of(target)
                if (sha256 != null && sha != sha256) { target.delete(); throw java.io.IOException("no coincide") }
                return sha
            } catch (e: Exception) {
                last = e
                Log.warn("Descarga de la actualización, intento $attempt: ${e.message}")
                if (attempt < 6) Thread.sleep(pauseMs * attempt)
            }
        }
        error(
            if (last is java.net.UnknownHostException) "No hay internet en este computador. Revisa la conexión y vuelve a intentar."
            else "La conexión a internet está muy lenta o se cortó. Vuelve a intentar: sigue desde donde quedó.",
        )
    }

    internal fun sha256Of(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input -> val b = ByteArray(1 shl 16); while (true) { val n = input.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Ruta del Selah.exe instalado (solo existe en la versión instalada, no al probar con Gradle). */
    val installedExe: String? get() = System.getProperty("jpackage.app-path")

    /** Instalado desde Microsoft Store (MSIX): la tienda lo actualiza, no el actualizador propio. */
    val fromStore: Boolean get() = isStorePath(installedExe)

    internal fun isStorePath(exe: String?): Boolean = exe?.replace('/', '\\')?.contains("\\WindowsApps\\", ignoreCase = true) == true

    val canInstall: Boolean get() = System.getProperty("os.name").startsWith("Windows") && installedExe != null && !fromStore

    /**
     * Instala el .msi con la barra de progreso de Windows (sin preguntas) y vuelve a abrir Selah.
     * Quien llama debe cerrar el programa justo después (el instalador reemplaza sus archivos).
     */
    fun installAndRestart(msi: File) {
        val exe = installedExe ?: error("Solo se puede actualizar la versión instalada")
        val script = File(System.getProperty("java.io.tmpdir"), "selah-actualizar.cmd")
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
