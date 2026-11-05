package app.selah.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ChordsTest {
    @Test
    fun chordsAreKeptForMusiciansButNotProjected() {
        val song = SongParser.parse(
            "x",
            """
            # Cuán grande es Él

            Intro:
            [G] [C] [D]

            [G]Señor mi Dios, al con[C]templar los cielos
            el firma[D]mento y las es[G]trellas mil

            Coro:
            Mi cora[G]zón entona la can[C]ción
            [coro x2]
            """.trimIndent(),
        )
        val (intro, verse, chorus) = song.slides
        assertEquals("", intro.text) // solo acordes: no se proyecta letra
        assertEquals("[G] [C] [D]", intro.chords)
        assertEquals("Señor mi Dios, al contemplar los cielos\nel firmamento y las estrellas mil", verse.text)
        assertEquals("[G]Señor mi Dios, al con[C]templar los cielos\nel firma[D]mento y las es[G]trellas mil", verse.chords)
        // "[coro x2]" no es un acorde (no empieza con una nota): queda en la letra
        assertEquals("Mi corazón entona la canción\n[coro x2]", chorus.text)
        assertEquals("Coro", chorus.label)
    }

    @Test
    fun songsWithoutChordsDontCarryThem() {
        val s = SongParser.parse("x", "# A\n\nhola [mundo]\n")
        assertEquals("hola [mundo]", s.slides.single().text)
        assertEquals("", s.slides.single().chords)
    }

    @Test
    fun chordVariants() {
        assertEquals("Te alabaré", SongParser.stripChords("[F#m7]Te [Bb/D]ala[Csus4]baré"))
        assertEquals("Te alabaré", SongParser.stripChords("[Am]Te  [G]alabaré"))
    }
}
