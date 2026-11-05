package app.proyecta.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import com.sun.jna.Pointer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.Canvas
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

    /** Para los anuncios: con sonido. */
    private val audioFactory: MediaPlayerFactory? by lazy {
        if (!available) null
        else runCatching { MediaPlayerFactory("--quiet", "--no-video-title-show", "--avcodec-hw=any") }.getOrNull()
    }

    private class Entry(val loop: VideoLoop, var users: Int)
    private val loops = HashMap<String, Entry>()

    /**
     * Un solo reproductor por archivo, compartido por el videobeam y las vistas previas.
     * [announcement]: con sonido, una sola vez y a mayor resolución (es lo principal en pantalla).
     */
    @Synchronized
    fun acquire(file: File, announcement: Boolean = false, light: Boolean = false): VideoLoop? {
        val f = (if (announcement) audioFactory else factory) ?: return null
        val key = key(file, announcement, light)
        loops[key]?.let { it.users++; return it.loop }
        val q = videoQuality(announcement, light)
        val loop = runCatching {
            VideoLoop(file, f, q.maxWidth, repeat = !announcement, audio = announcement, minFrameNanos = q.minFrameNanos)
        }.onFailure { Log.error("No pude abrir el video ${file.name}", it) }.getOrNull() ?: return null
        loops[key] = Entry(loop, 1)
        return loop
    }

    private fun key(file: File, announcement: Boolean, light: Boolean) =
        file.absolutePath + (if (announcement) "|anuncio" else "") + (if (light) "|liviano" else "")

    @Synchronized
    fun release(file: File, announcement: Boolean = false, light: Boolean = false) {
        val key = key(file, announcement, light)
        val e = loops[key] ?: return
        if (--e.users <= 0) {
            loops.remove(key)
            e.loop.close()
        }
    }
}

/** Resolución y cuadros por segundo con que VLC entrega el video. */
data class VideoQuality(val maxWidth: Int, val maxFps: Int) {
    /** Separación mínima entre cuadros (un poco menos que 1/fps, para no perder cuadros que llegan antes). */
    val minFrameNanos: Long get() = (1_000_000_000L / maxFps) * 3 / 4
}

/**
 * Fondo normal: 1280 px y hasta 30 cuadros. Modo liviano: 854 px y 20 cuadros (mucho menos trabajo).
 * Anuncio: es lo principal en pantalla, va a 1920 px (o 1280 en modo liviano).
 */
fun videoQuality(announcement: Boolean, light: Boolean): VideoQuality = when {
    announcement && light -> VideoQuality(1280, 30)
    announcement -> VideoQuality(1920, 30)
    light -> VideoQuality(854, 20)
    else -> VideoQuality(1280, 30)
}

/** Un cuadro listo para dibujar. [seq] cambia en cada cuadro para que la pantalla se redibuje. */
class VideoFrame(val image: ImageBitmap, val width: Int, val height: Int, val seq: Long)

/**
 * Separación mínima entre cuadros: 24 ms. Un video de 24/25/30 cuadros (33-41 ms entre cuadros) pasa
 * completo aunque un cuadro llegue un poco antes; uno de 60 queda en 30 parejos (se toma uno sí, uno no).
 */
private const val MIN_FRAME_NANOS = 24_000_000L

/** Imágenes que se reutilizan en ronda. Ninguna se libera mientras el video suena. */
private const val POOL_SIZE = 6

/**
 * Un video reproduciéndose en bucle, sin sonido; cada cuadro nuevo se publica en [frame].
 *
 * Memoria: hay un grupo fijo de [POOL_SIZE] imágenes que se reutilizan en ronda y que solo se
 * liberan al cerrar el video. Así nunca se dibuja una imagen ya liberada (eso hacía caer la app)
 * y no se crea ni destruye memoria en cada cuadro. Los píxeles se copian en el hilo de la interfaz,
 * el mismo que dibuja, así nunca se lee una imagen a medio escribir.
 */
