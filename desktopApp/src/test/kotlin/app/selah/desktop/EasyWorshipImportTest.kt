package app.selah.desktop

import app.selah.shared.SongParser
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EasyWorshipImportTest {

    /** Letra como la guarda EasyWorship 6/7: un párrafo RTF por línea, uno vacío entre diapositivas. */
    private fun ewRtf(vararg paragraphs: String) = buildString {
        append("{\\rtf1\\ansi\\ansicpg1252\\deff0\\sdeasyworship2\n{\\fonttbl{\\f0 Tahoma;}}\n{\\colortbl ;\\red255\\green255\\blue255;}\n")
        for (p in paragraphs) {
            append("{\\pard\\sdlistbullet\\qc\\qdef\\sdewparatemplatestyle101{\\*\\sdasfactor 1}{\\*\\sdasbaseline 34}")
            append("\\plain\\sdewtemplatestyle101 ").append(p).append("\\par}\n")
        }
        append("}")
    }

    @Test
    fun rtfBecomesPlainText() {
        val rtf = ewRtf("Estrofa 1", "Cu\\'e1n grande es \\u201?l", "llaves \\{ y \\} y barra \\\\", "", "Coro", "Se\\'f1or")
        assertEquals("Estrofa 1\nCuán grande es Él\nllaves { y } y barra \\\n\nCoro\nSeñor", EasyWorshipImport.rtfToText(rtf))
        assertEquals("ya es texto", EasyWorshipImport.rtfToText("ya es texto"))
    }

    @Test
    fun sectionsAuthorAndCcliGoIntoTheSongFile() {
        val words = "Verse 1\nAmazing grace\nhow sweet the sound\n\nChorus\nMy chains are gone\n\nsin etiqueta\n\nBridge"
        val song = assertNotNull(EasyWorshipImport.toSelah("Sublime gracia", "John Newton", "4768151", words))
        assertEquals(
            "# Sublime gracia\nAutor: John Newton\nCCLI: 4768151\n\nEstrofa 1:\nAmazing grace\nhow sweet the sound\n\nCoro:\nMy chains are gone\n\nsin etiqueta\n",
            song.text,
        )
        val parsed = SongParser.parse("x", song.text)
        assertEquals("John Newton", parsed.author)
        assertEquals("4768151", parsed.ccli)
        assertEquals(listOf("Estrofa 1", "Coro", ""), parsed.slides.map { it.label })
        assertTrue(parsed.slides.none { "Autor" in it.text || "CCLI" in it.text })
        // Sin número CCLI válido no se escribe la línea; sin letra no hay canción.
        assertTrue("CCLI" !in EasyWorshipImport.toSelah("A", null, "0", "letra")!!.text)
        assertNull(EasyWorshipImport.toSelah("A", null, null, "  \n "))
        assertNull(EasyWorshipImport.toSelah(" ", null, null, "letra"))
    }

    /** Crea Songs.db y SongWords.db con las tablas de EasyWorship 6/7. */
    private fun fakeEasyWorship(dir: File) {
        dir.mkdirs()
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${File(dir, "Songs.db")}").use { c ->
            c.createStatement().use { st ->
                st.execute("CREATE TABLE song (rowid INTEGER PRIMARY KEY, song_uid TEXT, title TEXT, author TEXT, copyright TEXT, administrator TEXT, vendor_id INTEGER)")
                st.execute("INSERT INTO song VALUES (1, 'a', 'Cuán grande es Él', 'Carl Boberg', '© 1953', '', 14181)")
                st.execute("INSERT INTO song VALUES (2, 'b', 'Sin letra', '', '', '', NULL)")
                st.execute("INSERT INTO song VALUES (3, 'c', 'Santo', NULL, NULL, NULL, NULL)")
            }
        }
        DriverManager.getConnection("jdbc:sqlite:${File(dir, "SongWords.db")}").use { c ->
            c.createStatement().execute("CREATE TABLE word (rowid INTEGER PRIMARY KEY, song_id INTEGER, words TEXT, slide_uids TEXT)")
            c.prepareStatement("INSERT INTO word (song_id, words) VALUES (?, ?)").use { ps ->
                ps.setLong(1, 1); ps.setString(2, ewRtf("Verse 1", "Se\\'f1or mi Dios", "al contemplar los cielos", "", "Chorus", "Mi coraz\\'f3n entona la canci\\'f3n")); ps.executeUpdate()
                ps.setLong(1, 3); ps.setString(2, ewRtf("Santo, santo, santo")); ps.executeUpdate()
            }
        }
    }

    @Test
    fun importsFromEasyWorshipDatabases() {
        val tmp = kotlin.io.path.createTempDirectory("selah-ew").toFile()
        try {
            val data = File(tmp, "EasyWorship/Default/v6.1/Databases/Data")
            fakeEasyWorship(data)
            // Se encuentra solo en la carpeta de EasyWorship
            assertEquals(File(data, "Songs.db"), EasyWorshipImport.findInstalled(listOf(File(tmp, "EasyWorship"))))

            val songs = EasyWorshipImport.read(File(data, "Songs.db"))
            assertEquals(listOf("Cuán grande es Él", "Santo"), songs.map { it.title }) // la que no tiene letra no se trae
            val first = SongParser.parse("x", songs[0].text)
            assertEquals("Carl Boberg", first.author)
            assertEquals("14181", first.ccli)
            assertEquals("Señor mi Dios\nal contemplar los cielos", first.slides[0].text)
            assertEquals("Coro", first.slides[1].label)

            // Elegir SongWords.db también sirve; y por la biblioteca, igual que los demás formatos.
            assertEquals(2, EasyWorshipImport.read(File(data, "SongWords.db")).size)
            val library = Library(File(tmp, "Selah")).also { it.reload() }
            val result = library.importSongs(listOf(File(data, "Songs.db")))
            assertEquals(2, result.added)
            assertTrue(File(tmp, "Selah/canciones/Cuán grande es Él.txt").readText().contains("CCLI: 14181"))
            // EasyWorship no se toca (se leen copias)
            assertTrue(File(data, "Songs.db").isFile && File(data, "SongWords.db").isFile)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun importsAnEwsxBackup() {
        val tmp = kotlin.io.path.createTempDirectory("selah-ewsx").toFile()
        try {
            // Un respaldo: una sola base con las dos tablas, dentro de un .zip
            val db = File(tmp, "main.db")
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:$db").use { c ->
                c.createStatement().use { st ->
                    st.execute("CREATE TABLE song (rowid INTEGER PRIMARY KEY, title TEXT, author TEXT)")
                    st.execute("INSERT INTO song VALUES (7, 'Castillo fuerte', 'Lutero')")
                    st.execute("CREATE TABLE word (rowid INTEGER PRIMARY KEY, song_id INTEGER, words TEXT)")
                }
                c.prepareStatement("INSERT INTO word (song_id, words) VALUES (7, ?)").use { it.setString(1, ewRtf("Castillo fuerte es nuestro Dios")); it.executeUpdate() }
            }
            val ewsx = File(tmp, "respaldo.ewsx")
            java.util.zip.ZipOutputStream(ewsx.outputStream()).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("main.db")); db.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
            val songs = EasyWorshipImport.read(ewsx)
            assertEquals(listOf("Castillo fuerte"), songs.map { it.title })
            assertTrue(songs[0].text.contains("Autor: Lutero"))
        } finally {
            tmp.deleteRecursively()
        }
    }
}
