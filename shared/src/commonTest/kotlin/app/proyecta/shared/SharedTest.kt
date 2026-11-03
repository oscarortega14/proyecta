package app.proyecta.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongParserTest {
    @Test
    fun parsesTitleBlocksAndLabels() {
        val song = SongParser.parse(
            "x",
            "﻿# Mi canción\r\n\r\nLínea 1\r\nLínea 2\r\n\r\n\r\nCoro:\nA\nB\n\nSolo:\n",
        )
        assertEquals("Mi canción", song.title)
        assertEquals(3, song.slides.size)
        assertEquals(Slide("", "Línea 1\nLínea 2"), song.slides[0])
        assertEquals(Slide("Coro", "A\nB"), song.slides[1])
        // un bloque de una sola línea que termina en ":" es texto, no etiqueta
        assertEquals(Slide("", "Solo:"), song.slides[2])
    }

    @Test
    fun fallsBackToFileNameAsTitle() {
        assertEquals("archivo", SongParser.parse("archivo", "Hola\nmundo").title)
    }
}

class BibleTest {
    private val bible = Bible(
        "Prueba",
        Bible.BOOK_NAMES.map { name ->
            Bible.Book(name, List(5) { ch -> List(10) { v -> "$name ${ch + 1}:${v + 1}" } })
        },
    )

    @Test
    fun parsesReferences() {
        assertEquals(BibleReference("Juan", 3, 16, null), BibleReference.parse("Juan 3:16"))
        assertEquals(BibleReference("1 Corintios", 13, 4, 7), BibleReference.parse("1 Corintios 13:4-7"))
        assertEquals(BibleReference("sal", 23), BibleReference.parse("sal 23"))
        assertEquals(BibleReference("jn", 3, 16), BibleReference.parse("jn 3 16"))
        assertEquals(BibleReference("Gén", 1, 1), BibleReference.parse("Gén. 1.1"))
        assertNull(BibleReference.parse("Juan"))
        assertNull(BibleReference.parse(""))
    }

    @Test
    fun findsBooksWithAccentsAbbreviationsAndNumbers() {
        assertEquals("Juan", bible.books[bible.findBook("juan")!!].name)
        assertEquals("Juan", bible.books[bible.findBook("jn")!!].name)
        assertEquals("1 Juan", bible.books[bible.findBook("1 juan")!!].name)
        assertEquals("Génesis", bible.books[bible.findBook("genesis")!!].name)
        assertEquals("Salmos", bible.books[bible.findBook("Salmo")!!].name)
        assertEquals("1 Corintios", bible.books[bible.findBook("1co")!!].name)
        assertNull(bible.findBook("xyz"))
    }

    @Test
    fun looksUpVersesRangesAndChapters() {
        val one = bible.lookup("juan 3:4")
        assertEquals("Juan 3:4", one.title)
        assertEquals(listOf(Slide("Juan 3:4", "Juan 3:4")), one.slides)

        val range = bible.lookup("1 co 2:3-50")
        assertEquals("1 Corintios 2:3-10", range.title)
        assertEquals(8, range.slides.size)

        val chapter = bible.lookup("Salmos 5")
        assertEquals("Salmos 5", chapter.title)
        assertEquals(10, chapter.slides.size)
    }

    @Test
    fun reportsClearErrors() {
        assertTrue(assertFailsWith<IllegalArgumentException> { bible.lookup("juan 99") }.message!!.contains("capítulo"))
        assertTrue(assertFailsWith<IllegalArgumentException> { bible.lookup("juan 3:40") }.message!!.contains("versículo"))
        assertTrue(assertFailsWith<IllegalArgumentException> { bible.lookup("hola") }.message!!.contains("No entiendo"))
    }

