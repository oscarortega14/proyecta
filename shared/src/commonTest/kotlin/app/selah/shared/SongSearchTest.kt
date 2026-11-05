package app.selah.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongSearchTest {
    private val songs = listOf(
        SongParser.parse("david", "# Como hizo David\n\nComo hizo David delante de Dios\n\nCoro:\nMe gozaré en tu presencia\ncantaré con alegría\n"),
        SongParser.parse("gozo", "# Gozo en el Señor\n\nHay gozo en mi corazón\n"),
        SongParser.parse("cuan", "# Cuán grande es Él\n\nSeñor mi Dios al contemplar los cielos\n"),
    )

    @Test
    fun findsByAnyPhraseOfTheLyricsWithTheLine() {
        val hits = searchSongsFull(songs, "me gozare")
        assertEquals(listOf("david"), hits.map { it.id })
        assertEquals("Me gozaré en tu presencia", hits.single().snippet)
    }

    @Test
    fun titleMatchesComeFirst() {
        val hits = searchSongsFull(songs, "gozo")
        assertEquals("gozo", hits.first().id)
        assertEquals("", hits.first().snippet) // por título: sin fragmento
    }

    @Test
    fun allWordsMustAppearAndAccentsDontMatter() {
        assertEquals(listOf("cuan"), searchSongsFull(songs, "CIELOS señor").map { it.id })
        assertTrue(searchSongsFull(songs, "cielos alegria").isEmpty())
        assertEquals(3, searchSongsFull(songs, "  ").size) // sin texto: todas
    }
}
