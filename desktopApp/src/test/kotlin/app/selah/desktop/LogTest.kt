package app.selah.desktop

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class LogTest {
    @Test
    fun writesEventsAndErrorsAndRotates() {
        val dir = createTempDirectory("selah-log").toFile()
        Log.start(dir)
        Log.info("Selah iniciando")
        Log.error("No pude abrir el video x.mp4", IllegalStateException("códec raro"))
        val text = File(dir, "registro.log").readText()
        assertTrue("INFO Selah iniciando" in text)
        assertTrue("ERROR No pude abrir el video x.mp4" in text && "códec raro" in text)

        // No crece sin fin: al pasar 1 MB se guarda como registro.1.log
        repeat(12_000) { Log.info("x".repeat(100)) }
        assertTrue(File(dir, "registro.1.log").isFile)
        assertTrue(File(dir, "registro.log").length() <= Log.MAX_BYTES + 200)
        dir.deleteRecursively()
    }

    @Test
    fun ordersThatFailAreLogged() {
        val dir = createTempDirectory("selah-log2").toFile()
        Log.start(dir)
        val p = Presenter(Library(dir).also { it.reload() })
        p.handle(app.selah.shared.ClientMessage.SelectSong("no existe"))
        assertTrue("SelectSong: Canción no encontrada" in File(dir, "registro.log").readText())
        dir.deleteRecursively()
    }
}
