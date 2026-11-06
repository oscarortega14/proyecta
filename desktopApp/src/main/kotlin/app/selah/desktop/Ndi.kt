package app.selah.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.io.File

/**
 * Salida NDI® (video por la red local para OBS, vMix, ATEM, Resolume…).
 *
 * Selah no trae la librería de NDI: usa la que instala el programa gratuito "NDI Tools"
 * (https://ndi.video/tools/). Si no está instalada, la opción dice cómo conseguirla.
 * Se llama a las funciones de NDI directamente (JNA), sin envoltorios de terceros.
 *
 * NDI® es una marca registrada de Vizrt NDI AB.
 */
object Ndi {
    const val TOOLS_URL = "https://ndi.video/tools/"

    /** Funciones de Processing.NDI.Lib (las pocas que usamos). */
    @Suppress("FunctionName")
    interface Lib : Library {
        fun NDIlib_initialize(): Boolean
        fun NDIlib_version(): String?
        fun NDIlib_send_create(settings: SendCreate): Pointer?
        fun NDIlib_send_destroy(instance: Pointer)
        fun NDIlib_send_send_video_v2(instance: Pointer, frame: VideoFrame)
        fun NDIlib_send_get_no_connections(instance: Pointer, timeoutMs: Int): Int
    }

    /** NDIlib_send_create_t */
    @Structure.FieldOrder("p_ndi_name", "p_groups", "clock_video", "clock_audio")
    class SendCreate : Structure() {
        @JvmField var p_ndi_name: String? = null
        @JvmField var p_groups: String? = null
        @JvmField var clock_video: Byte = 1
        @JvmField var clock_audio: Byte = 0
    }

    /** NDIlib_video_frame_v2_t */
    @Structure.FieldOrder(
        "xres", "yres", "FourCC", "frame_rate_N", "frame_rate_D", "picture_aspect_ratio",
        "frame_format_type", "timecode", "p_data", "line_stride_in_bytes", "p_metadata", "timestamp",
    )
    class VideoFrame : Structure() {
        @JvmField var xres: Int = 0
        @JvmField var yres: Int = 0
        @JvmField var FourCC: Int = FOURCC_BGRA
        @JvmField var frame_rate_N: Int = 30_000
        @JvmField var frame_rate_D: Int = 1_000
        @JvmField var picture_aspect_ratio: Float = 0f
        @JvmField var frame_format_type: Int = FORMAT_PROGRESSIVE
        @JvmField var timecode: Long = TIMECODE_SYNTHESIZE
        @JvmField var p_data: Pointer? = null
        @JvmField var line_stride_in_bytes: Int = 0
        @JvmField var p_metadata: String? = null
        @JvmField var timestamp: Long = 0

        /** Posición de un campo en bytes (para comprobar que coincide con el de NDI). */
        internal fun offsetOf(field: String) = fieldOffset(field)
    }

    /** 'B','G','R','A': 8 bits por canal, con transparencia (sin premultiplicar). */
    val FOURCC_BGRA = 'B'.code or ('G'.code shl 8) or ('R'.code shl 16) or ('A'.code shl 24)
    const val FORMAT_PROGRESSIVE = 1
    const val TIMECODE_SYNTHESIZE = Long.MAX_VALUE

    /** Nombre de la librería según el sistema (solo se usa en Windows, pero así también se prueba). */
    val libraryName: String
        get() = when {
            System.getProperty("os.name").startsWith("Windows") -> "Processing.NDI.Lib.x64.dll"
            System.getProperty("os.name").startsWith("Mac") -> "libndi.dylib"
            else -> "libndi.so.6"
        }

    /**
     * Dónde instaló NDI Tools su librería: las variables NDI_RUNTIME_DIR_V6, V5… (cada versión nueva
     * sirve para programas hechos con la anterior) o la carpeta de siempre.
     */
    fun findRuntime(env: (String) -> String? = System::getenv, extra: List<File> = defaultDirs()): File? {
        val dirs = (6 downTo 3).mapNotNull { v -> env("NDI_RUNTIME_DIR_V$v")?.let(::File) } + extra
        return dirs.map { File(it, libraryName) }.firstOrNull { it.isFile }
    }

    private fun defaultDirs(): List<File> = listOfNotNull(System.getenv("ProgramFiles")?.let { File(it, "NDI") })
        .filter { it.isDirectory }
        .flatMap { root -> root.walkTopDown().maxDepth(3).filter { it.isDirectory && it.name.matches(Regex("v\\d+")) }.toList() }

    /** La librería cargada, o null si NDI Tools no está instalado (o no se pudo cargar). */
    val lib: Lib? by lazy {
        val file = findRuntime() ?: return@lazy null
        runCatching {
            Native.load(file.absolutePath, Lib::class.java).takeIf { it.NDIlib_initialize() }
        }.onFailure { Log.error("No pude cargar NDI desde $file", it) }.getOrNull()
            ?.also { Log.info("NDI listo: ${runCatching { it.NDIlib_version() }.getOrNull()} ($file)") }
    }

    val available: Boolean get() = lib != null

    /** Una fuente NDI que se ve en la red con el nombre "<PC> (Selah <nombre>)". */
    class Sender(private val lib: Lib, name: String, val width: Int, val height: Int, fps: Int) : AutoCloseable {
        private val instance: Pointer = lib.NDIlib_send_create(SendCreate().apply { p_ndi_name = name; clock_video = 1 })
            ?: throw IllegalStateException("NDI no pudo crear la fuente \"$name\"")
        private val buffer = Memory(width.toLong() * height * 4)
        private val frame = VideoFrame().apply {
            xres = width; yres = height; line_stride_in_bytes = width * 4
            frame_rate_N = fps * 1000; frame_rate_D = 1000
            picture_aspect_ratio = width.toFloat() / height
            p_data = buffer
        }

        /** Cuántos programas están recibiendo esta fuente (si nadie, no hace falta dibujar). */
        fun connections(): Int = lib.NDIlib_send_get_no_connections(instance, 0)

        /** Envía un cuadro BGRA de [width]×[height] (espera lo necesario para mantener los cuadros por segundo). */
        fun send(bgra: ByteArray) {
            buffer.write(0, bgra, 0, minOf(bgra.size, width * height * 4))
            lib.NDIlib_send_send_video_v2(instance, frame)
        }

        override fun close() = lib.NDIlib_send_destroy(instance)
    }
}
