package app.proyecta.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdaterTest {
    // Recorte de lo que responde https://api.github.com/repos/…/releases/latest
    private val github = """
        {"tag_name": "v1.10.0", "name": "Proyecta 1.10.0", "draft": false, "prerelease": false,
         "body": "- Elegir la pantalla\n- Historial",
         "assets": [
           {"name": "ProyectaRemoto.apk", "browser_download_url": "https://github.com/x/releases/download/v1.10.0/ProyectaRemoto.apk", "size": 7882541},
           {"name": "Proyecta-1.10.0.msi", "browser_download_url": "https://github.com/x/releases/download/v1.10.0/Proyecta-1.10.0.msi",
            "size": 126481045, "digest": "sha256:ABCDEF0123"}
         ]}
    """.trimIndent()

    @Test
    fun readsTheReleaseAndPicksTheMsi() {
        val r = Updater.parseRelease(github)!!
        assertEquals("1.10.0", r.version)
        assertTrue(r.msiUrl.endsWith("Proyecta-1.10.0.msi"))
        assertEquals("abcdef0123", r.sha256)
        assertEquals(126481045, r.size)
        assertTrue(r.notes.contains("Historial"))
    }

    @Test
    fun ignoresDraftsReleasesWithoutMsiAndJunk() {
        assertNull(Updater.parseRelease(github.replace("\"prerelease\": false", "\"prerelease\": true")))
        assertNull(Updater.parseRelease("""{"tag_name": "v2.0.0", "assets": [{"name": "a.apk", "browser_download_url": "https://x/a.apk"}]}"""))
        assertNull(Updater.parseRelease("""{"message": "Not Found"}"""))
        assertNull(Updater.parseRelease("no es json"))
        // Solo https
        assertNull(Updater.parseRelease("""{"tag_name": "2.0", "assets": [{"name": "a.msi", "browser_download_url": "http://x/a.msi"}]}"""))
    }

    @Test
    fun comparesVersionsLikeNumbers() {
        assertTrue(Updater.isNewer("1.10.0", "1.9.0"))
        assertTrue(Updater.isNewer("v2.0", "1.99.99"))
        assertTrue(Updater.isNewer("1.9.1", "1.9"))
        assertFalse(Updater.isNewer("1.9.0", "1.9.0"))
        assertFalse(Updater.isNewer("1.8.5", "1.9.0"))
        assertFalse(Updater.isNewer("1.9.0-beta", "1.9.0"))
    }

    @Test
    fun offersOnlyNewerAndNotSkipped() {
        val r = Updater.parseRelease(github)
        assertTrue(Updater.shouldOffer(r, "1.9.0", null))
        assertFalse(Updater.shouldOffer(r, "1.10.0", null))
        assertFalse(Updater.shouldOffer(r, "1.9.0", "1.10.0")) // "No avisar de esta versión"
        assertFalse(Updater.shouldOffer(null, "1.9.0", null)) // sin internet o sin versiones publicadas
    }

    @Test
    fun knowsItsOwnVersion() {
        assertNotEquals("0", Updater.currentVersion)
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(Updater.currentVersion))
    }
}
