package app.proyecta.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import app.proyecta.shared.ClientMessage
import app.proyecta.shared.PlanKind
import app.proyecta.shared.ServerMessage
import app.proyecta.shared.decodeClientMessage
import app.proyecta.shared.encode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnouncementsTest {
    private lateinit var dir: File
    private lateinit var library: Library
    private lateinit var commands: Commands

    /** PNG de 160×90 rojo (16:9) hecho con Skia. */
    private fun png(): ByteArray {
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(160, 90)
        surface.canvas.clear(0xFFD32F2F.toInt())
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("proyecta-anuncios").toFile()
        library = Library(dir).also { it.reload() }
        commands = Commands.create(library)
    }

    @AfterTest
    fun tearDown() { dir.deleteRecursively() }

    @Test
    fun addingAndListingAnnouncements() {
        val src = File(dir, "src").apply { mkdirs() }
        val img = File(src, "Cumpleaños.png").apply { writeBytes(png()) }
        val vid = File(src, "Retiro.mp4").apply { writeBytes(ByteArray(10)) }
        val txt = File(src, "notas.txt").apply { writeText("no") }
        assertEquals(2, library.addAnnouncements(listOf(img, vid, txt)))
        assertEquals(listOf("Cumpleaños.png", "Retiro.mp4"), library.catalog.value.announcements)
        assertEquals(0, library.addAnnouncements(listOf(img))) // ya estaba
        // Archivo copiado a mano a la carpeta: aparece al refrescar
        File(library.announcementsDir, "Ofrenda.jpg").writeBytes(png())
        library.refreshAnnouncements()
        assertTrue("Ofrenda.jpg" in library.catalog.value.announcements)
        // Nada fuera de la carpeta
        assertNull(library.announcementFile("../canciones/Canción de ejemplo.txt"))
        assertNull(library.announcementFile("noexiste.png"))
    }

    @Test
    fun showingAnAnnouncementAndUsingItInThePlan() {
        File(library.announcementsDir, "Retiro.png").writeBytes(png())
        library.refreshAnnouncements()
        assertNull(commands.handle(ClientMessage.ShowMedia("Retiro.png")))
        val s = commands.presenter.state.value
        assertEquals("Retiro.png", s.media)
        assertEquals("Anuncio: Retiro", s.title)
        assertNotNull(commands.handle(ClientMessage.ShowMedia("otro.png")))

        // Al pasar a una canción, el anuncio se quita
        commands.handle(ClientMessage.SelectSong("Canción de ejemplo"))
        assertNull(commands.presenter.state.value.media)

        // En la lista del culto
        assertNull(commands.handle(ClientMessage.PlanAddMedia("Retiro.png")))
        assertNotNull(commands.handle(ClientMessage.PlanAddMedia("otro.png")))
        val item = commands.plan.state.value.items.single()
        assertEquals(PlanKind.MEDIA, item.kind)
        commands.handle(ClientMessage.PlanGo(0))
        assertEquals("Retiro.png", commands.presenter.state.value.media)

        // Viaja por el protocolo y los remotos reciben la lista
        val m = ClientMessage.ShowMedia("Retiro.png")
        assertEquals(m, decodeClientMessage(m.encode()))
        val lib = ServerMessage.Library(emptyList(), announcements = library.catalog.value.announcements)
        assertTrue("Retiro.png" in (app.proyecta.shared.decodeServerMessage(lib.encode()) as ServerMessage.Library).announcements)
    }

    @Test
    fun projectorShowsTheWholeImage() {
        File(library.announcementsDir, "Retiro.png").writeBytes(png())
        commands.handle(ClientMessage.ShowMedia("Retiro.png"))
        val state = commands.presenter.state.value
        val look = Look(frozenTime = 0f, mediaFile = { library.announcementFile(it) })
        // Pantalla 4:3: la imagen 16:9 entra entera, con franjas negras arriba y abajo.
        val pixels = ImageComposeScene(400, 300, Density(1f)) { ProjectorScreen(state, look = look) }.use { scene ->
            scene.render(0) // la imagen se carga fuera del hilo de dibujo
            Thread.sleep(500)
            scene.render(1_000_000_000L)
        }.let { img ->
            val bmp = org.jetbrains.skia.Bitmap.makeFromImage(img)
            System.getenv("PROYECTA_RENDER_DIR")?.let { File(it, "anuncio.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
            listOf(bmp.getColor(200, 10), bmp.getColor(200, 150), bmp.getColor(200, 290))
        }
        val (top, middle, bottom) = pixels
        assertEquals(0xFF000000.toInt(), top, "franja negra arriba")
        assertEquals(0xFFD32F2F.toInt(), middle, "la imagen en el centro")
        assertEquals(0xFF000000.toInt(), bottom, "franja negra abajo")
    }
}
