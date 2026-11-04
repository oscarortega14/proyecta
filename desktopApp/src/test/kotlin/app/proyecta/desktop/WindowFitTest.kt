package app.proyecta.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowFitTest {
    private val operator = DpSize(1440.dp, 820.dp)

    @Test
    fun laptopAt125PercentMaximizesTheOperator() {
        val laptop = DpSize(1536.dp, 824.dp) // 1920×1080 al 125 %, sin la barra de tareas
        assertTrue(doesNotFit(operator, laptop) || operator.height > laptop.height * 0.96f)
        val small = DpSize(1366.dp, 728.dp) // portátil 1366×768
        assertTrue(doesNotFit(operator, small))
        val fitted = fitSize(DpSize(980.dp, 720.dp), small) // ventana "Conectar dispositivos"
        assertTrue(fitted.height <= small.height && fitted.width == 980.dp)
    }

    @Test
    fun bigScreensKeepTheWantedSize() {
        val fullHd = DpSize(1920.dp, 1040.dp)
        assertFalse(doesNotFit(operator, fullHd))
        assertEquals(operator, fitSize(operator, fullHd))
        assertEquals(operator, fitSize(operator, null)) // sin pantalla (pruebas): igual
    }
}
