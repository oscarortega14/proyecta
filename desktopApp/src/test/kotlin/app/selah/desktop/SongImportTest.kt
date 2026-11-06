package app.selah.desktop

import app.selah.shared.SongParser
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongImportTest {

    // Como lo exporta OpenLP (OpenLyrics 0.8): estrofas, coro, orden y saltos de línea con <br/>
    private val openLp = """
        <?xml version='1.0' encoding='UTF-8'?>
        <song xmlns="http://openlyrics.info/namespace/2009/song" version="0.8" createdIn="OpenLP 2.4.6">
          <properties>
            <titles><title>Cuán Grande Es Él</title></titles>
            <authors><author>Carl Boberg</author></authors>
            <ccliNo>14181</ccliNo>
            <verseOrder>v1 c1 v2 c1</verseOrder>
          </properties>
          <lyrics>
            <verse name="v1"><lines>Señor mi Dios, al contemplar los cielos<br/>El firmamento y las estrellas mil</lines></verse>
            <verse name="c1"><lines>Mi corazón entona la canción<br/>Cuán grande es Él</lines><lines>Cuán grande es Él<br/>Cuán grande es Él</lines></verse>
            <verse name="v2"><lines>Al recorrer los montes y los valles</lines></verse>
          </lyrics>
        </song>
    """.trimIndent()

    @Test
    fun openLyricsBecomesASongWithSectionsAndOrder() {
        val imported = assertNotNull(SongImport.fromOpenLyrics(openLp))
        assertEquals("Cuán Grande Es Él", imported.title)
        val song = SongParser.parse("x", imported.text)
        assertEquals("Cuán Grande Es Él", song.title)
        assertEquals("Carl Boberg", song.author)
        assertEquals("14181", song.ccli)
        assertEquals(emptyList(), song.unknownOrder)
        // Orden v1 c1 v2 c1: el coro (2 diapositivas) se repite
        assertEquals(
            listOf("Verso 1", "Coro 1", "", "Verso 2", "Coro 1", ""),
            song.slides.map { it.label },
        )
        assertEquals("Señor mi Dios, al contemplar los cielos\nEl firmamento y las estrellas mil", song.slides[0].text)
        assertEquals("Cuán grande es Él\nCuán grande es Él", song.slides[2].text)
    }

    @Test
    fun openLyricsIgnoresChordsCommentsAndOtherLanguages() {
        val xml = """
            <song xmlns="http://openlyrics.info/namespace/2009/song" version="0.9">
              <properties><titles><title>Alabaré</title></titles></properties>
              <lyrics>
                <verse name="c" lang="es"><lines><comment>suave</comment><chord name="D"/>Alabaré, <chord name="G">a</chord>labaré<br/>
                   alabaré a mi Señor</lines></verse>
                <verse name="c" lang="en"><lines>I will praise</lines></verse>
              </lyrics>
            </song>
        """.trimIndent()
        val song = SongParser.parse("x", SongImport.fromOpenLyrics(xml)!!.text)
        assertEquals(1, song.slides.size)
        assertEquals("Coro", song.slides[0].label)
        assertEquals("Alabaré, alabaré\nalabaré a mi Señor", song.slides[0].text)
        assertEquals("[D]Alabaré, [G]alabaré\nalabaré a mi Señor", song.slides[0].chords) // los acordes quedan para los músicos
    }

    @Test
    fun badOrHostileXmlIsRejected() {
        assertNull(SongImport.fromOpenLyrics("no es xml"))
        assertNull(SongImport.fromOpenLyrics("<html><title>x</title></html>"))
        // Entidades externas (XXE): no se leen archivos del PC
        val xxe = """<?xml version="1.0"?><!DOCTYPE song [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <song><properties><titles><title>&x;</title></titles></properties><lyrics><verse name="v1"><lines>a</lines></verse></lyrics></song>"""
        assertNull(SongImport.fromOpenLyrics(xxe))
    }

    @Test
    fun verseNamesInSpanish() {
        assertEquals("Verso 1", SongImport.label("v1"))
        assertEquals("Coro", SongImport.label("c"))
        assertEquals("Puente", SongImport.label("b"))
        assertEquals("Pre-coro 2", SongImport.label("p2"))
        assertEquals("Verso 1 a", SongImport.label("v1a"))
    }

    // Formato "Holyrics File JSON" (Músicas → Importar/Exportar → Holyrics File JSON)
    private val holyrics = """
        [
          {"title": "HN 1 - BENDIZER A TRINDADE – Seu plano", "note": "8.5.8.3",
           "lyrics": {"paragraphs": [{"text": "1 Ao Pai santo seja a glória\nE ao Filho Seu;"}, {"text": "2 Contemplando o céu, a terra\nQue Deus planejou,"}]}},
          {"title": "Renuévame", "artist": "Marcos Witt",
           "lyrics": {"paragraphs": [{"text": "Renuévame Señor Jesús\nYa no quiero ser igual", "description": "Coro"}, {"text": "  "}]}},
          {"title": "", "lyrics": {"paragraphs": [{"text": "sin título"}]}}
        ]
    """.trimIndent()

    @Test
    fun holyricsJsonBecomesSongs() {
        val songs = SongImport.fromHolyricsJson(holyrics)
        assertEquals(listOf("HN 1 - BENDIZER A TRINDADE – Seu plano", "Renuévame"), songs.map { it.title })
        val first = SongParser.parse("x", songs[0].text)
        assertEquals(2, first.slides.size)
        assertEquals("1 Ao Pai santo seja a glória\nE ao Filho Seu;", first.slides[0].text)
        val second = SongParser.parse("x", songs[1].text)
        assertEquals(listOf("Coro"), second.slides.map { it.label }) // el párrafo vacío no queda
        assertTrue(SongImport.fromHolyricsJson("{roto").isEmpty())
    }

    @Test
    fun libraryImportsXmlJsonAndZips() {
        val dir = createTempDirectory("selah-import").toFile()
        val library = Library(dir).also { it.reload() }
        val src = File(dir, "src").apply { mkdirs() }
        val xml = File(src, "cuan grande.xml").apply { writeText(openLp) }
        val json = File(src, "Holyrics.json").apply { writeText(holyrics) }
        val zip = File(src, "openlp.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("Songs/otra.xml"))
            z.write(openLp.replace("Cuán Grande Es Él", "Otra canción").toByteArray())
            z.closeEntry()
        }
        val r = library.importSongs(listOf(xml, json, zip))
        assertEquals(4, r.added)
        val titles = library.catalog.value.songs.map { it.title }
        assertTrue(titles.containsAll(listOf("Cuán Grande Es Él", "Renuévame", "Otra canción", "HN 1 - BENDIZER A TRINDADE – Seu plano")))
        // Importar lo mismo otra vez no duplica
        assertEquals(0, library.importSongs(listOf(xml, json)).added)
        dir.deleteRecursively()
    }

    /** Con un archivo real de Holyrics:  SELAH_HOLYRICS=/ruta/Holyrics.json */
    @Test
    fun realHolyricsFile() {
        val path = System.getenv("SELAH_HOLYRICS") ?: return
        val songs = SongImport.fromHolyricsJson(File(path).readText())
        println("Holyrics: ${songs.size} canciones")
        assertTrue(songs.size > 100)
        assertTrue(songs.all { SongParser.parse("x", it.text).slides.isNotEmpty() })
    }
}
