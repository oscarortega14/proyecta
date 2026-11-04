package app.proyecta.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.sun.jna.NativeLibrary
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.math.min
import kotlin.math.roundToInt

val VIDEO_EXTENSIONS = setOf("mp4", "mov", "m4v", "webm", "mkv", "avi", "wmv")

fun isVideoFile(name: String) = name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

/**
 * Fondos de video en bucle con VLC (vía vlcj). Se usa el VLC que viene dentro de Proyecta
 * (carpeta "vlc" en los recursos de la app); si no está, uno instalado en el PC.
 * Si no hay ninguno, [available] es false y los videos simplemente no se muestran.
 */
object VideoBackgrounds {
    /** Ancho máximo al que VLC entrega los cuadros: detrás de la letra no se nota y alivia mucho el trabajo. */
    private const val MAX_WIDTH = 1280

    /** Carpeta del VLC incluido en la app (Windows), si existe. */
    val bundledDir: File? by lazy {
        System.getProperty("compose.application.resources.dir")
            ?.let { File(it, "vlc") }
            ?.takeIf { File(it, "libvlc.dll").isFile && File(it, "libvlccore.dll").isFile }
    }

    val available: Boolean by lazy { useBundled() || runCatching { NativeDiscovery().discover() }.getOrDefault(false) }

    /** Apunta JNA al VLC incluido; VLC busca sus "plugins" junto a libvlccore.dll. */
    private fun useBundled(): Boolean {
        val dir = bundledDir ?: return false
        return runCatching {
            NativeLibrary.addSearchPath("libvlccore", dir.absolutePath)
            NativeLibrary.addSearchPath("libvlc", dir.absolutePath)
            NativeLibrary.getInstance(File(dir, "libvlccore.dll").absolutePath) // cargar primero su dependencia
            true
        }.getOrDefault(false)
    }

    private val factory: MediaPlayerFactory? by lazy {
        if (!available) null
        else runCatching { MediaPlayerFactory("--no-audio", "--quiet", "--no-video-title-show", "--avcodec-hw=any") }.getOrNull()
    }

    private class Entry(val loop: VideoLoop, var users: Int)
    private val loops = HashMap<String, Entry>()

    /** Un solo reproductor por archivo, compartido por el videobeam y las vistas previas. */
    @Synchronized
    fun acquire(file: File): VideoLoop? {
        val f = factory ?: return null
        val key = file.absolutePath
        loops[key]?.let { it.users++; return it.loop }
        val loop = runCatching { VideoLoop(file, f, MAX_WIDTH) }.getOrNull() ?: return null
        loops[key] = Entry(loop, 1)
        return loop
    }

    @Synchronized
    fun release(file: File) {
        val key = file.absolutePath
        val e = loops[key] ?: return
        if (--e.users <= 0) {
            loops.remove(key)
            e.loop.close()
        }
    }
}

/** Un video reproduciéndose en bucle, sin sonido; cada cuadro nuevo se publica en [frame]. */
private const val MIN_FRAME_NANOS = 32_000_000L // ~30 cuadros por segundo

class VideoLoop(file: File, factory: MediaPlayerFactory, maxWidth: Int) : AutoCloseable {
    val frame: MutableState<ImageBitmap?> = mutableStateOf(null)

    private val player: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()
    @Volatile private var info: ImageInfo? = null
    @Volatile private var bytes = ByteArray(0)
    private val pending = AtomicBoolean(false)
    @Volatile private var lastFrameNanos = 0L
    /** Cuadros ya reemplazados, que se liberan un par de cuadros después (por si aún se están dibujando). */
    private val retired = ArrayDeque<Bitmap>()

    init {
        val format = object : BufferFormatCallbackAdapter() {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                val scale = min(1.0, maxWidth.toDouble() / sourceWidth.coerceAtLeast(1))
                val w = (sourceWidth * scale).roundToInt().coerceAtLeast(2) and 1.inv() // par
                val h = (sourceHeight * scale).roundToInt().coerceAtLeast(2) and 1.inv()
                info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                bytes = ByteArray(w * h * 4)
                return RV32BufferFormat(w, h)
            }
        }
        val render = RenderCallback { _: MediaPlayer, buffers: Array<ByteBuffer>, _: BufferFormat ->
            val i = info ?: return@RenderCallback
            // Máximo ~30 cuadros por segundo: muchos loops vienen a 60 y detrás de la letra no se nota.
            val now = System.nanoTime()
            if (now - lastFrameNanos < MIN_FRAME_NANOS) return@RenderCallback
            lastFrameNanos = now
            // Si la pantalla todavía no dibujó el cuadro anterior, se descarta este (no se acumula trabajo).
            if (!pending.compareAndSet(false, true)) return@RenderCallback
            val buf = buffers[0]
            buf.rewind()
            val dst = bytes
            buf.get(dst, 0, min(dst.size, buf.remaining()))
            // Un solo mapa de bits por cuadro (sin copias intermedias), que se libera a mano después.
            val bitmap = Bitmap()
            if (!bitmap.installPixels(i, dst, i.width * 4)) {
                bitmap.close(); pending.set(false); return@RenderCallback
            }
            bitmap.setImmutable()
            val image = bitmap.asComposeImageBitmap()
            SwingUtilities.invokeLater {
                if (closed) { bitmap.close(); return@invokeLater }
                frame.value = image
                retired.addLast(bitmap)
                // Liberar de inmediato la memoria de los cuadros viejos en vez de esperar al recolector de Java.
                while (retired.size > 3) retired.removeFirst().close()
                pending.set(false)
            }
        }
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(format, render, true))
        player.controls().setRepeat(true)
        player.media().play(file.absolutePath, ":no-audio", ":input-repeat=65535")
    }

    @Volatile private var closed = false

    override fun close() {
        closed = true
        runCatching { player.controls().stop() }
        runCatching { player.release() }
        SwingUtilities.invokeLater {
            frame.value = null
            while (retired.isNotEmpty()) retired.removeFirst().close()
        }
    }
}

/**
 * Video de fondo a pantalla completa (recortado para llenar). No dibuja nada si no se puede reproducir.
 * [maxFps] limita cuántas veces por segundo se redibuja este lugar (las vistas previas usan menos).
 */
@Composable
fun VideoBackground(file: File, modifier: Modifier = Modifier, maxFps: Int = 30) {
    val loop = remember(file) { VideoBackgrounds.acquire(file) }
    DisposableEffect(file) { onDispose { if (loop != null) VideoBackgrounds.release(file) } }
    if (loop == null) return
    val shown = remember(loop) { mutableStateOf(loop.frame.value) }
    LaunchedEffect(loop, maxFps) {
        val minGap = 1_000L / maxFps.coerceIn(1, 60)
        var last = 0L
        snapshotFlow { loop.frame.value }.collect { f ->
            val now = System.currentTimeMillis()
            if (now - last >= minGap || shown.value == null) { shown.value = f; last = now }
        }
    }
    val img = shown.value ?: return
    Image(img, contentDescription = null, modifier = modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}
