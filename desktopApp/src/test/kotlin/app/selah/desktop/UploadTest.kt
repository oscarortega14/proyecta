package app.selah.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.ServerSocket
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UploadTest {
    private val t = java.util.GregorianCalendar(2026, 10, 8, 10, 30, 5).timeInMillis

    @Test
    fun cameraNamesBecomeReadable() {
        assertEquals("Anuncio 2026-11-08 10-30-05.jpg", RemoteServer.uploadName("image.jpg", t))
        assertEquals("Anuncio 2026-11-08 10-30-05.jpg", RemoteServer.uploadName("IMG_4821.JPG".lowercase(), t))
        assertEquals("Anuncio 2026-11-08 10-30-05.mp4", RemoteServer.uploadName("VID_20261108_103000.mp4", t))
        assertEquals("Retiro de jóvenes.png", RemoteServer.uploadName("Retiro de jóvenes.png", t))
        assertEquals("secreto.png", RemoteServer.uploadName("../../Windows/secreto.png", t)) // sin carpetas
        assertNull(RemoteServer.uploadName("virus.exe", t))
        assertNull(RemoteServer.uploadName("notas.txt", t))
    }

    @Test
    fun uploadNeedsThePinAndLandsInAnnouncements() = runBlocking {
        val dir = createTempDirectory("selah-subir").toFile()
        val library = Library(dir).also { it.reload() }
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteServer(Commands.create(library), library, port, MutableStateFlow("4821")).also { it.start() }
        val client = HttpClient(CIO)
        val png = ByteArray(5000) { it.toByte() }
        try {
            val url = "http://127.0.0.1:$port/anuncios?nombre=Retiro.png"
            assertEquals(HttpStatusCode.Unauthorized, client.post(url) { setBody(png) }.status)
            assertEquals(HttpStatusCode.Unauthorized, client.post(url) { header("X-Proyecta-Pin", "0000"); setBody(png) }.status)
            assertEquals(emptyList(), library.catalog.value.announcements)

            val ok = client.post(url) { header("X-Proyecta-Pin", "4821"); setBody(png) }
            assertEquals(HttpStatusCode.OK, ok.status, ok.bodyAsText())
            assertEquals(listOf("Retiro.png"), library.catalog.value.announcements)
            assertEquals(5000, File(library.announcementsDir, "Retiro.png").length())

            val bad = client.post("http://127.0.0.1:$port/anuncios?nombre=x.exe") { header("X-Proyecta-Pin", "4821"); setBody(png) }
            assertEquals(HttpStatusCode.BadRequest, bad.status)
            val empty = client.post("http://127.0.0.1:$port/anuncios?nombre=y.png") { header("X-Proyecta-Pin", "4821"); setBody(ByteArray(0)) }
            assertEquals(HttpStatusCode.BadRequest, empty.status)
            assertTrue(library.announcementsDir.listFiles()!!.none { it.name.startsWith("x") || it.name.startsWith("y") })
        } finally {
            client.close(); server.stop(); dir.deleteRecursively()
        }
    }
}