class VideoLoop(
    file: File,
    factory: MediaPlayerFactory,
    maxWidth: Int,
    private val repeat: Boolean = true,
    audio: Boolean = false,
    private val minFrameNanos: Long = MIN_FRAME_NANOS,
) : AutoCloseable {
    val frame: MutableState<VideoFrame?> = mutableStateOf(null)

    private val player: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()
    @Volatile private var info: ImageInfo? = null
    @Volatile private var bytes = ByteArray(0)
    private val pending = AtomicBoolean(false)
    @Volatile private var lastFrameNanos = 0L
    @Volatile private var closed = false

    // Solo se tocan en el hilo de la interfaz (EDT).
    private var pool: List<Pair<Bitmap, ImageBitmap>> = emptyList()
    private var next = 0
    private var seq = 0L

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
            // Videos de 60 cuadros se reducen a 30 parejos; los de hasta 30 pasan completos.
            val now = System.nanoTime()
            if (now - lastFrameNanos < minFrameNanos) return@RenderCallback
            // Si la pantalla todavía no tomó el cuadro anterior, se descarta este (no se acumula trabajo).
            if (!pending.compareAndSet(false, true)) return@RenderCallback
            lastFrameNanos = now
            val buf = buffers[0]
            buf.rewind()
            val dst = bytes
            buf.get(dst, 0, min(dst.size, buf.remaining()))
            SwingUtilities.invokeLater {
                try {
                    if (!closed) publish(i, dst)
                } finally {
                    pending.set(false)
                }
            }
        }
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(format, render, true))
        player.controls().setRepeat(repeat)
        if (repeat) player.media().play(file.absolutePath, ":no-audio", ":input-repeat=65535")
        else player.media().play(file.absolutePath, *(if (audio) emptyArray() else arrayOf(":no-audio")))
    }

    /** En el EDT: copia el cuadro a la siguiente imagen del grupo y lo publica. */
    private fun publish(i: ImageInfo, pixels: ByteArray) {
        if (pool.isEmpty() || pool[0].first.width != i.width || pool[0].first.height != i.height) {
            // Primer cuadro (o cambió el tamaño): se crea el grupo. El anterior se libera más tarde.
            val old = pool
            pool = List(POOL_SIZE) {
                val b = Bitmap()
                check(b.allocPixels(i)) { "No pude reservar memoria para el video" }
                b to b.asComposeImageBitmap()
            }
            releaseLater(old.map { it.first })
        }
        val (bitmap, image) = pool[next]
        next = (next + 1) % pool.size
        val pixmap = bitmap.peekPixels() ?: return
        val rowBytes = i.width * 4
        if (pixmap.rowBytes == rowBytes) {
            Pointer(pixmap.addr).write(0L, pixels, 0, min(pixels.size, rowBytes * i.height))
        } else {
            for (y in 0 until i.height) Pointer(pixmap.addr).write(y.toLong() * pixmap.rowBytes, pixels, y * rowBytes, rowBytes)
        }
        bitmap.notifyPixelsChanged()
        frame.value = VideoFrame(image, i.width, i.height, ++seq)
    }

    override fun close() {
        closed = true
        runCatching { player.controls().stop() }
        runCatching { player.release() }
        SwingUtilities.invokeLater {
            frame.value = null
            releaseLater(pool.map { it.first })
            pool = emptyList()
        }
    }

    /** Libera imágenes un par de segundos después, cuando ya nadie puede estar dibujándolas. */
    private fun releaseLater(bitmaps: List<Bitmap>) {
        if (bitmaps.isEmpty()) return
        javax.swing.Timer(3_000) { bitmaps.forEach { it.close() } }.apply { isRepeats = false; start() }
    }
}

/**
 * Video de fondo a pantalla completa (recortado para llenar). No dibuja nada si no se puede reproducir.
 * [maxFps] limita cuántas veces por segundo se redibuja este lugar (las vistas previas usan menos).
 */
@Composable
fun VideoBackground(file: File, modifier: Modifier = Modifier, maxFps: Int = 30, announcement: Boolean = false, light: Boolean = false) {
    val loop = remember(file, announcement, light) { VideoBackgrounds.acquire(file, announcement, light) }
    DisposableEffect(file, announcement, light) { onDispose { if (loop != null) VideoBackgrounds.release(file, announcement, light) } }
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
    Canvas(modifier.fillMaxSize()) {
        val f = shown.value ?: return@Canvas
        if (announcement) {
            // El anuncio se ve entero (como ContentScale.Fit), con franjas negras si hace falta.
            val s = minOf(size.width / f.width, size.height / f.height)
            val w = (f.width * s).roundToInt(); val h = (f.height * s).roundToInt()
            drawImage(
                f.image, srcOffset = IntOffset.Zero, srcSize = IntSize(f.width, f.height),
                dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()),
                dstSize = IntSize(w, h), filterQuality = FilterQuality.Medium,
            )
            return@Canvas
        }
        // Recortar para llenar (como ContentScale.Crop), centrado.
        val scale = maxOf(size.width / f.width, size.height / f.height)
        val srcW = (size.width / scale).roundToInt().coerceIn(1, f.width)
        val srcH = (size.height / scale).roundToInt().coerceIn(1, f.height)
        drawImage(
            f.image,
            srcOffset = IntOffset((f.width - srcW) / 2, (f.height - srcH) / 2),
            srcSize = IntSize(srcW, srcH),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}
