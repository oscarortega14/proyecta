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
    /** Firma Ed25519 del instalador (Proyecta-x.msi.sig). Sin firma válida no se instala. */
    val sigUrl: String? = null,
)

/**
 * Actualizaciones: se pregunta a GitHub por la última versión publicada; si es más nueva,
 * se descarga el .msi, se verifica y se instala (reemplaza la anterior gracias al upgradeUuid).
 */
object Updater {
    const val LATEST_URL = app.proyecta.shared.RELEASES_LATEST_URL

    /**
     * Llave pública de las versiones oficiales (Ed25519). La privada solo la tiene quien publica
     * (~/.config/proyecta/firma-ed25519.key, fuera del repositorio). Aunque alguien entrara a la cuenta
     * de GitHub y publicara un instalador falso, Proyecta no lo instalaría.
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

    /** Versión de este programa (la escribe el build en proyecta-build.properties). */
    val currentVersion: String by lazy {
        runCatching {
            Properties().apply {
                Updater::class.java.getResourceAsStream("/proyecta-build.properties")?.use { load(it) }
            }.getProperty("version")
        }.getOrNull()?.trim().orEmpty().ifEmpty { "0" }
    }

    /** "1.10.0" > "1.9.2"; ignora una "v" al inicio y sufijos como "-beta". */
    fun isNewer(candidate: String, current: String): Boolean = app.proyecta.shared.Releases.isNewer(candidate, current)

    /** ¿Avisar de esta versión? Solo si es más nueva y el operador no pidió saltarla. */
    fun shouldOffer(release: Release?, current: String, skipped: String?): Boolean =
        release != null && isNewer(release.version, current) && release.version != skipped

    /** Lee la respuesta de GitHub (releases/latest). null si no hay .msi o no se entiende. */
    fun parseRelease(json: String): Release? {
        val r = app.proyecta.shared.Releases.parse(json) ?: return null
        val msi = r.asset(".msi") ?: return null
        return Release(r.version, msi.url, r.notes, msi.sha256, msi.size, r.asset(".msi.sig")?.url)
    }

    /** Pregunta a GitHub. null si no hay internet, no hay versiones publicadas o algo falla. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            // PROYECTA_UPDATE_URL: solo para probar actualizaciones con un servidor local (la firma se exige igual).
            val url = System.getenv("PROYECTA_UPDATE_URL")?.takeIf { it.startsWith("http://127.0.0.1") || it.startsWith("http://localhost") } ?: LATEST_URL
            val c = URI(url).toURL().openConnection() as HttpURLConnection
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
            error("La descarga llegó dañada. Intenta de nuevo.")
        }
        // Firma oficial: sin ella (o si no corresponde) no se instala, venga de donde venga.
        val signature = release.sigUrl?.let { url ->
            runCatching {
                val s = URI(url).toURL().openConnection() as HttpURLConnection
                s.connectTimeout = 15000; s.readTimeout = 15000
                s.setRequestProperty("User-Agent", "Proyecta/$currentVersion")
                if (s.responseCode == 200) s.inputStream.use { it.readBytes().decodeToString() } else null
            }.getOrNull()
        }
        if (signature == null || !verifySignature(sha, signature)) {
            target.delete()
            Log.error("Actualización ${release.version} RECHAZADA: falta la firma oficial o no corresponde")
            error("Esta versión no tiene la firma oficial de Proyecta, así que no se instala por seguridad.")
        }
        Log.info("Actualización ${release.version} descargada y con firma válida")
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
