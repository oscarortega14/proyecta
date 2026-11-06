package app.selah.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import app.selah.shared.ClientMessage
import app.selah.shared.PlanItem
import app.selah.shared.PlanKind
import app.selah.shared.ServicePlanState
import app.selah.shared.forOldRemotes
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.Color
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresentationsTest {
    private lateinit var dir: File

    @BeforeTest
    fun setUp() { dir = createTempDirectory("selah-pres").toFile() }

    @AfterTest
    fun tearDown() { dir.deleteRecursively() }

    /** PDF de 16:9 con una página de cada color. */
    private fun pdf(file: File, vararg colors: Color) {
        PDDocument().use { doc ->
            for (c in colors) {
                val page = PDPage(PDRectangle(960f, 540f))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.setNonStrokingColor(c)
                    cs.addRect(0f, 0f, 960f, 540f)
                    cs.fill()
                }
            }
            doc.save(file)
        }
    }

    /** PowerPoint con un título por diapositiva. */
    private fun pptx(file: File, vararg titles: String) {
        org.apache.poi.xslf.usermodel.XMLSlideShow().use { show ->
            show.pageSize = java.awt.Dimension(960, 540)
            for (t in titles) {
                val slide = show.createSlide()
                val box = slide.createTextBox()
                box.anchor = java.awt.Rectangle(60, 200, 840, 120)
                box.setText(t).fontSize = 48.0
                // fondo azul para comprobar que se dibuja
                val bg = slide.createAutoShape()
                bg.anchor = java.awt.Rectangle(0, 0, 960, 80)
                bg.fillColor = Color(20, 40, 200)
            }
            file.outputStream().use { show.write(it) }
        }
    }

    @Test
    fun pdfPagesBecomeImagesAndAreReused() {
        val p = Presentations(File(dir, "presentaciones").apply { mkdirs() })
        pdf(File(p.dir, "Anuncios.pdf"), Color.RED, Color.GREEN, Color.BLUE)
        assertEquals(listOf("Anuncios.pdf"), p.list())

        val pages = p.pages("Anuncios.pdf")
        assertEquals(3, pages.size)
        val first = ImageIO.read(pages[0])
        assertEquals(Presentations.WIDTH, first.width)
        assertEquals(1080, first.height)
        assertEquals(Color.RED.rgb, first.getRGB(960, 540))
        assertEquals(Color.BLUE.rgb, ImageIO.read(pages[2]).getRGB(10, 10))
        assertNotNull(p.thumb("Anuncios.pdf", 1))
        assertEquals(320, ImageIO.read(p.thumb("Anuncios.pdf", 1)).width)
        assertEquals(pages[1], p.page("Anuncios.pdf", 1))
        assertNull(p.page("Anuncios.pdf", 3))

        // La segunda vez no se vuelve a convertir
        val stamp = pages[0].lastModified()
        Thread.sleep(20)
        p.pages("Anuncios.pdf")
        assertEquals(stamp, pages[0].lastModified())
        // Si el archivo cambia, sí
        pdf(File(p.dir, "Anuncios.pdf"), Color.YELLOW)
        File(p.dir, "Anuncios.pdf").setLastModified(System.currentTimeMillis() + 5000)
        assertEquals(1, p.pages("Anuncios.pdf").size)
        assertEquals(Color.YELLOW.rgb, ImageIO.read(p.page("Anuncios.pdf", 0)).getRGB(960, 540))
    }

    @Test
    fun powerPointSlidesAreDrawn() {
        val p = Presentations(File(dir, "presentaciones").apply { mkdirs() })
        pptx(File(p.dir, "Predicación.pptx"), "Juan 3:16", "Punto 1")
        val pages = p.pages("Predicación.pptx")
        assertEquals(2, pages.size)
        val img = ImageIO.read(pages[0])
        assertEquals(1920, img.width)
        assertEquals(1080, img.height)
        assertEquals(Color(20, 40, 200).rgb, img.getRGB(960, 60)) // la franja azul de arriba
        // Hay texto oscuro en el centro (el título)
        val dark = (0 until img.width step 4).count { x -> (400 until 650 step 4).any { y -> Color(img.getRGB(x, y)).red < 100 } }
        assertTrue(dark > 10, "no se dibujó el texto")
    }

    @Test
    fun badFilesAndOddNamesAreRefused() {
        val p = Presentations(File(dir, "presentaciones").apply { mkdirs() })
        File(p.dir, "roto.pdf").writeText("esto no es un PDF")
        val e = runCatching { p.pages("roto.pdf") }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException && e.message!!.contains("No pude abrir"))
        File(dir, "afuera.pdf").also { pdf(it, Color.RED) }
        assertNull(p.file("../afuera.pdf"))
        assertNull(p.file("anuncio.jpg"))
        assertTrue(runCatching { p.pages("../afuera.pdf") }.isFailure)
        // Los temporales de PowerPoint (~$archivo.pptx) no se listan
        File(p.dir, "~\$abierto.pptx").writeText("x")
        assertEquals(listOf("roto.pdf"), p.list())
    }

    @Test
    fun presenterAndServicePlanShowPresentations() {
        val library = Library(File(dir, "Selah")).also { it.reload() }
        pdf(File(library.presentations.dir, "Bienvenida.pdf"), Color.RED, Color.GREEN)
        library.refreshPresentations()
        assertEquals(listOf("Bienvenida.pdf"), library.catalog.value.presentations)

        val commands = Commands.create(library)
        assertNull(commands.handle(ClientMessage.ShowPresentation("Bienvenida.pdf")))
        val s = commands.presenter.state.value
        assertEquals("Bienvenida.pdf", s.presentation)
        assertEquals("Bienvenida", s.title)
        assertEquals(listOf("Diapositiva 1 de 2", "Diapositiva 2 de 2"), s.slides.map { it.text })
        assertTrue(s.sections.isEmpty())
        commands.handle(ClientMessage.Next)
        assertEquals(1, commands.presenter.state.value.index)
        // Otra cosa en pantalla: ya no es presentación
        commands.handle(ClientMessage.ShowBible("Juan 3:16"))
        assertNull(commands.presenter.state.value.presentation)
        assertNotNull(commands.handle(ClientMessage.ShowPresentation("no-existe.pdf")))

        // En la lista del culto
        assertNull(commands.handle(ClientMessage.PlanAddPresentation("Bienvenida.pdf")))
        assertEquals(PlanItem(PlanKind.PRESENTATION, "Bienvenida.pdf", "Presentación: Bienvenida"), commands.plan.state.value.items.last())
        assertNull(commands.handle(ClientMessage.PlanGo(commands.plan.state.value.items.lastIndex)))
        assertEquals("Bienvenida.pdf", commands.presenter.state.value.presentation)
        assertNotNull(commands.handle(ClientMessage.PlanAddPresentation("otra.pdf")))
    }

    @Test
    fun oldRemotesSeePresentationsAsAnnouncements() {
        val plan = ServicePlanState(listOf(PlanItem(PlanKind.SONG, "a", "A"), PlanItem(PlanKind.PRESENTATION, "p.pdf", "Presentación: p")))
        assertEquals(listOf(PlanKind.SONG, PlanKind.MEDIA), plan.forOldRemotes().items.map { it.kind })
        val noPresentations = ServicePlanState(listOf(PlanItem(PlanKind.SONG, "a", "A")))
        assertTrue(noPresentations.forOldRemotes() === noPresentations)
    }

    @Test
    fun projectorShowsTheSlideImage() {
        val p = Presentations(File(dir, "presentaciones").apply { mkdirs() })
        pdf(File(p.dir, "x.pdf"), Color.RED, Color.GREEN)
        p.pages("x.pdf")
        val state = app.selah.shared.PresentationState("x", listOf(app.selah.shared.Slide("", "1"), app.selah.shared.Slide("", "2")), 1, presentation = "x.pdf")
        val look = Look(frozenTime = 0f, presentationPage = { n, i -> p.page(n, i) })
        ImageComposeScene(640, 360, Density(1f)) { ProjectorScreen(state, look = look) }.use { scene ->
            var pixel = 0
            // La imagen se carga fuera del hilo de dibujo: se esperan unos cuadros
            for (frame in 1..50) {
                val img = scene.render(frame * 16_000_000L)
                pixel = img.peekPixels()!!.getColor(320, 180)
                if (pixel == Color.GREEN.rgb) break
                Thread.sleep(20)
            }
            assertEquals(Color.GREEN.rgb, pixel)
        }
        assertNotEquals(Color.GREEN.rgb, Color.RED.rgb)
    }
}
