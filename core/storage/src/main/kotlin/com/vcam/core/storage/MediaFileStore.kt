package com.vcam.core.storage

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-private storage for captured and imported media.
 *
 * Everything lives under `filesDir/media`, i.e. scoped storage inside the app sandbox:
 * no other app can read it, and the user's private gallery is never scanned or modified.
 * Exporting to the shared gallery is an explicit, user-triggered action (see
 * [MediaStoreExporter]).
 */
class MediaFileStore(private val context: Context) {

    private val root: File by lazy {
        File(context.filesDir, "media").apply { if (!exists()) mkdirs() }
    }

    private val photoDirectory: File by lazy {
        File(root, "photos").apply { if (!exists()) mkdirs() }
    }

    private val videoDirectory: File by lazy {
        File(root, "videos").apply { if (!exists()) mkdirs() }
    }

    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    fun newPhotoFile(mimeType: String = "image/jpeg"): File {
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        return File(photoDirectory, "IMG_${timestamp()}.$extension")
    }

    fun newVideoFile(): File = File(videoDirectory, "VID_${timestamp()}.mp4")

    fun newImportedFile(originalName: String, mimeType: String): File {
        val extension = originalName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            ?: mimeType.substringAfterLast('/', "bin")
        val directory = if (mimeType.startsWith("video/")) videoDirectory else photoDirectory
        return File(directory, "IMP_${timestamp()}.$extension")
    }

    fun existingFile(path: String): File? = File(path).takeIf { it.exists() && it.isFile }

    fun delete(path: String): Boolean {
        val file = File(path)
        return !file.exists() || file.delete()
    }

    /** Bytes used by the library on disk. */
    fun usageBytes(): Long {
        val files = root.walkTopDown().filter { it.isFile }.toList()
        return files.sumOf { it.length() }
    }

    private fun timestamp(): String = timestampFormat.format(Date())
}
