package app.selah.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SermonTest {
    private val bible = Bible(
        "Prueba",
        Bible.BOOK_NAMES.map { name -> Bible.Book(name, List(12) { ch -> List(12) { v -> "$name ${ch + 1}:${v + 1}" } }) },
    )

    private val text = """
        # El buen pastor

        Introducción:
        Dios nos busca primero

        Biblia: Juan 10:11

        Punto 1:
        Él conoce a sus ovejas

        lectura: Salmos 1:1-2

        Biblia: Hechos 99:1
    """.trimIndent()

    @Test
    fun recognisesBibleBlocks() {
        assertEquals("Juan 10:11", Sermon.bibleReference("Biblia: Juan 10:11"))
        assertEquals("Salmos 23", Sermon.bibleReference("  lectura:Salmos 23 "))
        assertNull(Sermon.bibleReference("La Biblia: dice que…\nsegunda línea"))
        assertNull(Sermon.bibleReference("Él conoce a sus ovejas"))
    }

    @Test
    fun expandsCoverPointsAndVerses() {
        val e = Sermon.expand(SongParser.parse("x", text), bible)
        assertEquals(
            listOf("" to "El buen pastor", "Introducción" to "Dios nos busca primero", "Juan 10:11" to "Juan 10:11",
                "Punto 1" to "Él conoce a sus ovejas", "Salmos 1:1" to "Salmos 1:1", "Salmos 1:2" to "Salmos 1:2", "" to "Hechos 99:1"),
            e.slides.map { it.label to it.text },
        )
        // La cita que no existe se avisa (y queda escrita para no perder el lugar)
        assertEquals(1, e.problems.size)
        assertTrue(e.problems.single().contains("capítulo 99"))
    }

    @Test
    fun onlyPointsAreJumpSections() {
        val slides = Sermon.expand(SongParser.parse("x", text), bible).slides
        val state = PresentationState("El buen pastor", slides, 0, sermon = true)
        assertEquals(listOf("Inicio", "Introducción", "Punto 1"), state.sectionLabels)
    }
}
