package app.selah.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.selah.shared.RELEASES_LATEST_URL
import app.selah.shared.ReleaseAsset
import app.selah.shared.Releases
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
    private val http = HttpClient(OkHttp) {
        // Tiempos de espera generosos para un Wi-Fi malo (la descarga en sí no tiene tope total).
        install(io.ktor.client.plugins.HttpTimeout) {
            connectTimeoutMillis = Downloader.CONNECT_TIMEOUT_MS
            socketTimeoutMillis = Downloader.SOCKET_TIMEOUT_MS
        }
    }

    val currentVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"

    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val json = http.get(RELEASES_LATEST_URL) {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "SelahRemoto/$currentVersion")
            }.bodyAsText()
            val r = Releases.parse(json) ?: return@runCatching null
            val apk = r.asset(".apk") ?: return@runCatching null
            if (Releases.isNewer(r.version, currentVersion)) AppUpdate(r.version, apk) else null
        }.getOrNull()
    }

    /**
     * Descarga el APK (con progreso), reintentando y siguiendo desde donde quedó si el Wi-Fi se corta,
     * y lo verifica. Si ya estaba bajado y entero, no lo vuelve a bajar. Lanza [DownloadException]
     * con un mensaje corto para el usuario si no se pudo.
     */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "actualizacion").apply { mkdirs() }
        val target = File(dir, "SelahRemoto-${update.version}.apk")
        // Se borran solo las de otras versiones: la parcial de esta se continúa.
        dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
        Downloader(http, "SelahRemoto/$currentVersion").download(update.apk.url, target, update.apk.size.takeIf { it > 0 }, update.apk.sha256, onProgress)
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
        // CLEAR_TASK: el instalador se abre limpio, sin pantallas viejas encima (ej. los ajustes de
        // "Fuentes desconocidas" de la primera vez, que si no aparecían antes que el instalador).
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        return true
    }
}
