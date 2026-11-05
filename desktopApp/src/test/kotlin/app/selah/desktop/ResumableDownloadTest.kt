package app.selah.desktop

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResumableDownloadTest {
    private val data = Random(3).nextBytes(400_000)
    private val sha = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    @Test
    fun aCutConnectionResumesAndTheInstallerArrivesIntact() {
        val requests = AtomicInteger()
        val ranges = mutableListOf<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/p.msi") { ex ->
                val n = requests.incrementAndGet()
                val range = ex.requestHeaders.getFirst("Range"); synchronized(ranges) { ranges += range }
                val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
                val body = data.copyOfRange(from, data.size)
                ex.sendResponseHeaders(if (from > 0) 206 else 200, body.size.toLong())
                if (n == 1) { ex.responseBody.write(body, 0, 150_000); ex.responseBody.flush(); ex.close() } // se corta
                else { ex.responseBody.write(body); ex.close() }
            }
            start()
        }
        val dir = createTempDirectory("msi").toFile()
        try {
            val target = File(dir, "p.msi")
            val got = Updater.resumableDownload("http://127.0.0.1:${server.address.port}/p.msi", target, data.size.toLong(), sha, {}, pauseMs = 10)
            assertEquals(sha, got)
            assertTrue(target.readBytes().contentEquals(data))
            assertEquals(null, ranges.first())
            assertTrue(ranges.drop(1).all { it?.startsWith("bytes=") == true }, "siguió desde donde quedó: $ranges")
            // Ya entero: no se vuelve a bajar
            val before = requests.get()
            Updater.resumableDownload("http://127.0.0.1:${server.address.port}/p.msi", target, data.size.toLong(), sha, {}, pauseMs = 10)
            assertEquals(before, requests.get())
        } finally {
            server.stop(0); dir.deleteRecursively()
        }
    }

    @Test
    fun withoutConnectionTheMessageIsShortAndInSpanish() {
        val dir = createTempDirectory("msi2").toFile()
        val e = assertFailsWith<IllegalStateException> { Updater.resumableDownload("http://127.0.0.1:1/p.msi", File(dir, "x"), 10, null, {}, pauseMs = 1) }
        assertTrue(e.message!!.length < 120 && "http" !in e.message!!, e.message)
        dir.deleteRecursively()
    }
}
