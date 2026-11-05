package app.proyecta.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Un error de descarga ya explicado para el usuario (sin direcciones largas ni inglés). */
class DownloadException(message: String) : Exception(message)

/**
 * Descarga resistente a un Wi-Fi malo:
 * - espera hasta [SOCKET_TIMEOUT_MS] sin datos antes de dar por perdida la conexión (antes eran 10 s);
 * - si se corta, reintenta sola hasta [MAX_ATTEMPTS] veces y SIGUE desde donde quedó (Range);
 * - si el archivo ya está completo y su SHA-256 coincide, no lo vuelve a bajar.
 */
class Downloader(private val http: HttpClient, private val userAgent: String, private val pauseMs: Long = 2_000) {

    suspend fun download(url: String, target: File, expectedSize: Long?, sha256: String?, onProgress: (Float) -> Unit): File {
        if (sha256 != null && target.isFile && sha256Of(target) == sha256) { onProgress(1f); return target }
        var lastError: Exception? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                fetch(url, target, expectedSize, onProgress)
                if (expectedSize != null && expectedSize > 0 && target.length() < expectedSize) throw java.io.IOException("incompleto")
                if (sha256 != null && sha256Of(target) != sha256) {
                    target.delete() // dañado: se vuelve a bajar entero
                    throw java.io.IOException("no coincide")
                }
                return target
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_ATTEMPTS) delay(pauseMs * attempt)
            }
        }
        throw DownloadException(friendly(lastError))
    }

    private suspend fun fetch(url: String, target: File, expectedSize: Long?, onProgress: (Float) -> Unit) {
        val have = if (target.isFile) target.length() else 0L
        http.prepareGet(url) {
            header("User-Agent", userAgent)
            if (have > 0) header("Range", "bytes=$have-")
        }.execute { response ->
            val resuming = response.status.value == 206
            if (response.status.value != 200 && !resuming) {
                if (response.status.value == 416 && expectedSize != null && have >= expectedSize) return@execute // ya estaba entero
                throw java.io.IOException("HTTP ${response.status.value}")
            }
            val channel = response.bodyAsChannel()
            var done = if (resuming) have else 0L
            FileOutputStream(target, resuming).use { out ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = channel.readAvailable(buf, 0, buf.size)
                    if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (expectedSize != null && expectedSize > 0) onProgress((done.toFloat() / expectedSize).coerceIn(0f, 1f))
                }
            }
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 6
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val SOCKET_TIMEOUT_MS = 30_000L

        fun sha256Of(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { input -> val b = ByteArray(1 shl 16); while (true) { val n = input.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        fun friendly(e: Exception?): String = when {
            e?.message == "no coincide" -> "La descarga llegó dañada varias veces. Intenta más tarde."
            e is java.net.UnknownHostException -> "No hay internet en este dispositivo. Revisa el Wi-Fi y vuelve a tocar Actualizar."
            else -> "La conexión a internet está muy lenta o se cortó. Toca Actualizar para seguir desde donde quedó."
        }
    }
}
