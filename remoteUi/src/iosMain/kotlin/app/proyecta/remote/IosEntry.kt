package app.proyecta.remote

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import platform.UIKit.UIViewController

/*
 * Puerta de entrada para Swift (iosApp/). En Swift se llaman:
 *   IosEntryKt.MainViewController()      → la pantalla de la app
 *   IosEntryKt.openUrl(url: "proyecta://connect?host=…")  → enlace del QR / control web
 */

private object App {
    val store = IosStore()
    val linkHost = MutableStateFlow<String?>(null)
    val client by lazy { RemoteClient(MainScope()).also { it.useStore(store) } }
}

fun MainViewController(): UIViewController =
    ComposeUIViewController { ProyectaRemoto(App.client, App.store, App.linkHost) }

/** Enlace proyecta://connect?host=…&pin=… abierto desde Safari o la cámara (QR). */
fun openUrl(url: String) {
    val link = parseConnectLink(url) ?: return
    App.linkHost.value = App.store.acceptLink(link)
}
