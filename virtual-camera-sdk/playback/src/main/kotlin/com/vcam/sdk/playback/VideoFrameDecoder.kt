package com.vcam.sdk.playback

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.vcam.sdk.api.SourceError
import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.frames.Frame
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streaming video decoder: MediaExtractor -> MediaCodec -> SurfaceTexture -> GL -> Frame.
 *
 * The whole video is never in memory; only a bounded set of codec buffers and RGBA
 * read-back buffers exists at any moment.
 *
 * Presentation timing: output buffers are released with a future render timestamp, so the
 * platform itself paces rendering against the media clock. That gives smooth playback and
 * natural backpressure — when the GL consumer is slow the buffer queue fills and the
 * decoder stops producing.
 */
class VideoFrameDecoder(
    private val context: Context,
    private val uri: Uri,
    private val configuration: FrameConfiguration,
    private val bufferCount: Int = 4,
    private val listener: Listener,
) {

    interface Listener {
        fun onPrepared(durationUs: Long, width: Int, height: Int)
        fun onFrame(frame: Frame)
        fun onCompleted()
        fun onError(error: SourceError)
        fun onPositionChanged(positionUs: Long)
    }

    private sealed interface Command {
        data object Pause : Command
        data object Resume : Command
        data class Seek(val positionUs: Long) : Command
        data class Loop(val enabled: Boolean) : Command
        data object Stop : Command
    }

    private val commands = LinkedBlockingQueue<Command>()
    private val running = AtomicBoolean(false)
    private val playing = AtomicBoolean(false)
    private val frameAvailable = AtomicBoolean(false)

    @Volatile
    private var looping = false

    @Volatile
    private var durationUs = 0L

    @Volatile
    private var positionUs = 0L

    @Volatile
    private var preparedWidth = 0

    @Volatile
    private var preparedHeight = 0

    private var decodeExecutor: java.util.concurrent.ExecutorService? = null

    val mediaDurationUs: Long get() = durationUs

    val currentPositionUs: Long get() = positionUs

    val sourceWidth: Int get() = preparedWidth

    val sourceHeight: Int get() = preparedHeight

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "vcam-video-decode").apply {
                priority = Thread.NORM_PRIORITY + 1
            }
        }
        decodeExecutor = executor
        executor.execute { runDecodeLoop() }
    }

    fun pause() {
        commands.offer(Command.Pause)
    }

    fun resume() {
        commands.offer(Command.Resume)
    }

    fun seekTo(positionUs: Long) {
        commands.offer(Command.Seek(positionUs.coerceAtLeast(0L)))
    }

    fun setLooping(enabled: Boolean) {
        looping = enabled
        commands.offer(Command.Loop(enabled))
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        commands.offer(Command.Stop)
        val executor = decodeExecutor
        decodeExecutor = null
        executor?.shutdown()
        try {
            executor?.awaitTermination(3, TimeUnit.SECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun runDecodeLoop() {
        val producer = GlFrameProducer(configuration, bufferCount, listener::onFrame)
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var surface: Surface? = null
        var surfaceTexture: SurfaceTexture? = null
        var callbackThread: HandlerThread? = null

        try {
            producer.initialize()
            producer.initializeExternal()
            surfaceTexture = SurfaceTexture(producer.externalTextureId)
            callbackThread = HandlerThread("vcam-video-callbacks").apply { start() }
            surfaceTexture.setOnFrameAvailableListener(
                { frameAvailable.set(true) },
                Handler(callbackThread.looper),
            )
            surface = Surface(surfaceTexture)

            extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
            } catch (t: Throwable) {
                throw SourceError.IoFailed("Cannot open $uri", t)
            }

            val trackIndex = findVideoTrack(extractor)
            if (trackIndex < 0) throw SourceError.UnsupportedMedia("No video track in $uri")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            durationUs = format.optLong(MediaFormat.KEY_DURATION, 0L)
            var sourceWidth = format.optInt(MediaFormat.KEY_WIDTH, configuration.width)
            var sourceHeight = format.optInt(MediaFormat.KEY_HEIGHT, configuration.height)
            preparedWidth = sourceWidth
            preparedHeight = sourceHeight
            val mime = format.optStringOrNull(MediaFormat.KEY_MIME)
                ?: throw SourceError.UnsupportedMedia("Video track has no MIME type")

            codec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (t: Throwable) {
                throw SourceError.UnsupportedMedia("No decoder for $mime", t)
            }
            try {
                codec.configure(format, surface, null, 0)
                codec.start()
            } catch (t: Throwable) {
                throw SourceError.DecoderInitFailed("Cannot configure decoder for $mime", t)
            }

            listener.onPrepared(durationUs, sourceWidth, sourceHeight)
            playing.set(true)

            val bufferInfo = MediaCodec.BufferInfo()
            val transform = FloatArray(16)
            var inputEosQueued = false
            var anchorPtsUs = -1L
            var anchorRealtimeNs = 0L
            var lastRenderedPtsUs = -1L
            var needsResync = false
            var lastPositionReportNanos = 0L

            while (running.get()) {
                var command = commands.poll()
                while (command != null) {
                    when (command) {
                        Command.Pause -> {
                            playing.set(false)
                            needsResync = true
                        }
                        Command.Resume -> {
                            if (needsResync) {
                                seekInternal(extractor, codec, positionUs)
                                inputEosQueued = false
                                anchorPtsUs = -1L
                                lastRenderedPtsUs = -1L
                                needsResync = false
                            }
                            playing.set(true)
                        }
                        is Command.Seek -> {
                            seekInternal(extractor, codec, command.positionUs)
                            inputEosQueued = false
                            anchorPtsUs = -1L
                            lastRenderedPtsUs = -1L
                            positionUs = command.positionUs
                            listener.onPositionChanged(positionUs)
                        }
                        is Command.Loop -> looping = command.enabled
                        Command.Stop -> running.set(false)
                    }
                    command = commands.poll()
                }
                if (!running.get()) break

                if (!playing.get()) {
                    Thread.sleep(5L)
                    continue
                }

                if (!inputEosQueued) {
                    val inputIndex = codec.dequeueInputBuffer(0L)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer == null) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, 0)
                        } else {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputEosQueued = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
                when {
                    outputIndex >= 0 -> {
                        val endOfStream =
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        val hasFrame = bufferInfo.size > 0 && bufferInfo.presentationTimeUs >= 0
                        val render = hasFrame && shouldRender(bufferInfo.presentationTimeUs, lastRenderedPtsUs)

                        if (render) {
                            lastRenderedPtsUs = bufferInfo.presentationTimeUs
                            if (anchorPtsUs < 0L) {
                                anchorPtsUs = bufferInfo.presentationTimeUs
                                anchorRealtimeNs = System.nanoTime()
                            }
                            val targetNs = anchorRealtimeNs +
                                (bufferInfo.presentationTimeUs - anchorPtsUs) * 1_000L
                            codec.releaseOutputBuffer(outputIndex, maxOf(targetNs, System.nanoTime()))
                            positionUs = bufferInfo.presentationTimeUs
                        } else {
                            codec.releaseOutputBuffer(outputIndex, false)
                        }

                        if (endOfStream) {
                            if (looping) {
                                seekInternal(extractor, codec, 0L)
                                inputEosQueued = false
                                anchorPtsUs = -1L
                                lastRenderedPtsUs = -1L
                                positionUs = 0L
                            } else {
                                playing.set(false)
                                listener.onCompleted()
                            }
                        }
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sourceWidth = outputFormat.optInt(MediaFormat.KEY_WIDTH, sourceWidth)
                        sourceHeight = outputFormat.optInt(MediaFormat.KEY_HEIGHT, sourceHeight)
                        preparedWidth = sourceWidth
                        preparedHeight = sourceHeight
                        listener.onPrepared(durationUs, sourceWidth, sourceHeight)
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                }

                if (frameAvailable.compareAndSet(true, false)) {
                    surfaceTexture.updateTexImage()
                    surfaceTexture.getTransformMatrix(transform)
                    val timestampNanos = surfaceTexture.timestamp
                    if (timestampNanos > 0L) {
                        producer.emitExternal(
                            texMatrix = transform,
                            sourceWidth = sourceWidth,
                            sourceHeight = sourceHeight,
                            timestampNanos = timestampNanos,
                            mediaTimestampUs = timestampNanos / 1_000L,
                        )
                        val now = System.nanoTime()
                        if (now - lastPositionReportNanos > 200_000_000L) {
                            lastPositionReportNanos = now
                            listener.onPositionChanged(positionUs)
                        }
                    }
                }
            }
        } catch (error: SourceError) {
            listener.onError(error)
        } catch (throwable: Throwable) {
            listener.onError(SourceError.DecodeFailed("Video pipeline failed: ${throwable.message}", throwable))
        } finally {
            playing.set(false)
            try {
                codec?.stop()
            } catch (ignored: Throwable) {
            }
            try {
                codec?.release()
            } catch (ignored: Throwable) {
            }
            try {
                extractor?.release()
            } catch (ignored: Throwable) {
            }
            try {
                surface?.release()
            } catch (ignored: Throwable) {
            }
            try {
                surfaceTexture?.release()
            } catch (ignored: Throwable) {
            }
            callbackThread?.quitSafely()
            producer.release()
            running.set(false)
        }
    }

    private fun shouldRender(presentationTimeUs: Long, lastRenderedPtsUs: Long): Boolean {
        if (lastRenderedPtsUs < 0L) return true
        return presentationTimeUs - lastRenderedPtsUs >= configuration.frameIntervalUs
    }

    private fun seekInternal(extractor: MediaExtractor, codec: MediaCodec, positionUs: Long) {
        extractor.seekTo(positionUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        codec.flush()
        this.positionUs = positionUs
    }

    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.optStringOrNull(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return index
        }
        return -1
    }
}
