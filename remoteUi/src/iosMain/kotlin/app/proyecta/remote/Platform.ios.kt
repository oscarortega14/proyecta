package app.proyecta.remote

import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun today(): Triple<Int, Int, Int> {
    val c = NSCalendar.currentCalendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay, fromDate = NSDate(),
    )
    return Triple(c.year.toInt(), c.month.toInt(), c.day.toInt())
}

actual fun platformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Darwin, config)

/**
 * TODO(iPhone): búsqueda UDP. Apple exige el permiso com.apple.developer.networking.multicast
 * (se pide en https://developer.apple.com/contact/request/networking-multicast) antes de poder
 * enviar broadcast. Mientras tanto: dirección escrita a mano o enlace proyecta://connect del QR.
 * Ver docs/IOS.md.
 */
actual suspend fun discoverServers(timeoutMs: Long): List<FoundServer> = emptyList()

/** TODO(iPhone): subir fotos con PHPickerViewController. Por ahora no se muestra el botón. */
@Composable
actual fun rememberMediaPicker(onPicked: (PickedFile) -> Unit): (() -> Unit)? = null

/** Preferencias del iPhone (NSUserDefaults). */
class IosStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun get(key: String): String? = defaults.stringForKey("proyecta.$key")
    override fun put(vararg pairs: Pair<String, String>) {
        pairs.forEach { (k, v) -> defaults.setObject(v, forKey = "proyecta.$k") }
    }
}
