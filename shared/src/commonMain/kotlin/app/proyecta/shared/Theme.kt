package app.proyecta.shared

import kotlinx.serialization.Serializable

/**
 * Cómo se ve la proyección. Se guarda en Proyecta/tema.json.
 * Colores en formato "#RRGGBB".
 */
@Serializable
data class Theme(
    /** Color de fondo (o color de arriba si hay [color2]). */
    val color: String = "#000000",
    /** Si no es null, degradado vertical de [color] a [color2]. */
    val color2: String? = null,
    /** Imagen de fondo: nombre de archivo dentro de Proyecta/fondos. */
    val image: String? = null,
    /** Cuánto oscurecer la imagen para que se lea la letra (0 = nada, 0.8 = casi negro). */
    val dim: Float = 0.4f,
    val textColor: String = "#FFFFFF",
    /** Multiplica el tamaño automático de la letra (0.6 a 1.4). */
    val fontScale: Float = 1f,
    val shadow: Boolean = true,
    /** Logo de la iglesia para la pantalla de espera: archivo dentro de Proyecta/fondos. */
    val logo: String? = null,
) {
    companion object {
        /** Fondos de color incluidos (no necesitan archivos). */
        val PRESETS: List<Pair<String, Theme>> = listOf(
            "Negro" to Theme(),
            "Azul noche" to Theme(color = "#0B1E3F", color2 = "#1E4A7A"),
            "Púrpura" to Theme(color = "#2A0845", color2 = "#6441A5"),
            "Atardecer" to Theme(color = "#3A1C71", color2 = "#B24F6E"),
            "Bosque" to Theme(color = "#0F2027", color2 = "#2C5364"),
            "Vino" to Theme(color = "#2B0A12", color2 = "#6E1423"),
        )

        val TEXT_COLORS: List<Pair<String, String>> = listOf(
            "Blanco" to "#FFFFFF",
            "Crema" to "#FFF4D6",
            "Amarillo" to "#FFE082",
            "Celeste" to "#B3E5FC",
        )

        private val HEX = Regex("^#[0-9a-fA-F]{6}$")
        fun isColor(s: String?) = s != null && HEX.matches(s)
    }

    /** Valores fuera de rango o colores mal escritos vuelven a algo seguro. */
    fun sanitized(): Theme = copy(
        color = if (isColor(color)) color else "#000000",
        color2 = color2?.takeIf { isColor(it) },
        image = image?.takeIf { it.isNotBlank() },
        logo = logo?.takeIf { it.isNotBlank() },
        dim = dim.coerceIn(0f, 0.85f),
        textColor = if (isColor(textColor)) textColor else "#FFFFFF",
        fontScale = fontScale.coerceIn(0.6f, 1.4f),
    )
}
