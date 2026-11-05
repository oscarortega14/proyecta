package app.selah.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Búsqueda de Selah sin broadcast: se le pregunta a cada dirección de la red Wi-Fi (GET /info).
 * La usa el iPhone, que no puede enviar broadcast sin un permiso especial de Apple.
 */
object SubnetScan {
    /**
     * Las direcciones de la misma red que [ownIp] (las 254 de una red de casa o iglesia, x.x.x.1–254),
     * empezando por las más cercanas: el PC suele estar cerca del celular en la numeración.
     * Incluye la propia: en el simulador de iPhone, la IP "propia" es la de la Mac donde corre Selah.
     * Lista vacía si [ownIp] no es una IPv4 privada (datos móviles, sin Wi-Fi…).
     */
    fun hosts(ownIp: String): List<String> {
        val parts = ownIp.trim().split('.').map { it.toIntOrNull() ?: return emptyList() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return emptyList()
        val (a, b) = parts
        val private = a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
        if (!private) return emptyList()
        val own = parts[3]
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}."
        return (1..254).sortedBy { kotlin.math.abs(it - own) }.map { prefix + it }
    }

    /**
     * Pregunta a todas las [hosts] ([parallel] a la vez) y devuelve las que respondieron con un nombre.
     * [probe] devuelve el nombre del PC, o null si ahí no hay Selah (o no contestó a tiempo).
     */
    suspend fun scan(hosts: List<String>, parallel: Int = 48, probe: suspend (String) -> String?): List<FoundServer> = coroutineScope {
        val gate = Semaphore(parallel)
        hosts.map { host -> async { gate.withPermit { runCatching { probe(host) }.getOrNull()?.let { FoundServer(it, host) } } } }
            .mapNotNull { it.await() }
    }
}