    @Test
    fun loadsVplFormat() {
        val codes = List(66) { "B$it" }
        val text = buildString {
            append("﻿")
            for (c in codes) append("$c 1:1 EN el principio.\n$c 1:2 Segundo.\n$c 2:1 JEHOVÁ es mi pastor.\n")
            append("línea que no es versículo\n")
        }
        val b = Bible.fromVpl(text, "RV")
        assertEquals("RV", b.name)
        assertEquals(66, b.books.size)
        assertEquals("Génesis", b.books.first().name)
        assertEquals("Apocalipsis", b.books.last().name)
        assertEquals("En el principio.", b.lookup("gen 1:1").slides.single().text)
        assertEquals("JEHOVÁ es mi pastor.", b.lookup("ap 2:1").slides.single().text)
        assertEquals(2, b.lookup("juan 1").slides.size)

        assertFailsWith<IllegalArgumentException> { Bible.fromVpl("GEN 1:1 x\n", "x") }

        // versículo vacío en medio de un rango: se salta
        val gap = Bible("g", listOf(Bible.Book("Job", listOf(listOf("uno", "", "tres")))))
        assertEquals(listOf("uno", "tres"), gap.lookup("job 1:1-3").slides.map { it.text })
        assertFailsWith<IllegalArgumentException> { gap.lookup("job 1:2") }
    }

    @Test
    fun fixesDropCapsOnly() {
        assertEquals("En el principio", Bible.fixDropCap("EN el principio"))
        assertEquals("Dios es nuestro amparo", Bible.fixDropCap("DIOS es nuestro amparo"))
        assertEquals("JEHOVÁ es mi pastor", Bible.fixDropCap("JEHOVÁ es mi pastor"))
        assertEquals("Y dijo Dios", Bible.fixDropCap("Y dijo Dios"))
        assertEquals("Salmo de David. JEHOVÁ", Bible.fixDropCap("Salmo de David. JEHOVÁ"))
    }

    @Test
    fun loadsBothJsonFormats() {
        val a = Bible.fromJson("""{"name":"X","books":[{"name":"Génesis","chapters":[["En el principio"]]}]}""", "f")
        assertEquals("X", a.name)
        assertEquals("En el principio", a.lookup("gen 1:1").slides.single().text)

        val b = Bible.fromJson("﻿[{\"abbrev\":\"gn\",\"chapters\":[[\"uno\",\"dos\"]]}]", "Archivo")
        assertEquals("Archivo", b.name)
        assertEquals("Génesis", b.books.single().name)
    }
}

class SectionsTest {
    private val song = listOf(
        Slide("", "estrofa a"), Slide("", "estrofa b"),
        Slide("Coro", "coro a"), Slide("", "coro b"),
        Slide("Pregón 1", "p1"),
    )

    @Test
    fun groupsSlidesUnderTheirLabels() {
        assertEquals(listOf(Section("Inicio", 0), Section("Coro", 2), Section("Pregón 1", 4)), song.sections())
    }

    @Test
    fun currentSectionFollowsIndex() {
        val st = PresentationState("T", song, index = 3)
        assertEquals(1, st.currentSection)
        assertEquals(0, st.copy(index = 1).currentSection)
        assertEquals(2, st.copy(index = 4).currentSection)
    }

    @Test
    fun noSectionsWithoutLabelsOrForBible() {
        assertEquals(emptyList(), listOf(Slide("", "a"), Slide("", "b")).sections())
        assertEquals(emptyList(), PresentationState("Juan 3", listOf(Slide("Juan 3:1", "x")), 0, isBible = true).sections)
    }
}

class SongOrderTest {
    private val text = """
        # Alabad
        Orden: Inicio, Coro, Pregón 1, coro, Puente

        estrofa a
        estrofa b

        Coro:
        coro a

        coro b

        Pregón 1:
        p1
    """.trimIndent()

    @Test
    fun expandsTheOrderRepeatingSections() {
        val song = SongParser.parse("x", text)
        assertEquals(
            listOf("estrofa a\nestrofa b", "coro a", "coro b", "p1", "coro a", "coro b"),
            song.slides.map { it.text },
        )
        assertEquals(listOf("Inicio", "Coro", "", "Pregón 1", "Coro", ""), song.slides.map { it.label })
        assertEquals(listOf("Puente"), song.unknownOrder)
    }

