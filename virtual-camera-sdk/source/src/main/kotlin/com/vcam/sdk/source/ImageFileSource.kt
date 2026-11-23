package com.vcam.sdk.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.vcam.sdk.api.PlaybackController
import com.vcam.sdk.api.PlaybackState
import com.vcam.sdk.api.SourceCapabilities
import com.vcam.sdk.api.SourceError
import com.vcam.sdk.api.SourceKind
import com.vcam.sdk.api.SourceState
import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.playback.GlFrameProducer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Turns a still image into a continuous frame stream at the configured FPS.
 *
 * The image is decoded once (down-sampled to at most twice the output size to bound
 * memory) and re-rendered per tick. Cost per frame is one GL draw plus one read-back, so
 * a 4K image at 30 fps behaves exactly like a video of the same size.
 */
class ImageFileSource(
    private val context: Context,
    private val uri: Uri,
    override val displayName: String = uri.lastPathSegment ?: "image",
) : BaseCameraSource(), PlaybackController {

    override val id: String = "image:$uri"

    override val kind: SourceKind = SourceKind.IMAGE_FILE

    override val capabilities: SourceCapabilities = SourceCapabilities.STATIC_IMAGE

    private val _playback = MutableStateFlow(PlaybackState(isPlaying = false, bufferedPercent = 100f))
    override val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vcam-image-source")
    }

    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    @Volatile
    private var bitmap: Bitmap? = null

    override suspend fun open(configuration: FrameConfiguration) {
        beginSession(configuration)
        try {
            val decoded = withContext(Dispatchers.IO) { decode(uri, configuration) }
            bitmap = decoded
            sourceWidth = decoded.width
            sourceHeight = decoded.height
            durationUs = 0L
            transition(SourceState.Ready)
        } catch (error: SourceError) {
            failWith(error)
            throw error
        } catch (throwable: Throwable) {
            val error = SourceError.IoFailed("Cannot decode $uri", throwable)
            failWith(error)
            throw error
        }
    }

    override suspend fun start() {
        if (disposed) throw SourceError.Internal("Source is closed")
        paused.set(false)
        if (running.compareAndSet(false, true)) {
            transition(SourceState.Running)
            executor.execute { renderLoop() }
        } else {
            transition(SourceState.Running)
        }
        _playback.value = _playback.value.copy(isPlaying = true, ended = false)
    }

    override suspend fun stop() {
        paused.set(true)
        if (state.value !is SourceState.Closed) transition(SourceState.Paused)
        _playback.value = _playback.value.copy(isPlaying = false)
    }

    override suspend fun close() {
        if (disposed) return
        disposed = true
        running.set(false)
        paused.set(false)
        withContext(Dispatchers.Default) {
            executor.shutdown()
            runCatching { executor.awaitTermination(3, TimeUnit.SECONDS) }
        }
        bitmap?.recycle()
        bitmap = null
        endSession()
        transition(SourceState.Closed)
        disposeScope()
    }

    override fun playbackController(): PlaybackController = this

    override suspend fun play() = start()

    override suspend fun pause() = stop()

    override suspend fun seekTo(positionUs: Long) {
        // A still image has no timeline; seeking is a documented no-op.
    }

    override suspend fun setLooping(looping: Boolean) {
        _playback.value = _playback.value.copy(looping = looping)
    }

    private fun renderLoop() {
        val image = bitmap ?: return
        val producer = GlFrameProducer(configuration, bufferCount = 3) { frame -> emit(frame) }
        try {
            producer.initialize()
            var nextTick = System.nanoTime()
            val interval = configuration.frameIntervalNanos
            while (running.get() && !disposed) {
                if (paused.get()) {
                    Thread.sleep(10L)
                    nextTick = System.nanoTime()
                    continue
                }
                producer.emitBitmap(image, rotationDegrees = 0, timestampNanos = System.nanoTime())

                nextTick += interval
                val sleepNanos = nextTick - System.nanoTime()
                if (sleepNanos > 1_000_000L) {
                    Thread.sleep(sleepNanos / 1_000_000L, (sleepNanos % 1_000_000L).toInt())
                } else if (sleepNanos < -100_000_000L) {
                    // Fell more than 100 ms behind: resynchronise instead of bursting.
                    nextTick = System.nanoTime()
                }
            }
        } catch (throwable: Throwable) {
            if (!disposed) {
                failWith(SourceError.Internal("Image render loop failed: ${throwable.message}", throwable))
            }
        } finally {
            producer.release()
        }
    }

    private fun decode(uri: Uri, configuration: FrameConfiguration): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) throw SourceError.IoFailed("Cannot open $uri")
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw SourceError.UnsupportedMedia("Not a decodable image: $uri")
        }

        var sampleSize = 1
        val maxWidth = configuration.width * 2
        val maxHeight = configuration.height * 2
        while (bounds.outWidth / (sampleSize * 2) >= maxWidth &&
            bounds.outHeight / (sampleSize * 2) >= maxHeight
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) throw SourceError.IoFailed("Cannot open $uri")
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw SourceError.UnsupportedMedia("Unsupported image format: $uri")
        return decoded
    }
}
