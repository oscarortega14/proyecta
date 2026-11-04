package app.proyecta.desktop

import app.proyecta.shared.ProtocolJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.io.File

/** Ajustes del programa (no del tema): se guardan en Proyecta/ajustes.json. */
@Serializable
data class Settings(
    /** Pantalla de la proyección: id de la pantalla, [WINDOWED], o null = automático (la segunda pantalla). */
    val projectorScreen: String? = null,
    /** Nombre de la Biblia elegida (null = la primera disponible). */
    val bible: String? = null,
    /** Ya se mostró la guía de bienvenida. */
    val welcomeDone: Boolean = false,
    /** Versión nueva que el usuario pidió no volver a avisar. */
    val skippedUpdate: String? = null,
) {
    companion object {
        const val WINDOWED = "ventana"
    }
}

class SettingsStore(private val file: File) {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val value: Settings get() = _settings.value

    fun update(f: (Settings) -> Settings) {
        val new = f(_settings.value)
        if (new == _settings.value) return
        _settings.value = new
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(ProtocolJson.encodeToString(Settings.serializer(), new), Charsets.UTF_8)
        }
    }

    private fun load(): Settings = runCatching {
        ProtocolJson.decodeFromString(Settings.serializer(), file.readText(Charsets.UTF_8))
    }.getOrDefault(Settings())
}