    @Test
    fun withoutOrderNothingChanges() {
        val song = SongParser.parse("x", text.lines().filterNot { it.startsWith("Orden") }.joinToString("\n"))
        assertEquals(4, song.slides.size)
        assertEquals(emptyList(), song.unknownOrder)
    }

    @Test
    fun jumpsGoToTheNextOccurrence() {
        val st = PresentationState("A", SongParser.parse("x", text).slides, index = 0)
        assertEquals(listOf("Inicio", "Coro", "Pregón 1"), st.sectionLabels)
        assertEquals(1, st.jumpTarget("Coro"))
        assertEquals(4, st.copy(index = 2).jumpTarget("Coro"))  // en el primer coro -> el segundo
        assertEquals(1, st.copy(index = 5).jumpTarget("Coro"))  // en el último -> vuelve al primero
        assertEquals(null, st.jumpTarget("Puente"))
        assertEquals("coro b", st.copy(index = 1).next?.text)
    }
}

class ThemeAndBackgroundTest {
    @Test
    fun readsTheFondoLine() {
        val song = SongParser.parse("x", "# T\nFondo: montañas.jpg\nOrden: Coro, Coro\n\nCoro:\nla la\n")
        assertEquals("montañas.jpg", song.background)
        assertEquals(2, song.slides.size)
        assertNull(SongParser.parse("x", "# T\n\nletra\n\nFondo: no es la línea\n").background)
    }

    @Test
    fun sanitizesThemes() {
        val t = Theme(color = "rojo", color2 = "#12", image = " ", dim = 3f, textColor = "#ffffff", fontScale = 9f).sanitized()
        assertEquals(Theme(color = "#000000", color2 = null, image = null, dim = 0.85f, textColor = "#ffffff", fontScale = 1.4f), t)
        assertTrue(Theme.PRESETS.all { it.second == it.second.sanitized() })
    }
}

class ProtocolTest {
    @Test
    fun roundTripsPlanAndStageMessages() {
        val msgs = listOf(
            ClientMessage.PlanAddSong("a"), ClientMessage.PlanAddBible("Juan 3:16"), ClientMessage.PlanRemove(1),
            ClientMessage.PlanMove(0, 2), ClientMessage.PlanGo(1), ClientMessage.PlanNext, ClientMessage.PlanClear,
            ClientMessage.StageMessage("2 minutos"),
        )
        for (m in msgs) assertEquals(m, decodeClientMessage(m.encode()))
        val plan = ServerMessage.Plan(ServicePlanState(listOf(PlanItem(PlanKind.SONG, "a", "A")), 0))
        assertEquals(plan, decodeServerMessage(plan.encode()))
        assertTrue(plan.encode().contains("\"kind\":\"SONG\""))
    }

    @Test
    fun roundTripsMessages() {
        val msgs = listOf(
            ClientMessage.SelectSong("a"), ClientMessage.GoTo(3), ClientMessage.Next,
            ClientMessage.Prev, ClientMessage.SetBlank(true), ClientMessage.ShowBible("Juan 3:16"),
        )
        for (m in msgs) assertEquals(m, decodeClientMessage(m.encode()))

        val state = ServerMessage.State(PresentationState("T", listOf(Slide("Coro", "x")), 0, blank = false, isBible = false))
        assertEquals(state, decodeServerMessage(state.encode()))
        assertTrue(ClientMessage.Next.encode().contains("\"type\":\"next\""))
    }

    @Test
    fun searchIgnoresAccentsAndCase() {
        val songs = listOf(SongSummary("1", "Cuán grande es Él", "Señor mi Dios"), SongSummary("2", "Otra", "nada"))
        assertEquals(listOf("1"), songs.search("cuan GRANDE").map { it.id })
        assertEquals(listOf("1"), songs.search("senor").map { it.id })
        assertEquals(2, songs.search("  ").size)
    }
}
