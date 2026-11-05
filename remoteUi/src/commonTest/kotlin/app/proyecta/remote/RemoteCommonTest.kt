package app.proyecta.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteCommonTest {
    @Test
    fun suggestedNameIsNextSunday() {
        assertEquals("Culto domingo 8 nov 2026", suggestedListName(Triple(2026, 11, 4))) // miércoles
        assertEquals("Culto domingo 8 nov 2026", suggestedListName(Triple(2026, 11, 8))) // ya es domingo
        assertEquals("Culto domingo 3 ene 2027", suggestedListName(Triple(2026, 12, 29))) // cambia de año
        assertEquals("Culto domingo 1 mar 2026", suggestedListName(Triple(2026, 2, 26))) // fin de febrero
        assertEquals("Culto domingo 3 mar 2024", suggestedListName(Triple(2024, 2, 29))) // año bisiesto
    }

    @Test
    fun calendarMathRoundTrips() {
        assertEquals(0L, daysFromCivil(1970, 1, 1))
        for (d in listOf(-1000L, 0L, 59L, 365L, 11_000L, 20_763L, 30_000L)) {
            val (y, m, day) = civilFromDays(d)
            assertEquals(d, daysFromCivil(y, m, day))
        }
    }

    @Test
    fun connectLinks() {
        assertEquals(ConnectLink("192.168.1.20", null), parseConnectLink("proyecta://connect?host=192.168.1.20"))
        assertEquals(ConnectLink("192.168.1.20", "9550"), parseConnectLink("proyecta://connect?host=192.168.1.20&pin=9550"))
        assertEquals(ConnectLink("pc-iglesia.local", null), parseConnectLink("proyecta://connect?pin=abc&host=pc-iglesia.local"))
        assertNull(parseConnectLink("proyecta://connect?host=1.2.3.4%2Fmal"))
        assertNull(parseConnectLink("https://evil.example/connect?host=1.2.3.4"))
        assertNull(parseConnectLink("proyecta://otra?host=1.2.3.4"))
        assertNull(parseConnectLink("proyecta://connect"))
    }

    @Test
    fun storeKeepsPinOnlyForItsComputer() {
        val store = object : KeyValueStore {
            val map = mutableMapOf<String, String>()
            override fun get(key: String) = map[key]
            override fun put(vararg pairs: Pair<String, String>) { map.putAll(pairs) }
        }
        assertEquals("10.0.0.5", store.acceptLink(ConnectLink("10.0.0.5", "1234")))
        assertEquals(mapOf("pin" to "1234", "pinHost" to "10.0.0.5"), store.map)
        store.acceptLink(ConnectLink("10.0.0.9", null)) // sin clave: no borra la anterior
        assertEquals("1234", store.map["pin"])
    }
}
