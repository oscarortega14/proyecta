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
