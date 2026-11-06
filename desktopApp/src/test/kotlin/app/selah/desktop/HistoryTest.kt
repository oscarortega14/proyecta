package app.selah.desktop

import app.selah.shared.ClientMessage
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryTest {
    private lateinit var dir: File
    private var now = LocalDateTime.of(2026, 11, 8, 10, 30)

    @BeforeTest
    fun setUp() { dir = createTempDirectory("selah-hist").toFile() }

    @AfterTest
    fun tearDown() { dir.deleteRecursively() }

    private fun history() = History(File(dir, "historial.csv")) { now }

    @Test
    fun recordsOncePerDayAndSurvivesRestart() {
        val h = history()
        h.record(HistoryEntry.Kind.SONG, "Cuán grande es Él", "cuan")
        now = now.plusMinutes(40)
        h.record(HistoryEntry.Kind.SONG, "Cuán grande es Él", "cuan") // mismo día: no se repite
        h.record(HistoryEntry.Kind.BIBLE, "Juan 3:16", "Juan 3:16")
        now = LocalDateTime.of(2026, 11, 15, 10, 5)
        h.record(HistoryEntry.Kind.SONG, "Cuán grande es Él", "cuan")
        assertEquals(3, h.entries.value.size)

        val again = history()
        assertEquals(h.entries.value, again.entries.value)
        assertEquals("10:30", again.entries.value.first().time)
        assertEquals(mapOf("cuan" to LocalDate.of(2026, 11, 15)), again.lastSung())
    }

    @Test
    fun ccliReportCountsSongsInThePeriodWithTheirNumbers() {
        val h = history()
        h.record(HistoryEntry.Kind.SONG, "Cuán grande es Él", "cuan")
        h.record(HistoryEntry.Kind.SONG, "Borrada", "borrada")
        h.record(HistoryEntry.Kind.BIBLE, "Juan 3:16", "Juan 3:16") // las lecturas no van
        now = LocalDateTime.of(2026, 11, 15, 10, 0)
        h.record(HistoryEntry.Kind.SONG, "Cuán grande es Él", "cuan")
        now = LocalDateTime.of(2026, 12, 6, 10, 0)
        h.record(HistoryEntry.Kind.SONG, "Cuán grande es Él", "cuan") // fuera del periodo
        val songs = mapOf("cuan" to app.selah.shared.Song("cuan", "Cuán Grande Es Él", emptyList(), author = "Carl Boberg", ccli = "14181"))

        val rows = h.ccliReport(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)) { songs[it] }
        assertEquals(
            listOf(
                CcliRow("Cuán Grande Es Él", "Carl Boberg", "14181", 2, listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 15))),
                CcliRow("Borrada", "", "", 1, listOf(LocalDate.of(2026, 11, 8))),
            ),
            rows,
        )
        assertEquals(
            "titulo,autor,numero_ccli,veces,fechas\nCuán Grande Es Él,Carl Boberg,14181,2,2026-11-08 2026-11-15\nBorrada,,,1,2026-11-08\n",
            History.ccliCsv(rows),
        )
    }

    @Test
    fun topSongsAndCsvWithCommasAndQuotes() {
        val h = history()
        h.record(HistoryEntry.Kind.SONG, "A", "a")
        h.record(HistoryEntry.Kind.SONG, "Santo, santo, \"santo\"", "s")
        now = now.plusDays(7)
        h.record(HistoryEntry.Kind.SONG, "Santo, santo, \"santo\"", "s")
        assertEquals(listOf("Santo, santo, \"santo\"" to 2, "A" to 1), h.topSongs(LocalDate.of(2026, 1, 1)))
        assertEquals(listOf("Santo, santo, \"santo\"" to 1), h.topSongs(LocalDate.of(2026, 11, 10)))

        val csv = File(dir, "export.csv")
        h.exportCsv(csv)
        val lines = csv.readLines()
        assertEquals(History.HEADER, lines[0])
        assertEquals("2026-11-08,10:30,Canción,\"Santo, santo, \"\"santo\"\"\",s", lines[2])
        assertEquals(listOf("2026-11-08", "10:30", "Canción", "Santo, santo, \"santo\"", "s"), History.parseCsvLine(lines[2]))
        assertEquals(h.entries.value, history().entries.value) // y se vuelve a leer igual
    }

    @Test
    fun projectingSongsAndReadingsFillsTheHistory() {
        File(dir, "canciones").mkdirs()
        File(dir, "canciones/A.txt").writeText("# Alabanza\n\nletra\n")
        val library = Library(dir).also { it.reload() }
        val commands = Commands.create(library)
        commands.handle(ClientMessage.SelectSong("A"))
        commands.handle(ClientMessage.ShowBible("Juan 3:16"))
        commands.handle(ClientMessage.SelectSong("no-existe"))
        val e = commands.history!!.entries.value
        assertEquals(listOf("Alabanza", "Juan 3:16"), e.map { it.title })
        assertTrue(File(dir, "historial.csv").readText().contains("Alabanza"))
        // También lo que se proyecta desde la lista del culto
        commands.handle(ClientMessage.PlanAddBible("Salmos 23"))
        commands.handle(ClientMessage.PlanGo(0))
        assertEquals("Salmos 23", commands.history.entries.value.last().title)
    }

    @Test
    fun friendlyDates() {
        val today = LocalDate.of(2026, 11, 8)
        assertEquals("domingo 8 nov 2026", formatDay(today))
        assertEquals("hoy", formatAgo(today, today))
        assertEquals("ayer", formatAgo(today.minusDays(1), today))
        assertEquals("hace 3 días", formatAgo(today.minusDays(3), today))
        assertEquals("1 nov", formatAgo(LocalDate.of(2026, 11, 1), today))
        assertEquals("20 dic 2025", formatAgo(LocalDate.of(2025, 12, 20), today))
    }
}
