package app.selah.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import app.selah.shared.PresentationState
import app.selah.shared.Slide
import app.selah.shared.Theme
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
        val dir = createTempDirectory("selah-vid").toFile()
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
        val dir = createTempDirectory("selah-vid2").toFile()
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
     *   SELAH_VIDEO=C:\\ruta\\loop.mp4 gradlew.bat :desktopApp:test --tests '*VideoBackgroundsTest*'
     */
    @Test
    fun playsARealVideoWithVlc() {
        val path = System.getenv("SELAH_VIDEO") ?: return
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

    /** Con VLC real: el modo liviano entrega cuadros más chicos y el anuncio (con sonido) más grandes. */
    @Test
    fun lightModeAndAnnouncementsWithVlc() {
        val path = System.getenv("SELAH_VIDEO") ?: return
        val file = File(path)
        fun firstFrame(announcement: Boolean, light: Boolean): VideoFrame {
            val loop = assertNotNull(VideoBackgrounds.acquire(file, announcement, light))
            try {
                val deadline = System.currentTimeMillis() + 10_000
                while (loop.frame.value == null && System.currentTimeMillis() < deadline) {
                    javax.swing.SwingUtilities.invokeAndWait { }
                    Thread.sleep(100)
                }
                return assertNotNull(loop.frame.value, "sin cuadros (anuncio=$announcement, liviano=$light)")
            } finally {
                VideoBackgrounds.release(file, announcement, light)
            }
        }
        val normal = firstFrame(false, false)
        val light = firstFrame(false, true)
        val ad = firstFrame(true, false)
        println("VIDEO normal=${normal.width} liviano=${light.width} anuncio=${ad.width}")
        assertTrue(light.width <= 854 && light.width < normal.width || normal.width <= 854)
        assertTrue(ad.width >= normal.width)
    }

    /**
     * Estrés (antes la app se caía al dibujar un cuadro ya liberado): el video real con VLC dibujado
     * a la vez como videobeam (30 cps) y miniatura (10 cps), redibujando a 60 por segundo.
     *   SELAH_VIDEO=… SELAH_STRESS_SECONDS=120 gradlew.bat :desktopApp:test --tests '*VideoBackgroundsTest*'
     */
    @Test
    fun stressDrawingWhileVideoPlays() {
        val path = System.getenv("SELAH_VIDEO") ?: return
        val seconds = System.getenv("SELAH_STRESS_SECONDS")?.toLongOrNull() ?: return
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
