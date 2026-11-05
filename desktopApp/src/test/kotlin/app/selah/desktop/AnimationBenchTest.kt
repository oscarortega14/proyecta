package app.selah.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import app.selah.shared.Theme
import kotlin.test.Test

/**
 * Mide el costo de dibujar cada fondo incluido (dibujo por CPU, el peor caso).
 * Se omite salvo que se pida:  SELAH_BENCH=1 ./gradlew :desktopApp:test --tests '*AnimationBench*'
 */
class AnimationBenchTest {
    @Test
    fun bench() {
        if (System.getenv("SELAH_BENCH") == null) return
        for (key in listOf("(vacío)") + Theme.BUILTINS.map { it.first }) {
            // estado observable: cada cambio obliga a redibujar el fondo, como en vivo
            val t = androidx.compose.runtime.mutableFloatStateOf(0f)
            ImageComposeScene(1920, 1080, Density(1f)) { BuiltinBackground(key, time = t) }.use { scene ->
                repeat(5) { t.floatValue += 0.03f; scene.render(it * 33_000_000L) } // calentar
                val n = 30
                val start = System.nanoTime()
                repeat(n) { t.floatValue += 0.033f; scene.render((5 + it) * 33_000_000L) }
                val ms = (System.nanoTime() - start) / 1e6 / n
                println("BENCH $key: %.1f ms/cuadro (1080p, CPU)".format(ms))
            }
        }
    }
}
