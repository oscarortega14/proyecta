package app.proyecta.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow

/** Un enlace proyecta://connect?host=…&pin=… (botón "Abrir en la app" o QR del control web). */
data class ConnectLink(val host: String, val pin: String?)

/** Lee proyecta://connect?host=192.168.x.x[&pin=1234]; cualquier otra cosa devuelve null. */
fun parseConnectLink(url: String): ConnectLink? {
    val rest = url.trim().removePrefix("proyecta://connect").takeIf { it != url.trim() } ?: return null
    val query = rest.removePrefix("/").takeIf { it.startsWith("?") }?.drop(1) ?: return null
    val params = query.split('&').mapNotNull { p ->
        val i = p.indexOf('=')
        if (i <= 0) null else p.substring(0, i) to decodeQuery(p.substring(i + 1))
    }.toMap()
    val host = params["host"]?.trim().orEmpty()
    if (!host.matches(Regex("""^[0-9A-Za-z.\-:]{1,64}$"""))) return null
    val pin = params["pin"]?.trim()?.takeIf { it.matches(Regex("""\d{4,6}""")) }
    return ConnectLink(host, pin)
}

private fun decodeQuery(s: String): String = buildString {
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '+' -> append(' ')
            c == '%' && i + 2 < s.length ->
                s.substring(i + 1, i + 3).toIntOrNull(16)?.let { append(it.toChar()); i += 2 } ?: append(c)
            else -> append(c)
        }
        i++
    }
}

/**
 * Guarda el enlace recibido: la clave vale solo para ese computador.
 * Devuelve la dirección a la que hay que conectarse.
 */
fun KeyValueStore.acceptLink(link: ConnectLink): String {
    link.pin?.let { put("pin" to it, "pinHost" to link.host) }
    return link.host
}

/** Le enseña al cliente dónde está la clave guardada y la recuerda cuando el PC la acepta. */
fun RemoteClient.useStore(store: KeyValueStore) {
    // La clave se manda sola solo al computador donde se aceptó.
    // (Las versiones anteriores no guardaban de qué computador era: vale para el que ya estaba guardado.)
    savedPin = { host ->
        val pinHost = store.get("pinHost") ?: store.get("host")
        store.get("pin")?.takeIf { pinHost == host }
    }
    onPinAccepted = { host, pin -> store.put("pin" to pin, "pinHost" to host) }
    onHostChanged = { host -> store.put("host" to host) }
    store.get("host")?.takeIf { it.isNotEmpty() }?.let { connect(it) }
}

/**
 * Toda la app (igual en Android y en iPhone). Cada sistema solo pone la ventana alrededor.
 * @param linkHost dirección que llegó por un enlace proyecta://connect (se vuelve null al usarla)
 * @param topBanner aviso de actualización (solo la versión de Android de GitHub lo tiene)
 */
@Composable
fun ProyectaRemoto(
    client: RemoteClient,
    store: KeyValueStore,
    linkHost: MutableStateFlow<String?>,
    topBanner: @Composable () -> Unit = {},
) {
    val notices = remember { Notices() }
    LaunchedEffect(client) { client.errors.collect { notices.show(it, long = true) } }
    MaterialTheme(colorScheme = darkColorScheme()) {
        CompositionLocalProvider(LocalNotices provides notices) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                // El sistema dibuja la app detrás de sus barras: se deja libre su espacio
                // (barra de estado, botones, isla del iPhone y teclado) para que nada quede tapado.
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    var pcName by remember { mutableStateOf(store.get("name").orEmpty()) }
                    val link by linkHost.collectAsState()
                    val connect: (FoundServer) -> Unit = { server ->
                        store.put("host" to server.host, "name" to server.name)
                        pcName = server.name
                        client.connect(server.host)
                    }
                    // Enlace desde el navegador: conectarse a ese PC y averiguar su nombre.
                    LaunchedEffect(link) {
                        val host = link ?: return@LaunchedEffect
                        connect(FoundServer(client.pcName(host) ?: host, host))
                        linkHost.value = null
                    }
                    // Conexión guardada de una versión anterior, sin nombre: preguntárselo al PC.
                    LaunchedEffect(Unit) {
                        val host = store.get("host").orEmpty()
                        if (pcName.isEmpty() && host.isNotEmpty()) {
                            client.pcName(host)?.let { name ->
                                store.put("name" to name)
                                pcName = name
                            }
                        }
                    }
                    RemoteApp(
                        client = client,
                        savedHost = store.get("host").orEmpty(),
                        pcName = pcName,
                        linkKey = link,
                        onConnect = connect,
                        topBanner = topBanner,
                    )
                    NoticeHost(notices)
                }
            }
        }
    }
}
