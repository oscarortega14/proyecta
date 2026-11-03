package app.proyecta.desktop

import app.proyecta.shared.Bible
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifica una Biblia real en formato VPL. Se omite si no se indica el archivo:
 *   PROYECTA_VPL=/ruta/spaRV1909_vpl.txt ./gradlew :desktopApp:test
 */
class RealBibleTest {
    @Test
    fun loadsRealVplBible() {
        val path = System.getenv("PROYECTA_VPL") ?: return
        val bible = Bible.fromVpl(Library.readTextSmart(File(path)), "RV1909")

        assertEquals(66, bible.books.size)
        // 31102 versículos en la numeración estándar; la RV1909 deja 18 vacíos (los numera distinto).
        assertEquals(31084, bible.books.sumOf { b -> b.chapters.sumOf { ch -> ch.count { it.isNotBlank() } } })
        assertEquals(150, bible.books[bible.findBook("salmos")!!].chapters.size)

        val john = bible.lookup("Juan 3:16").slides.single().text
        assertTrue(john.startsWith("Porque de tal manera amó Dios al mundo"), john)
        assertTrue(bible.lookup("gen 1:1").slides.single().text.startsWith("En el principio"))
        assertEquals(4, bible.lookup("1 Co 13:4-7").slides.size)
        assertTrue(bible.lookup("Sal 23:1").slides.single().text.contains("JEHOVÁ es mi pastor"))
    }
}
