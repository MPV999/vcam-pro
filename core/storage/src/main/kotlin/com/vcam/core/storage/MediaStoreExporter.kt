package com.vcam.core.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Explicit export of one library item into the shared gallery through MediaStore.
 *
 * Uses the scoped-storage contract for Android 10+: insert a pending row, stream the
 * bytes, then clear IS_PENDING. No storage permission is required and nothing outside the
 * exported file is touched.
 */
class MediaStoreExporter(private val context: Context) {

    suspend fun export(
        file: File,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext Result.failure(IOException("File not found: ${file.absolutePath}"))
        }
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativePath = if (isVideo) {
            "${Environment.DIRECTORY_MOVIES}/VCAM Pro"
        } else {
            "${Environment.DIRECTORY_PICTURES}/VCAM Pro"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = try {
            resolver.insert(collection, values)
        } catch (error: Exception) {
            null
        } ?: return@withContext Result.failure(IOException("MediaStore refused the insert"))

        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Cannot open output stream for $uri")

            val clearPending = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, clearPending, null, null)
            Result.success(uri)
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            Result.failure(error)
        }
    }
}
