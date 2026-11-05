@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package app.selah.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/**
 * Botón "Subir foto o video" en iPhone: el selector de fotos del sistema (PHPicker). No pide permiso
 * de la galería: la persona elige qué entregar. Las fotos (HEIC en el iPhone) se convierten a JPG;
 * los videos se copian tal cual (.mov / .mp4). Se sube desde un archivo temporal, por partes.
 */
@Composable
actual fun rememberMediaPicker(onPicked: (PickedFile) -> Unit): (() -> Unit)? {
    val controller = LocalUIViewController.current
    val callback = rememberUpdatedState(onPicked)
    val delegate = remember { PickerDelegate { file -> callback.value(file) } }
    return {
        val config = PHPickerConfiguration().apply {
            selectionLimit = 1
            filter = PHPickerFilter.anyFilterMatchingSubfilters(listOf(PHPickerFilter.imagesFilter, PHPickerFilter.videosFilter))
        }
        val picker = PHPickerViewController(configuration = config)
        picker.delegate = delegate
        controller.presentViewController(picker, animated = true, completion = null)
    }
}

private const val MOVIE = "public.movie"
private const val IMAGE = "public.image"

private class PickerDelegate(private val onPicked: (PickedFile) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider ?: return
        val suggested = provider.suggestedName
        val dir = uploadsDir()
        val done: (String?, String) -> Unit = { path, name ->
            if (path != null) {
                val size = fileSize(path)
                dispatch_async(dispatch_get_main_queue()) { onPicked(PickedFile(name, size) { readInChunks(path) }) }
            }
        }
        if (provider.hasItemConformingToTypeIdentifier(MOVIE)) {
            // El archivo que entrega iOS se borra al salir de este bloque: se copia antes.
            provider.loadFileRepresentationForTypeIdentifier(MOVIE) { url: NSURL?, _ ->
                val ext = url?.pathExtension?.lowercase()?.takeIf { it in setOf("mov", "mp4", "m4v") } ?: "mov"
                val name = uploadFileName(suggested, ext, "video")
                val target = "$dir/$name"
                val ok = url?.path?.let { NSFileManager.defaultManager.copyItemAtPath(it, toPath = target, error = null) } == true
                done(if (ok) target else null, name)
            }
        } else if (provider.hasItemConformingToTypeIdentifier(IMAGE)) {
            provider.loadDataRepresentationForTypeIdentifier(IMAGE) { data: NSData?, _ ->
                val jpg = data?.let { UIImage.imageWithData(it) }?.let { UIImageJPEGRepresentation(it, 0.9) }
                val name = uploadFileName(suggested, "jpg", "foto")
                val target = "$dir/$name"
                done(if (jpg?.writeToFile(target, atomically = true) == true) target else null, name)
            }
        }
    }
}

/** Carpeta temporal de subidas; se vacía en cada elección (solo se guarda la última). */
private fun uploadsDir(): String {
    val dir = NSTemporaryDirectory() + "subidas"
    val fm = NSFileManager.defaultManager
    fm.removeItemAtPath(dir, error = null)
    fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return dir
}

private fun fileSize(path: String): Long? =
    (NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue

/** Lee el archivo de 64 KB en 64 KB mientras se envía (un video largo no llena la memoria). */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
private fun readInChunks(path: String): ByteReadChannel = GlobalScope.writer(Dispatchers.IO) {
    val handle = NSFileHandle.fileHandleForReadingAtPath(path) ?: return@writer
    try {
        while (true) {
            val data = handle.readDataOfLength(65_536u)
            val n = data.length.toInt()
            if (n == 0) break
            val bytes = ByteArray(n)
            bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
            channel.writeFully(bytes)
        }
    } finally {
        handle.closeFile()
    }
}.channel
