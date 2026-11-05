package app.selah.desktop

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryFolderTest {
    private fun home() = createTempDirectory("casa").toFile().apply { deleteOnExit() }

    @Test
    fun newInstallUsesSelah() {
        val home = home()
        val (dir, result) = LibraryFolder.resolve(home)
        assertEquals(File(home, "Selah"), dir)
        assertEquals(LibraryFolder.Result.NEW, result)
    }

    @Test
    fun theOldProyectaFolderIsRenamedWithTheSongs() {
        val home = home()
        File(home, "Proyecta/canciones").mkdirs()
        File(home, "Proyecta/canciones/Sublime gracia.txt").writeText("# Sublime gracia")
        val (dir, result) = LibraryFolder.resolve(home)
        assertEquals(LibraryFolder.Result.MOVED, result)
        assertEquals(File(home, "Selah"), dir)
        assertEquals("# Sublime gracia", File(dir, "canciones/Sublime gracia.txt").readText())
        assertFalse(File(home, "Proyecta").exists())
    }

    @Test
    fun ifSelahAlreadyExistsTheOldFolderIsLeftAlone() {
        val home = home()
        File(home, "Selah").mkdirs()
        File(home, "Proyecta/canciones").mkdirs()
        val (dir, result) = LibraryFolder.resolve(home)
        assertEquals(File(home, "Selah"), dir)
        assertEquals(LibraryFolder.Result.NEW, result)
        assertTrue(File(home, "Proyecta/canciones").isDirectory)
    }
}
