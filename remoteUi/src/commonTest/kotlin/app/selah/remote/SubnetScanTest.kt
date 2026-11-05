package app.selah.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubnetScanTest {
    @Test
    fun hostsAreThe254AddressesClosestFirst() {
        val hosts = SubnetScan.hosts("192.168.40.4")
        assertEquals(254, hosts.size)
        assertEquals("192.168.40.4", hosts.first()) // la propia también (simulador: es la Mac con Selah)
        assertEquals(listOf("192.168.40.3", "192.168.40.5"), hosts.drop(1).take(2).sorted())
        assertTrue("192.168.40.1" in hosts && "192.168.40.254" in hosts && "192.168.40.0" !in hosts && "192.168.40.255" !in hosts)
        assertEquals(254, SubnetScan.hosts("10.0.0.20").size)
        assertEquals(254, SubnetScan.hosts("172.20.1.9").size)
    }

    @Test
    fun noScanOutsideAPrivateNetwork() {
        assertEquals(emptyList(), SubnetScan.hosts("8.8.8.8"))       // datos móviles / pública
        assertEquals(emptyList(), SubnetScan.hosts("172.40.0.1"))    // fuera de 172.16–31
        assertEquals(emptyList(), SubnetScan.hosts("no es una ip"))
        assertEquals(emptyList(), SubnetScan.hosts("192.168.1"))
    }

    @Test
    fun scanKeepsOnlyTheAddressesThatAnswer() = runTest {
        val pcs = mapOf("192.168.40.9" to "PC-IGLESIA", "192.168.40.77" to "SONIDO")
        val found = SubnetScan.scan(SubnetScan.hosts("192.168.40.4")) { host ->
            delay(10)
            if (host == "192.168.40.200") error("se cayó la conexión") // un error no detiene la búsqueda
            pcs[host]
        }
        assertEquals(setOf(FoundServer("PC-IGLESIA", "192.168.40.9"), FoundServer("SONIDO", "192.168.40.77")), found.toSet())
    }
}

class UploadFileNameTest {
    @Test
    fun keepsTheNameAndUsesTheRealExtension() {
        assertEquals("IMG_1234.jpg", uploadFileName("IMG_1234.HEIC", "jpg"))   // foto HEIC convertida
        assertEquals("Retiro jóvenes.mov", uploadFileName("Retiro jóvenes.MOV", "MOV"))
        assertEquals("anuncio.jpg", uploadFileName(null, "jpg"))
        assertEquals("anuncio.mp4", uploadFileName("  ", ".mp4"))
        assertEquals("foto.png", uploadFileName("../../carpeta/foto.png", "png"))
        // El iPhone a veces solo da un código: se manda como genérico (el PC le pone "Anuncio <fecha>")
        assertEquals("foto.jpg", uploadFileName("610B91B5-1580-4A1F-8DB9-530B76F78203", "jpg", "foto"))
        assertEquals("video.mov", uploadFileName("f2c3fab1-dd3a-4564-b1f2-124ab554ef62.MOV", "mov", "video"))
    }
}
