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
    /** Clave de 4 números para controlar desde celulares y tablets (null = todavía no se creó). */
    val pin: String? = null,
    /** El operador quitó la clave a propósito: cualquiera en la red puede controlar. */
    val pinOff: Boolean = false,
    /** Último respaldo automático o manual (milisegundos). */
    val lastBackup: Long = 0,
    /** Carpeta extra donde copiar cada respaldo (memoria USB, Google Drive…), o null. */
    val backupCopyDir: String? = null,
) {
    /** La clave que se exige ahora (null = no se pide). */
    val activePin: String? get() = pin?.takeIf { !pinOff }

    companion object {
        const val WINDOWED = "ventana"

        fun randomPin(): String = (java.security.SecureRandom().nextInt(9000) + 1000).toString()
    }
}

class SettingsStore(private val file: File) {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val value: Settings get() = _settings.value

    private val _pin = MutableStateFlow(_settings.value.activePin)
    /** Clave en uso (para el servidor de los remotos). */
    val pin: StateFlow<String?> = _pin.asStateFlow()

    fun update(f: (Settings) -> Settings) {
        val new = f(_settings.value)
        if (new == _settings.value) return
        _settings.value = new
        _pin.value = new.activePin
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(ProtocolJson.encodeToString(Settings.serializer(), new), Charsets.UTF_8)
        }
    }

    private fun load(): Settings = runCatching {
        ProtocolJson.decodeFromString(Settings.serializer(), file.readText(Charsets.UTF_8))
    }.getOrDefault(Settings())
}
