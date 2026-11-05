package app.selah.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DownloaderTest {
    private val data = Random(7).nextBytes(300_000)
    private val sha = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    /** Servidor que se porta como un Wi-Fi malo: la 1ª vez corta a la mitad, la 2ª se queda mudo, la 3ª anda. */
    private fun badWifiServer(requests: AtomicInteger, ranges: MutableList<String?>): ServerSocket {
        val server = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!server.isClosed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                Thread {
                    sock.use { s ->
                        val reader = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        val headers = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                        val range = headers.firstOrNull { it.startsWith("Range:", true) }?.substringAfter(':')?.trim()
                        synchronized(ranges) { ranges += range }
                        val n = requests.incrementAndGet()
                        val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
                        val body = data.copyOfRange(from, data.size)
                        val out = s.getOutputStream()
                        val status = if (from > 0) "206 Partial Content" else "200 OK"
                        val extra = if (from > 0) "Content-Range: bytes $from-${data.size - 1}/${data.size}\r\n" else ""
                        out.write("HTTP/1.1 $status\r\nContent-Length: ${body.size}\r\n${extra}Connection: close\r\n\r\n".toByteArray())
                        when (n) {
                            1 -> { out.write(body, 0, 100_000); out.flush() } // se corta
                            2 -> { out.write(body, 0, 50_000); out.flush(); Thread.sleep(3_000) } // se queda mudo
                            else -> { out.write(body); out.flush() }
                        }
                    }
                }.start()
            }
        }.apply { isDaemon = true }.start()
        return server
    }

    private fun client(socketMs: Long = 1_000) = HttpClient(OkHttp) { install(HttpTimeout) { socketTimeoutMillis = socketMs; connectTimeoutMillis = 2_000 } }

    @Test
    fun resumesWhereItStoppedAndEndsIntact(): Unit = runBlocking {
        val requests = AtomicInteger(); val ranges = mutableListOf<String?>()
        val server = badWifiServer(requests, ranges)
        val dir = createTempDirectory("descarga").toFile()
        try {
            val target = File(dir, "app.apk")
            var lastProgress = 0f
            Downloader(client(), "prueba", pauseMs = 10).download("http://127.0.0.1:${server.localPort}/app.apk", target, data.size.toLong(), sha) { lastProgress = it }
            assertTrue(target.readBytes().contentEquals(data), "contenido")
            assertEquals(1f, lastProgress)
            assertTrue(requests.get() >= 3, "pedidos=${requests.get()} rangos=$ranges")
            assertEquals(null, ranges[0]) // la primera, entera
            // Al menos un reintento siguió desde donde quedó (si la conexión se corta de golpe, lo que
            // estaba en tránsito puede perderse y ese intento empieza de cero; si se queda muda, se continúa).
            assertTrue(ranges.drop(1).any { it != null && it.startsWith("bytes=") && it != "bytes=0-" }, "rangos=$ranges")
            // Ya entera y verificada: no se vuelve a bajar
            val before = requests.get()
            Downloader(client(), "prueba", pauseMs = 10).download("http://127.0.0.1:${server.localPort}/app.apk", target, data.size.toLong(), sha) {}
            assertEquals(before, requests.get())
        } finally {
            server.close(); dir.deleteRecursively()
        }
    }

    @Test
    fun withoutInternetGivesAShortSpanishMessage(): Unit = runBlocking {
        val dir = createTempDirectory("descarga2").toFile()
        val e = assertFailsWith<DownloadException> {
            Downloader(client(), "prueba", pauseMs = 1).download("http://127.0.0.1:1/app.apk", File(dir, "x.apk"), 10, null) {}
        }
        assertTrue(e.message!!.length < 120 && "http" !in e.message!!, e.message)
        dir.deleteRecursively()
    }
}
