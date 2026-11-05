package app.proyecta.remote

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Ventana de Android. Toda la app (pantallas, conexión) está en el módulo compartido :remoteUi,
 * que también usa el iPhone; aquí solo va lo propio de Android.
 */
class MainActivity : ComponentActivity() {
    private lateinit var client: RemoteClient
    private lateinit var store: AndroidStore
    /** Dirección recibida por un enlace proyecta://connect?host=… (desde el QR del navegador). */
    private val linkHost = MutableStateFlow<String?>(null)

    private fun readLink(intent: Intent?) {
        val link = intent?.dataString?.let(::parseConnectLink) ?: return
        linkHost.value = store.acceptLink(link)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // En pleno culto la pantalla no debe apagarse.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        store = AndroidStore(this)
        client = RemoteClient(lifecycleScope)
        readLink(intent)
        client.useStore(store)

        // Android 15+ dibuja la app detrás de las barras del sistema. La app es siempre oscura:
        // barras transparentes con íconos claros (si no, en un teléfono en modo claro la hora
        // salía oscura sobre el fondo oscuro).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { ProyectaRemoto(client, store, linkHost, topBanner = { UpdateBanner() }) }
    }

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }
}
