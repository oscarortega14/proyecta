package app.selah.remote

import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.utils.io.ByteReadChannel

/*
 * Lo que cambia entre Android e iPhone. Cada sistema lo implementa en androidMain / iosMain.
 */

/** Hora actual en milisegundos (como System.currentTimeMillis). */
expect fun nowMillis(): Long

/** Fecha de hoy en el teléfono: año, mes (1-12), día. */
expect fun today(): Triple<Int, Int, Int>

/** Cliente HTTP con el motor de cada sistema (OkHttp en Android, Darwin en iPhone). */
expect fun platformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient

/**
 * Busca a Selah en la red local con un mensaje UDP a todos (broadcast).
 * En iPhone esto requiere el permiso de Apple "multicast networking"; mientras tanto devuelve
 * una lista vacía y se usa la dirección escrita a mano o el QR.
 */
expect suspend fun discoverServers(timeoutMs: Long): List<FoundServer>

/** Un archivo elegido por el usuario para subir (foto o video). */
class PickedFile(val name: String, val size: Long?, val open: () -> ByteReadChannel)

/**
 * Botón "Subir foto o video": devuelve la función que abre el selector del sistema,
 * o null si este sistema todavía no lo tiene (entonces no se muestra el botón).
 */
@Composable
expect fun rememberMediaPicker(onPicked: (PickedFile) -> Unit): (() -> Unit)?

/** Preferencias guardadas en el teléfono (SharedPreferences / NSUserDefaults). */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(vararg pairs: Pair<String, String>)
}
