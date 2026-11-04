package app.proyecta.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import app.proyecta.shared.PlanItem
import app.proyecta.shared.PlanKind
import app.proyecta.shared.PresentationState
import app.proyecta.shared.ServicePlanState
import app.proyecta.shared.Slide
import app.proyecta.shared.SongSummary
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectAndLayoutTest {
    /** El QR que se dibuja se puede leer y contiene exactamente la dirección. */
    @Test
    fun qrCodesDecodeToTheUrl() {
        for (url in listOf("http://192.168.40.35:8765", "http://10.0.0.7:8765/escenario.html")) {
            val m = qrMatrix(url)
            val quiet = 4
            val scale = 4
            val w = (m.width + quiet * 2) * scale
            val pixels = ByteArray(w * w) { 255.toByte() }
            for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y]) {
                for (dy in 0 until scale) for (dx in 0 until scale) pixels[((y + quiet) * scale + dy) * w + (x + quiet) * scale + dx] = 0
            }
            val source = object : LuminanceSource(w, w) {
                override fun getRow(y: Int, row: ByteArray?) = pixels.copyOfRange(y * w, y * w + w)
                override fun getMatrix() = pixels
            }
            assertEquals(url, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text)
        }
    }

    /** Dibuja la ventana del operador para revisarla a ojo:  PROYECTA_RENDER_DIR=/tmp/out ./gradlew … */
    @Test
    fun renderOperatorScreen() {
        val out = System.getenv("PROYECTA_RENDER_DIR")?.let(::File) ?: return
        out.mkdirs()
        val state = PresentationState(
            "Alabad a Jehová",
            listOf(
                Slide("", "Te alabaré oh Jehová con todo mi corazón\nCantaré y contaré tus maravillas"),
                Slide("Coro", "Alabad a Jehová con todo tu corazón"),
                Slide("Pregón 1", "Canta el coro mi hermano\nLa presencia del Señor reposa aquí"),
                Slide("Pregón 2", "Cantaré y contaré tus maravillas\nMe alegraré y me regocijaré"),
            ),
            index = 1, songId = "Alabad a Jehová", alert = "Carro ABC123",
        )
        val catalog = Catalog(
            listOf("Alabad a Jehová", "Bendito sea", "Bueno es alabar", "Celebraré", "Como hizo David", "Con pandero")
                .map { SongSummary(it, it, "Primera línea de $it") },
            "Reina-Valera 1909",
        )
        val plan = ServicePlanState(listOf(PlanItem(PlanKind.SONG, "Alabad a Jehová", "Alabad a Jehová"), PlanItem(PlanKind.BIBLE, "Juan 3:16", "Juan 3:16")), 0)
        ImageComposeScene(1440, 820, Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ProvideLook(Look(frozenTime = 2f)) {
                    OperatorScreen(
                        state, catalog, plan, serverError = null, projectorOn = true, onToggleProjector = {},
                        onCommand = { null }, onReload = {}, onOpenFolder = {}, onEdit = {}, onOpenBackgrounds = {},
                        onOpenConnect = {}, searchBible = { null }, firewallOk = false, onImport = { null }, onExport = { null },
                    )
                }
            }
        }.use { File(out, "operador.png").writeBytes(it.render(0).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
        ImageComposeScene(980, 720, Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                androidx.compose.material3.Surface { QrCode("http://192.168.40.35:8765", androidx.compose.ui.unit.Dp(220f)) }
            }
        }.use { File(out, "qr.png").writeBytes(it.render(0).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
    }
}
