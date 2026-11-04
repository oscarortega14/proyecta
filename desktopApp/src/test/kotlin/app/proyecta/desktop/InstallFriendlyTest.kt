package app.proyecta.desktop

import app.proyecta.shared.DISCOVERY_REQUEST
import app.proyecta.shared.DiscoveryReply
import app.proyecta.shared.ProtocolJson
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Todo lo que evita pasos manuales al instalar en un PC nuevo. */
class InstallFriendlyTest {
    @Test
    fun bundledBibleLoadsWhenTheFolderIsEmpty() {
        val dir = createTempDirectory("p-bib").toFile()
        val lib = Library(dir).also { it.reload() }
        assertEquals("Reina-Valera 1909", lib.catalog.value.bibleName)
        assertEquals(null, lib.catalog.value.bibleError)
        assertTrue(lib.bible!!.lookup("Juan 3:16").slides.single().text.startsWith("Porque de tal manera"))
        dir.deleteRecursively()
    }

    @Test
    fun discoveryAnswersOverUdp() {
        val responder = DiscoveryResponder(port = 0, wsPort = 9999).also { it.start() }
        val port = responder.boundPort
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 3000
                val req = DISCOVERY_REQUEST.toByteArray()
                s.send(DatagramPacket(req, req.size, InetAddress.getLoopbackAddress(), port))
                val buf = ByteArray(512)
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                val reply = ProtocolJson.decodeFromString(DiscoveryReply.serializer(), String(p.data, 0, p.length))
                assertEquals(9999, reply.port)
                assertEquals(responder.name, reply.name)
            }
        } finally {
            responder.stop()
        }
    }

    @Test
    fun importsLooseFilesAndZipsAndExports() {
        val dir = createTempDirectory("p-imp").toFile()
        val lib = Library(dir).also { it.reload() }
        File(lib.songsDir, "Canción de ejemplo.txt").delete()
        File(lib.songsDir, "Existe.txt").writeText("# Existe\n\nuno\n")
        lib.reload()

        val src = createTempDirectory("p-src").toFile()
        val loose = File(src, "Suelta.txt").apply { writeBytes("# Suelta\n\nÁnimo\n".toByteArray(charset("windows-1252"))) }
        val zip = File(src, "paquete.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            fun add(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
            add("canciones/Existe.txt", "# Existe\n\nuno\n")          // idéntica: se omite
            add("otra/Existe.txt", "# Existe\n\nOTRA LETRA\n")        // distinta: se guarda como "Existe (2)"
            add("canciones/Nueva.txt", "# Nueva\n\nhola\n")
            add("__MACOSX/._Nueva.txt", "basura")
            add("leeme.md", "no es canción")
        }
        val r = lib.importSongs(listOf(loose, zip))
        assertEquals(ImportResult(added = 3, skipped = 1, renamed = 1), r)
        assertEquals(setOf("Existe", "Existe (2)", "Suelta", "Nueva"), lib.catalog.value.songs.map { it.id }.toSet())
        assertEquals("Ánimo", lib.song("Suelta")!!.slides.single().text) // ANSI convertido bien

        val out = File(src, "respaldo.zip")
        assertEquals(4, lib.exportSongs(out))
        ZipFile(out).use { z -> assertEquals(4, z.entries().toList().count { it.name.startsWith("canciones/") }) }
        dir.deleteRecursively(); src.deleteRecursively()
    }

    @Test
    fun firewallCommandsAllowOnlyTheLocalNetworkAndClearBlocks() {
        val cmds = FirewallSetup.commands("C:\\Program Files\\Proyecta\\Proyecta.exe")
        assertTrue(cmds.any { "delete rule name=all dir=in program=\"C:\\Program Files\\Proyecta\\Proyecta.exe\"" in it })
        val adds = cmds.filter { " add rule " in it }
        assertEquals(2, adds.size)
        assertTrue(adds.all { "remoteip=localsubnet" in it && "action=allow" in it && "dir=in" in it })
        assertTrue(adds.any { "protocol=TCP localport=8765" in it } && adds.any { "protocol=UDP localport=8766" in it })
    }
}
