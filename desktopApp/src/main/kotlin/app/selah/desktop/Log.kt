package app.selah.desktop

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de lo que pasa (Selah/registro.log), para saber qué falló cuando algo sale mal en
 * una iglesia: un video que no abre, un celular que no conecta, un cierre inesperado.
 * Al pasar de [MAX_BYTES] se guarda como registro.1.log y se empieza uno nuevo (nunca crece sin fin).
 * No guarda claves ni contenido privado: solo eventos y errores.
 */
object Log {
    const val MAX_BYTES = 1_000_000L

    @Volatile private var file: File? = null
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    val current: File? get() = file

    /** Empezar a registrar en [dir]/registro.log y atrapar los errores que nadie atrapó. */
    fun start(dir: File) {
        dir.mkdirs()
        file = File(dir, "registro.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            error("Error inesperado en el hilo ${t.name}", e)
            previous?.uncaughtException(t, e)
        }
    }

    fun info(message: String) = write("INFO", message, null)
    fun warn(message: String, e: Throwable? = null) = write("AVISO", message, e)
    fun error(message: String, e: Throwable? = null) = write("ERROR", message, e)

    @Synchronized
    private fun write(level: String, message: String, e: Throwable?) {
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_BYTES) {
                val old = File(f.parentFile, "registro.1.log")
                old.delete()
                f.renameTo(old)
            }
            val line = buildString {
                append(time.format(Date())).append(' ').append(level).append(' ').append(message.replace('\n', ' '))
                if (e != null) {
                    append('\n')
                    val sw = StringWriter()
                    e.printStackTrace(PrintWriter(sw))
                    append(sw.toString().lines().take(25).joinToString("\n") { "    $it" })
                }
                append('\n')
            }
            f.appendText(line, Charsets.UTF_8)
        }
    }
}
