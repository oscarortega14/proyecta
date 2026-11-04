package app.proyecta.desktop

import app.proyecta.shared.ProtocolJson
import app.proyecta.shared.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LightModeTest {
    @Test
    fun lightVideoIsSmallerAndSlower() {
        val normal = videoQuality(announcement = false, light = false)
        val light = videoQuality(announcement = false, light = true)
        assertEquals(VideoQuality(1280, 30), normal)
        assertEquals(VideoQuality(854, 20), light)
        // ~2.25 veces menos píxeles y 1/3 menos cuadros
        assertTrue(light.maxWidth * light.maxWidth * light.maxFps * 3 < normal.maxWidth * normal.maxWidth * normal.maxFps)
        // La separación mínima deja pasar cuadros que llegan un poco antes de tiempo
        assertTrue(light.minFrameNanos in 30_000_000L..50_000_000L)
        // Los anuncios son lo principal: más resolución, pero también bajan en modo liviano
        assertEquals(1920, videoQuality(true, false).maxWidth)
        assertEquals(1280, videoQuality(true, true).maxWidth)
    }

    @Test
    fun animatedBackgroundsSlowDownInLightMode() {
        assertEquals(60, animationFps(preview = false, gpu = true, smooth = true, light = false))
        assertEquals(30, animationFps(preview = false, gpu = true, smooth = false, light = false))
        assertEquals(24, animationFps(preview = false, gpu = true, smooth = true, light = true))
        assertEquals(24, animationFps(preview = false, gpu = false, smooth = true, light = false))
        assertEquals(15, animationFps(preview = true, gpu = true, smooth = true, light = false))
    }

    @Test
    fun offByDefaultAndOldThemesStillLoad() {
        assertFalse(Theme().light)
        // tema.json guardado por una versión anterior (sin "light")
        val old = ProtocolJson.decodeFromString(Theme.serializer(), """{"color":"#000000","smooth":false}""")
        assertFalse(old.light)
        assertFalse(old.smooth)
    }
}
