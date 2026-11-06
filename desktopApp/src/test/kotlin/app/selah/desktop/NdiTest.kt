package app.selah.desktop

import app.selah.shared.PresentationState
import app.selah.shared.Slide
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NdiTest {

    @Test
    fun structuresMatchTheNdiHeaders() {
        // Processing.NDI.structs.h en 64 bits: NDIlib_video_frame_v2_t ocupa 72 bytes y NDIlib_send_create_t 24.
        assertEquals(72, Ndi.VideoFrame().size())
        assertEquals(24, Ndi.SendCreate().size())
        val f = Ndi.VideoFrame()
        assertEquals(32, f.offsetOf("timecode"))
        assertEquals(40, f.offsetOf("p_data"))
        assertEquals(48, f.offsetOf("line_stride_in_bytes"))
        assertEquals(56, f.offsetOf("p_metadata"))
        assertEquals(0x41524742, Ndi.FOURCC_BGRA) // "BGRA"
    }

    @Test
    fun findsTheRuntimeInstalledByNdiTools() {
        val tmp = createTempDirectory("selah-ndi").toFile()
        try {
            val v5 = File(tmp, "v5").apply { mkdirs(); File(this, Ndi.libraryName).writeText("x") }
            val v6 = File(tmp, "v6").apply { mkdirs(); File(this, Ndi.libraryName).writeText("x") }
            val env = mapOf("NDI_RUNTIME_DIR_V5" to v5.path, "NDI_RUNTIME_DIR_V6" to v6.path)
            assertEquals(File(v6, Ndi.libraryName), Ndi.findRuntime(env::get, emptyList())) // la más nueva primero
            assertEquals(File(v5, Ndi.libraryName), Ndi.findRuntime(mapOf("NDI_RUNTIME_DIR_V5" to v5.path)::get, emptyList()))
            assertEquals(File(v5, Ndi.libraryName), Ndi.findRuntime({ null }, listOf(v5)))
            assertNull(Ndi.findRuntime({ null }, listOf(File(tmp, "no-existe"))))
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun pixel(frame: ByteArray, w: Int, x: Int, y: Int): IntArray {
        val i = (y * w + x) * 4
        return intArrayOf(frame[i].toInt() and 255, frame[i + 1].toInt() and 255, frame[i + 2].toInt() and 255, frame[i + 3].toInt() and 255) // B G R A
    }

    private val song = PresentationState("Castillo fuerte", listOf(Slide("", "Castillo fuerte es nuestro Dios\ndefensa y buen escudo")), 0)

    @Test
    fun lyricsFrameIsTransparentWithWhiteTextAtTheBottom() {
        FrameRenderer(NdiOutput.Kind.LYRICS, song, Look(frozenTime = 0f)).use { r ->
            val frame = r.render(song, Look(frozenTime = 0f), 0)
            assertEquals(1920 * 1080 * 4, frame.size)
            assertEquals(0, pixel(frame, 1920, 960, 200)[3]) // arriba: transparente
            // Abajo hay letra blanca opaca
            val white = (0 until 1920 step 3).sumOf { x ->
                (820 until 1030 step 3).count { y -> pixel(frame, 1920, x, y).let { it[3] == 255 && it[0] > 240 && it[1] > 240 && it[2] > 240 } }
            }
            assertTrue(white > 200, "no se ve la letra ($white)")

            // Pantalla en negro, presentación o anuncio: no hay letra
            for (s in listOf(song.copy(blank = true), song.copy(presentation = "x.pdf"), song.copy(media = "foto.jpg"))) {
                val f = r.render(s, Look(frozenTime = 0f), 16_000_000)
                assertTrue((0 until f.size step 4 * 97).all { f[it + 3].toInt() == 0 }, "debería ser todo transparente: $s")
            }
        }
    }

    @Test
    fun projectionFrameIsOpaqueLikeTheProjector() {
        FrameRenderer(NdiOutput.Kind.PROJECTION, song.copy(blank = true), Look(frozenTime = 0f)).use { r ->
            val black = r.render(song.copy(blank = true), Look(frozenTime = 0f), 0)
            assertEquals(listOf(0, 0, 0, 255), pixel(black, 1920, 960, 540).toList())
            // Con letra: hay texto claro en el centro sobre el fondo
            val lit = r.render(song, Look(theme = app.selah.shared.Theme(builtin = null), frozenTime = 0f), 16_000_000)
            val bright = (0 until 1920 step 4).count { x -> pixel(lit, 1920, x, 540).let { it[2] > 200 } }
            assertTrue((0 until lit.size step 4 * 101).all { lit[it + 3].toInt() == -1 }, "la proyección es opaca")
            assertTrue(bright > 0 || (300 until 800 step 10).any { y -> (0 until 1920 step 8).any { x -> pixel(lit, 1920, x, y)[2] > 200 } })
        }
    }
}

class NdiFrameCacheTest {
    @Test
    fun onlyRedrawsWhenSomethingChanges() {
        val s1 = PresentationState("x", listOf(Slide("", "uno"), Slide("", "dos")), 0)
        val look = Look(frozenTime = 0f)
        FrameRenderer(NdiOutput.Kind.LYRICS, s1, look).use { r ->
            val a = r.frame(s1, look, 0, nowMillis = 0)
            // Recién cambiado: se sigue dibujando un momento (imágenes que cargan aparte)
            val b = r.frame(s1, look, 0, nowMillis = 100)
            assertTrue(a !== b)
            // Pasado ese momento, sin cambios: el mismo cuadro, sin dibujar
            val c = r.frame(s1, look, 0, nowMillis = 5_000)
            val d = r.frame(s1, look, 0, nowMillis = 6_000)
            assertTrue(c === d)
            // Siguiente diapositiva: se dibuja de nuevo y cambia la imagen
            val e = r.frame(s1.copy(index = 1), look, 0, nowMillis = 6_100)
            assertTrue(e !== d && !e.contentEquals(d))
        }
    }
}
