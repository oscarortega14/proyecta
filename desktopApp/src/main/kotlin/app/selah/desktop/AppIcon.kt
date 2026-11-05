package app.selah.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap

/** Ícono de Selah (el calderón) para la barra de tareas y el título de las ventanas; null si no se encuentra. */
val appIcon: Painter? by lazy {
    runCatching {
        val bytes = AppIconAnchor::class.java.getResourceAsStream("/web/icons/icon-192.png")!!.use { it.readBytes() }
        BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
    }.getOrNull()
}

private object AppIconAnchor
