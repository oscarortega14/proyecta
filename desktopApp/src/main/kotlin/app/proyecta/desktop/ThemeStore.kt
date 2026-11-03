package app.proyecta.desktop

import app.proyecta.shared.ProtocolJson
import app.proyecta.shared.Theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Tema de la proyección, guardado en [file] (Proyecta/tema.json). */
class ThemeStore(private val file: File) {
    private val _theme = MutableStateFlow(load())
    val theme: StateFlow<Theme> = _theme.asStateFlow()

    fun update(f: (Theme) -> Theme) {
        val new = f(_theme.value).sanitized()
        if (new == _theme.value) return
        _theme.value = new
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(ProtocolJson.encodeToString(Theme.serializer(), new), Charsets.UTF_8)
        }
    }

    private fun load(): Theme = runCatching {
        ProtocolJson.decodeFromString(Theme.serializer(), file.readText(Charsets.UTF_8)).sanitized()
    }.getOrDefault(Theme())
}
