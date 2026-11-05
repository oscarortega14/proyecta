package app.proyecta.desktop

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.util.concurrent.ConcurrentHashMap

/**
 * Tipografías incluidas para la letra del videobeam (libres, licencia OFL; ver resources/fuentes).
 * null = la del sistema (como antes).
 */
object Fonts {
    /** Nombre → descripción corta para el operador. */
    val CHOICES = linkedMapOf(
        "Montserrat" to "moderna",
        "Nunito" to "redondeada, amable",
        "Lato" to "clásica, muy legible",
        "Oswald" to "angosta: caben más palabras",
        "Merriweather" to "con remates (tipo libro)",
    )

    private val cache = ConcurrentHashMap<String, FontFamily>()

    fun family(name: String?): FontFamily? {
        if (name == null || name !in CHOICES) return null
        return cache.getOrPut(name) {
            val fonts = listOf(400, 600, 700).mapNotNull { w ->
                val path = "fuentes/$name-$w.ttf"
                if (Fonts::class.java.classLoader.getResource(path) == null) null
                else Font(path, FontWeight(w), FontStyle.Normal)
            } + listOfNotNull(
                "fuentes/$name-400-italic.ttf".takeIf { Fonts::class.java.classLoader.getResource(it) != null }
                    ?.let { Font(it, FontWeight.Normal, FontStyle.Italic) },
            )
            FontFamily(fonts)
        }
    }
}
