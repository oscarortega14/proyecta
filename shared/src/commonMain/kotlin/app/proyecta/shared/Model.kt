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

private val VERSE_LABEL = Regex(""".*\d+:\d+$""")

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
    /** Mensaje del operador para la vista de escenario (no sale en el videobeam). */
    val stageMessage: String = "",
    /** Fondo propio de la canción en pantalla (archivo en Proyecta/fondos), si tiene. */
    val background: String? = null,
    /** Pantalla de espera: fondo + logo de la iglesia en vez de la letra. */
    val logo: Boolean = false,
    /** Aviso en una franja abajo ("" = sin aviso). Sale encima de la letra, no la reemplaza. */
    val alert: String = "",
    /** Cuenta regresiva activa: termina en este instante (milisegundos de época del PC), o null. */
    val countdownEnd: Long? = null,
    /** Texto sobre la cuenta regresiva, ej. "Comenzamos en". */
    val countdownText: String = "",
    /** Anuncio en pantalla: imagen o video (archivo en Proyecta/anuncios) en vez de letra, o null. */
    val media: String? = null,
    /** Predicación en pantalla (songId es el id de la predicación): las etiquetas salen como encabezado. */
    val sermon: Boolean = false,
) {
    /** Segundos que faltan de la cuenta regresiva (0 si terminó), o null si no hay. */
    fun countdownSeconds(now: Long): Long? = countdownEnd?.let { ((it - now + 999) / 1000).coerceAtLeast(0) }

    val current: Slide? get() = slides.getOrNull(index)

    /** Secciones para saltar rápido (Coro, Pregón 1…). Vacío para la Biblia. */
    val sections: List<Section> get() = when {
        isBible -> emptyList()
        // En una predicación, los versículos ("Juan 10:11") no son secciones para saltar: solo los puntos.
        sermon -> slides.sections().filterNot { VERSE_LABEL.matches(it.label) }
        else -> slides.sections()
    }

    /** Posición en [sections] de la sección que contiene la diapositiva actual, o -1. */
    val currentSection: Int get() = sections.indexOfLast { it.start <= index }

    /** Etiquetas sin repetir, en orden de aparición: los botones de salto. */
    val sectionLabels: List<String> get() = sections.map { it.label }.distinct()

    /** Etiqueta de la sección actual, o null. */
    val currentLabel: String? get() = sections.getOrNull(currentSection)?.label

    /**
     * A dónde saltar al tocar [label]. Si la sección se repite (por la línea "Orden:"),
     * va a la siguiente aparición después de la actual; si no hay más, a la primera.
     */
    fun jumpTarget(label: String): Int? {
        val starts = sections.filter { it.label == label }.map { it.start }
        if (starts.isEmpty()) return null
        val here = sections.getOrNull(currentSection)?.start ?: -1
        return starts.firstOrNull { it > here } ?: starts.first()
    }

    /** La diapositiva que sigue, para la vista de escenario. */
    val next: Slide? get() = slides.getOrNull(index + 1)
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
    /** Nombres de la línea "Orden:" que no corresponden a ninguna sección. */
    val unknownOrder: List<String> = emptyList(),
    /** Línea "Fondo:" de la canción: archivo en Proyecta/fondos. */
    val background: String? = null,
) {
    fun summary() = SongSummary(id, title, slides.firstOrNull()?.text?.lineSequence()?.firstOrNull().orEmpty())
}

/** 125 -> "02:05"; 3725 -> "1:02:05". */
fun formatClock(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(sec)}" else "${two(m)}:${two(sec)}"
}

/** Un resultado de búsqueda en la Biblia. */
@Serializable
data class BibleHit(val reference: String, val text: String)
