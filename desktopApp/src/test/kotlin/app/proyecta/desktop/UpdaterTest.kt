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

class SignatureTest {
    private fun keyPair() = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private fun b64(b: ByteArray) = java.util.Base64.getEncoder().encodeToString(b)

    @Test
    fun acceptsOnlyTheOfficialSignatureForThatExactInstaller() {
        val kp = keyPair()
        val pub = b64(kp.public.encoded)
        val sha = "ab".repeat(32)
        val sig = java.security.Signature.getInstance("Ed25519").run { initSign(kp.private); update(Updater.signedMessage(sha)); b64(sign()) }
        kotlin.test.assertTrue(Updater.verifySignature(sha, sig, pub))
        kotlin.test.assertFalse(Updater.verifySignature("cd".repeat(32), sig, pub)) // otro instalador
        kotlin.test.assertFalse(Updater.verifySignature(sha, sig, b64(keyPair().public.encoded))) // otra llave
        kotlin.test.assertFalse(Updater.verifySignature(sha, "basura", pub))
        kotlin.test.assertFalse(Updater.verifySignature(sha, sig)) // la llave oficial no firmó esto
    }

    @Test
    fun recognisesTheMicrosoftStoreInstallFolder() {
        kotlin.test.assertTrue(Updater.isStorePath("""C:\Program Files\WindowsApps\Proyecta_2.4.0.0_x64__abc123\Proyecta.exe"""))
        kotlin.test.assertFalse(Updater.isStorePath("""C:\Program Files\Proyecta\Proyecta.exe"""))
        kotlin.test.assertFalse(Updater.isStorePath(null)) // corriendo desde Gradle
    }

    @Test
    fun plainHttpOnlyFromThisSameComputer() {
        val local = """{"tag_name":"2.3.1","assets":[{"name":"P.msi","browser_download_url":"http://127.0.0.1:8899/P.msi"}]}"""
        kotlin.test.assertNotNull(Updater.parseRelease(local))
        val lan = """{"tag_name":"2.3.1","assets":[{"name":"P.msi","browser_download_url":"http://192.168.1.5/P.msi"}]}"""
        kotlin.test.assertNull(Updater.parseRelease(lan))
    }

    /** Con un instalador firmado de verdad por tools/Firma.java:  PROYECTA_SIGNED=/ruta/Proyecta-x.msi */
    @Test
    fun realSignedInstaller() {
        val path = System.getenv("PROYECTA_SIGNED") ?: return
        val f = java.io.File(path)
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        kotlin.test.assertTrue(Updater.verifySignature(sha, java.io.File("$path.sig").readText()))
    }

    @Test
    fun releaseWithSignatureAsset() {
        val r = Updater.parseRelease(
            """{"tag_name":"v2.3.0","assets":[
              {"name":"Proyecta-2.3.0.msi","browser_download_url":"https://x/Proyecta-2.3.0.msi","size":5},
              {"name":"Proyecta-2.3.0.msi.sig","browser_download_url":"https://x/Proyecta-2.3.0.msi.sig","size":1},
              {"name":"ProyectaRemoto.apk","browser_download_url":"https://x/ProyectaRemoto.apk","size":2}]}""",
        )!!
        kotlin.test.assertEquals("https://x/Proyecta-2.3.0.msi", r.msiUrl)
        kotlin.test.assertEquals("https://x/Proyecta-2.3.0.msi.sig", r.sigUrl)
    }
}
