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

    /**
     * Estrés (antes la app se caía al dibujar un cuadro ya liberado): el video real con VLC dibujado
     * a la vez como videobeam (30 cps) y miniatura (10 cps), redibujando a 60 por segundo.
     *   PROYECTA_VIDEO=… PROYECTA_STRESS_SECONDS=120 gradlew.bat :desktopApp:test --tests '*VideoBackgroundsTest*'
     */
    @Test
    fun stressDrawingWhileVideoPlays() {
        val path = System.getenv("PROYECTA_VIDEO") ?: return
        val seconds = System.getenv("PROYECTA_STRESS_SECONDS")?.toLongOrNull() ?: return
        val file = File(path)
        lateinit var scene: ImageComposeScene
        javax.swing.SwingUtilities.invokeAndWait {
            scene = ImageComposeScene(960, 540, Density(1f)) {
                androidx.compose.foundation.layout.Row {
                    VideoBackground(file, androidx.compose.ui.Modifier.weight(2f), maxFps = 30)
                    VideoBackground(file, androidx.compose.ui.Modifier.weight(1f), maxFps = 10)
                }
            }
        }
        val start = System.currentTimeMillis()
        var frames = 0
        var lastSeq = -1L
        var distinct = 0
        while (System.currentTimeMillis() - start < seconds * 1000) {
            javax.swing.SwingUtilities.invokeAndWait {
                scene.render((System.currentTimeMillis() - start) * 1_000_000)
            }
            frames++
            Thread.sleep(16)
        }
        javax.swing.SwingUtilities.invokeAndWait { scene.close() }
        val rt = Runtime.getRuntime()
        println("ESTRES OK: $frames dibujos en $seconds s, memoria Java usada ${(rt.totalMemory() - rt.freeMemory()) / 1_048_576} MB")
    }
}
