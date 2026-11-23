package com.vcam.core.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Copies a user-picked document (Storage Access Framework) into app-private storage.
 *
 * Only the single URI the user picked is read — the app never enumerates the user's files.
 */
class SafImporter(
    private val context: Context,
    private val fileStore: MediaFileStore,
) {

    data class ImportedFile(
        val file: File,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
    )

    suspend fun import(uri: Uri): Result<ImportedFile> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: guessMimeType(uri)
            val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "import"

            if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) {
                return@withContext Result.failure(
                    IOException("Unsupported type: $mimeType"),
                )
            }

            val target = fileStore.newImportedFile(displayName, mimeType)
            val size = resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Cannot open $uri")

            if (size <= 0L) {
                target.delete()
                return@withContext Result.failure(IOException("Empty file: $uri"))
            }

            Result.success(
                ImportedFile(
                    file = target,
                    displayName = displayName,
                    mimeType = mimeType,
                    sizeBytes = target.length(),
                ),
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    private fun guessMimeType(uri: Uri): String {
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (extension) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            else -> "image/jpeg"
        }
    }
}
