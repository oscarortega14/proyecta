package app.proyecta.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import app.proyecta.shared.PresentationState
import app.proyecta.shared.Slide
import app.proyecta.shared.Theme
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VideoBackgroundsTest {
    @Test
    fun classifiesAndListsVideos() {
        assertTrue(isVideoFile("Humo.MP4")); assertTrue(isVideoFile("a.webm")); assertTrue(isVideoFile("x.mov"))
        assertFalse(isVideoFile("foto.jpg")); assertFalse(isVideoFile("sin-extension"))
        val dir = createTempDirectory("proyecta-vid").toFile()
        val lib = Library(dir).also { it.reload() }
        File(lib.backgroundsDir, "b.mp4").writeBytes(byteArrayOf(0))
        File(lib.backgroundsDir, "a.MOV").writeBytes(byteArrayOf(0))
        File(lib.backgroundsDir, "foto.png").writeBytes(byteArrayOf(0))
        assertEquals(listOf("a.MOV", "b.mp4"), lib.videos())
        assertEquals(listOf("foto.png"), lib.backgrounds()) // las imágenes no incluyen videos
        dir.deleteRecursively()
    }

    /** Sin VLC (como en este entorno de pruebas) un video de fondo no rompe nada: se ve el fondo incluido. */
    @Test
    fun withoutVlcFallsBackGracefully() {
        if (VideoBackgrounds.available) return
        val dir = createTempDirectory("proyecta-vid2").toFile()
        val video = File(dir, "loop.mp4").apply { writeBytes(byteArrayOf(0)) }
        assertEquals(null, VideoBackgrounds.acquire(video))
        val state = PresentationState("A", listOf(Slide(text = "Hola")), 0, background = "loop.mp4")
        ImageComposeScene(320, 180, Density(1f)) {
            ProjectorScreen(state, look = Look(Theme(builtin = "luz"), backgroundFile = { video }, frozenTime = 0f))
        }.use { assertNotNull(it.render(0)) }
        dir.deleteRecursively()
    }

    /**
     * Reproducción real con VLC: solo corre si VLC está instalado y se indica un video.
     *   PROYECTA_VIDEO=C:\\ruta\\loop.mp4 gradlew.bat :desktopApp:test --tests '*VideoBackgroundsTest*'
     */
    @Test
    fun playsARealVideoWithVlc() {
        val path = System.getenv("PROYECTA_VIDEO") ?: return
        assertTrue(VideoBackgrounds.available, "VLC no encontrado")
        val file = File(path)
        val loop = assertNotNull(VideoBackgrounds.acquire(file))
        try {
            val deadline = System.currentTimeMillis() + 10_000
            while (loop.frame.value == null && System.currentTimeMillis() < deadline) {
                javax.swing.SwingUtilities.invokeAndWait { }
                Thread.sleep(100)
            }
            val frame = assertNotNull(loop.frame.value, "VLC no entregó ningún cuadro en 10 s")
            assertTrue(frame.width in 2..1280, "ancho ${frame.width}")
            println("VIDEO OK: ${frame.width}x${frame.height}")
        } finally {
            VideoBackgrounds.release(file)
        }
    }
}
