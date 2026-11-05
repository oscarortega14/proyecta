package app.proyecta.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.proyecta.shared.RELEASES_LATEST_URL
import app.proyecta.shared.ReleaseAsset
import app.proyecta.shared.Releases
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Una versión nueva de la app disponible en GitHub. */
data class AppUpdate(val version: String, val apk: ReleaseAsset)

/**
 * Actualizar la app sola: pregunta a GitHub por la última versión; si trae un APK más nuevo,
 * lo descarga, comprueba que llegó entero (SHA-256 de GitHub) y abre el instalador de Android.
 * Android solo lo acepta si está firmado con la MISMA clave que la app instalada.
 */
class AppUpdater(private val context: Context) {
    private val http = HttpClient(OkHttp)

    val currentVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"

    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val json = http.get(RELEASES_LATEST_URL) {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "ProyectaRemoto/$currentVersion")
            }.bodyAsText()
            val r = Releases.parse(json) ?: return@runCatching null
            val apk = r.asset(".apk") ?: return@runCatching null
            if (Releases.isNewer(r.version, currentVersion)) AppUpdate(r.version, apk) else null
        }.getOrNull()
    }

    /** Descarga el APK (con progreso) y lo verifica. Lanza un error con un mensaje claro si falla. */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "actualizacion").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val target = File(dir, "ProyectaRemoto-${update.version}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        http.prepareGet(update.apk.url) { header("User-Agent", "ProyectaRemoto/$currentVersion") }.execute { response ->
            check(response.status.value == 200) { "GitHub respondió ${response.status.value}" }
            val channel = response.bodyAsChannel()
            val total = update.apk.size.takeIf { it > 0 }
            var done = 0L
            target.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = channel.readAvailable(buf, 0, buf.size)
                    if (n < 0) break
                    out.write(buf, 0, n); digest.update(buf, 0, n); done += n
                    if (total != null) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (update.apk.sha256 != null && sha != update.apk.sha256) {
            target.delete()
            error("La descarga llegó dañada. Intenta de nuevo.")
        }
        target
    }

    /**
     * Abre el instalador de Android con el APK. En Android 8 o más nuevo, la primera vez hay que
     * permitir "instalar apps de esta fuente": se abre esa pantalla y luego hay que tocar Actualizar otra vez.
     * Devuelve false si primero hay que dar ese permiso.
     */
    fun install(apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }
}
