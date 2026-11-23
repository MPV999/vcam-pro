package com.vcam.core.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Real metadata read from the media container, never guessed from the file name. */
data class MediaInfo(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val mimeType: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val rotationDegrees: Int,
    val bitrate: Int,
)

class MediaProbe(private val context: Context) {

    suspend fun probe(uri: Uri): MediaInfo? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            extract(retriever)
        } catch (error: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun probe(file: File): MediaInfo? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            extract(retriever)
        } catch (error: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extract(retriever: MediaMetadataRetriever): MediaInfo {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
        val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            ?.toIntOrNull() ?: 0

        val swapped = rotation == 90 || rotation == 270
        return MediaInfo(
            width = if (swapped) height else width,
            height = if (swapped) width else height,
            durationMs = durationMs,
            mimeType = mime,
            hasVideo = hasVideo,
            hasAudio = hasAudio,
            rotationDegrees = rotation,
            bitrate = bitrate,
        )
    }
}
