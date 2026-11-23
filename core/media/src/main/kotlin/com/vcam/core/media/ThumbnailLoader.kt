package com.vcam.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Small thumbnail cache for the media library.
 *
 * Thumbnails are decoded from the local file only, capped by an LRU cache counted in
 * bitmaps (not bytes) so a 48-entry cache stays bounded even with large frames.
 */
class ThumbnailLoader(private val maxEntries: Int = 48) {

    private val cache = object : LruCache<String, Bitmap>(maxEntries) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    suspend fun load(path: String, isVideo: Boolean, targetSizePx: Int = 320): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = "$path@$targetSizePx"
            cache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }

            val bitmap = if (isVideo) decodeVideoFrame(path, targetSizePx) else decodeImage(path, targetSizePx)
            if (bitmap != null) cache.put(key, bitmap)
            bitmap
        }

    fun clear() = cache.evictAll()

    private fun decodeImage(path: String, targetSizePx: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetSizePx &&
            bounds.outHeight / (sample * 2) >= targetSizePx
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
    }

    private fun decodeVideoFrame(path: String, targetSizePx: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val width = frame.width
            val height = frame.height
            if (width <= 0 || height <= 0) {
                frame.recycle()
                return null
            }
            val scale = targetSizePx.toFloat() / maxOf(width, height)
            if (scale >= 1f) {
                frame
            } else {
                val scaled = Bitmap.createScaledBitmap(
                    frame,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true,
                )
                if (scaled !== frame) frame.recycle()
                scaled
            }
        } catch (error: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
