package app.selah.desktop

import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File
import java.net.ServerSocket
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PixabayTest {
    private fun hit(id: Int, base: String) = """
        {"id":$id,"pageURL":"https://pixabay.com/videos/x-$id/","tags":"partículas, luz, fondo","duration":20,"user":"ana",
         "videos":{
           "large":{"url":"$base/v/$id-large.mp4","width":3840,"height":2160,"size":34000000,"thumbnail":"$base/t/$id-l.jpg"},
           "medium":{"url":"$base/v/$id-medium.mp4","width":2560,"height":1440,"size":15000000,"thumbnail":"$base/t/$id-m.jpg"},
           "small":{"url":"$base/v/$id-small.mp4","width":1920,"height":1080,"size":9000000,"thumbnail":"$base/t/$id-s.jpg"},
           "tiny":{"url":"$base/v/$id-tiny.mp4","width":1280,"height":720,"size":4700000,"thumbnail":"$base/t/$id-t.jpg"}}}
    """.trimIndent()

    @Test
    fun parsesAndPicksTheLightest1280Rendition() {
        val page = PixabayClient.parse("""{"total":2,"totalHits":2,"hits":[${hit(7, "http://x")}]}""")
        val v = page.videos.single()
        assertEquals(1280, v.width)
        assertTrue(v.downloadUrl.endsWith("7-tiny.mp4"))
        assertTrue(v.thumbnail.endsWith("7-m.jpg"))
        assertEquals("Pixabay 7 - partículas.mp4", v.fileName)

        // si ninguna llega a 1280, se toma la más grande
        val small = PixabayClient.parse("""{"totalHits":1,"hits":[{"id":1,"tags":"a/b:c","duration":5,"videos":{
            "tiny":{"url":"http://x/a.mp4","width":640,"height":360,"size":1},"small":{"url":"http://x/b.mp4","width":960,"height":540,"size":2}}}]}""")
        assertEquals(960, small.videos.single().width)
        assertEquals("Pixabay 1 - a-b-c.mp4", small.videos.single().fileName)
    }

    @Test
    fun searchesCachesAndDownloads() {
        val port = ServerSocket(0).use { it.localPort }
        val base = "http://127.0.0.1:$port"
        var searches = 0
        var lastQuery = ""
        val video = ByteArray(300_000) { (it % 251).toByte() }
        val server = embeddedServer(CIO, port = port) {
            routing {
                get("/api/videos/") {
                    searches++
                    lastQuery = call.request.queryParameters.entries().joinToString("&") { "${it.key}=${it.value.first()}" }
                    if (call.request.queryParameters["q"] == "limite") call.respondText("", status = HttpStatusCode.TooManyRequests)
                    else call.respondText("""{"totalHits":1,"hits":[${hit(42, base)}]}""")
                }
                get("/v/{name}") { call.respondBytes(video) }
            }
        }.start(wait = false)
        try {
            val client = PixabayClient(key = "CLAVE-SECRETA", baseUrl = "$base/api/videos/")
            val page = client.search("partículas doradas")
            assertEquals(1, page.videos.size)
            assertTrue("lang=es" in lastQuery && "category=backgrounds" in lastQuery && "safesearch=true" in lastQuery)
            assertTrue("q=partículas doradas" in lastQuery)
            client.search("partículas doradas")
            assertEquals(1, searches, "la segunda búsqueda igual debe salir de la caché")
            client.search("partículas doradas", onlyBackgrounds = false)
            assertFalse("category" in lastQuery)

            assertTrue("espera un minuto" in assertFailsWith<IllegalStateException> { client.search("limite") }.message!!)

            val dir = createTempDirectory("pix").toFile()
            val target = File(dir, page.videos.single().fileName)
            val seen = mutableListOf<Float>()
            client.download(page.videos.single(), target) { seen += it }
            assertEquals(video.size.toLong(), target.length())
            assertEquals(1f, seen.last())
            assertFalse(File(dir, target.name + ".part").exists())
            dir.deleteRecursively()
        } finally {
            server.stop(100, 500)
        }
        assertFailsWith<IllegalArgumentException> { PixabayClient(key = "").search("x") }
    }

    /** Contra el Pixabay real (usa la clave del build):  SELAH_PIXABAY_LIVE=1 ./gradlew :desktopApp:test --tests '*PixabayTest*' */
    @Test
    fun liveSearch() {
        if (System.getenv("SELAH_PIXABAY_LIVE") == null) return
        val client = PixabayClient()
        assertTrue(client.configured, "falta la clave en selah-build.properties")
        val page = client.search("partículas")
        println("LIVE: ${page.total} resultados; primero: ${page.videos.firstOrNull()?.let { "${it.fileName} ${it.width}x${it.height} ${it.sizeBytes / 1024} KB" }}")
        assertTrue(page.videos.isNotEmpty())
        assertTrue(page.videos.all { it.width >= 1280 || it.width > 0 })
    }
}
