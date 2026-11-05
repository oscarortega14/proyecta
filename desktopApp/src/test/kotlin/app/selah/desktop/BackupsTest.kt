package app.selah.desktop

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupsTest {
    private lateinit var dir: File
    private lateinit var settings: SettingsStore
    private var now = 1_700_000_000_000L

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("selah-resp").toFile()
        File(dir, "canciones").mkdirs(); File(dir, "canciones/A.txt").writeText("# A\n\nuno\n")
        File(dir, "predicaciones").mkdirs(); File(dir, "predicaciones/P.txt").writeText("# P\n\npunto\n")
        File(dir, "listas").mkdirs(); File(dir, "listas/Domingo.json").writeText("{}")
        File(dir, "historial.csv").writeText(History.HEADER + "\n")
        File(dir, "fondos").mkdirs(); File(dir, "fondos/video.mp4").writeBytes(ByteArray(1000)) // no entra
        settings = SettingsStore(File(dir, "ajustes.json"))
    }

    @AfterTest
    fun tearDown() { dir.deleteRecursively() }

    private fun backups() = Backups(dir, settings) { now }

    @Test
    fun weeklyBackupHasTheImportantThingsOnly() {
        val b = backups()
        assertTrue(b.isDue()) // nunca se hizo
        val zip = b.create()
        val names = java.util.zip.ZipFile(zip).use { z -> z.entries().asSequence().map { it.name }.toSet() }
        assertEquals(setOf("canciones/A.txt", "predicaciones/P.txt", "listas/Domingo.json", "historial.csv"), names)
        assertFalse(b.isDue())
        now += Backups.WEEK_MILLIS
        assertTrue(b.isDue()) // a la semana, otra vez
    }

    @Test
    fun keepsOnlyTheLastTenAndCopiesToTheExtraFolder() {
        val usb = File(dir, "usb").apply { mkdirs() }
        settings.update { it.copy(backupCopyDir = usb.absolutePath) }
        val b = backups()
        repeat(12) { b.create(); now += 1000 }
        assertEquals(Backups.KEEP, b.list().size)
        assertTrue(b.list().first().name > b.list().last().name) // el más reciente primero
        assertEquals(12, usb.listFiles()!!.size) // la copia extra no se poda
    }

    @Test
    fun restoreBringsBackDeletedSongsAndSavesTheCurrentStateFirst() {
        val b = backups()
        val zip = b.create()
        now += 1000
        File(dir, "canciones/A.txt").delete()
        File(dir, "canciones/Nueva.txt").writeText("# Nueva\n\nx\n")
        val n = b.restore(zip)
        assertEquals(4, n)
        assertTrue(File(dir, "canciones/A.txt").isFile)
        assertEquals(2, b.list().size) // se respaldó lo de antes de restaurar
    }

    @Test
    fun hostileZipCannotWriteOutsideSelah() {
        val evil = File(dir, "malo.zip")
        java.util.zip.ZipOutputStream(evil.outputStream()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("canciones/../../fuera.txt")); z.write("x".toByteArray()); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("ajustes.json")); z.write("{}".toByteArray()); z.closeEntry()
        }
        assertEquals(0, backups().restore(evil))
        assertFalse(File(dir.parentFile, "fuera.txt").exists())
    }

    @Test
    fun friendlyNames() {
        val f = File(dir, "Selah-respaldo-2026-11-08_103000.zip").apply { writeBytes(ByteArray(2048)) }
        assertEquals("domingo 8 nov 2026, 10:30 · 2 KB", describe(f))
    }
}
