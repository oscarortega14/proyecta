package app.selah.desktop

import java.io.File

/**
 * La carpeta de la biblioteca (canciones, Biblia, fondos, ajustes) en la carpeta personal del usuario.
 * Hasta la versión 2.4 el programa se llamaba Proyecta y la carpeta era "Proyecta": la primera vez que
 * se abre Selah se le cambia el nombre a "Selah". Si Windows no deja (un archivo abierto, permisos),
 * se sigue usando la vieja: nunca se pierde nada.
 */
object LibraryFolder {
    const val NAME = "Selah"
    const val OLD_NAME = "Proyecta"

    /** Qué pasó al buscar la carpeta (para el registro). */
    enum class Result { NEW, MOVED, KEPT_OLD }

    fun resolve(home: File = File(System.getProperty("user.home"))): Pair<File, Result> {
        val folder = File(home, NAME)
        val old = File(home, OLD_NAME)
        if (folder.exists() || !old.isDirectory) return folder to Result.NEW
        return if (runCatching { old.renameTo(folder) }.getOrDefault(false)) folder to Result.MOVED
        else old to Result.KEPT_OLD
    }

    /** La carpeta que usa este programa y qué pasó al buscarla (se resuelve una vez al abrir). */
    val resolved: Pair<File, Result> by lazy { resolve() }
    val dir: File get() = resolved.first
}
