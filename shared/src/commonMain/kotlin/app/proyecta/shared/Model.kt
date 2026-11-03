package app.proyecta.shared

import kotlinx.serialization.Serializable

/** Una "diapositiva": una estrofa de canción o un versículo. */
@Serializable
data class Slide(
    val label: String = "",
    val text: String,
)

/** Resumen de una canción para listarla en el control remoto. */
@Serializable
data class SongSummary(
    val id: String,
    val title: String,
    val firstLine: String = "",
)

/** Lo que se está proyectando en este momento. */
@Serializable
data class PresentationState(
    val title: String = "",
    val slides: List<Slide> = emptyList(),
    val index: Int = -1,
    val blank: Boolean = false,
    val isBible: Boolean = false,
    /** Id (nombre de archivo) de la canción en pantalla; null si es Biblia o nada. */
    val songId: String? = null,
) {
    val current: Slide? get() = slides.getOrNull(index)

    /** Secciones para saltar rápido (Coro, Pregón 1…). Vacío para la Biblia. */
    val sections: List<Section> get() = if (isBible) emptyList() else slides.sections()

    /** Posición en [sections] de la sección que contiene la diapositiva actual, o -1. */
    val currentSection: Int get() = sections.indexOfLast { it.start <= index }
}

/** Una sección de canción: empieza en la diapositiva [start] y sigue hasta la próxima etiqueta. */
data class Section(val label: String, val start: Int)

/**
 * Cada diapositiva con etiqueta ("Coro", "Pregón 1"…) abre una sección; las diapositivas
 * sin etiqueta que la siguen le pertenecen. Lo que va antes de la primera etiqueta es "Inicio".
 * Si la canción no tiene etiquetas no hay secciones (no hace falta saltar).
 */
fun List<Slide>.sections(): List<Section> {
    if (none { it.label.isNotEmpty() }) return emptyList()
    val out = mutableListOf<Section>()
    forEachIndexed { i, s ->
        when {
            s.label.isNotEmpty() -> out += Section(s.label, i)
            i == 0 -> out += Section("Inicio", 0)
        }
    }
    return out
}

/** Canción completa (solo vive en el escritorio). */
data class Song(
    val id: String,
    val title: String,
    val slides: List<Slide>,
) {
    fun summary() = SongSummary(id, title, slides.firstOrNull()?.text?.lineSequence()?.firstOrNull().orEmpty())
}
