package com.vcam.sdk.source

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.vcam.sdk.api.PlaybackController
import com.vcam.sdk.api.PlaybackState
import com.vcam.sdk.api.SourceCapabilities
import com.vcam.sdk.api.SourceError
import com.vcam.sdk.api.SourceKind
import com.vcam.sdk.api.SourceState
import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.frames.Frame
import com.vcam.sdk.playback.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Plays a video file as a frame source.
 *
 * Decoding is streamed by [VideoFrameDecoder]; this class only owns the state machine and
 * the transport API. Pause/seek/resume/loop all go through the decoder's command queue, so
 * they are safe to call from the UI thread while decoding runs on its own thread.
 */
class VideoFileSource(
    private val context: Context,
    private val uri: Uri,
    override val displayName: String = uri.lastPathSegment ?: "video",
) : BaseCameraSource(), PlaybackController {

    override val id: String = "video:$uri"

    override val kind: SourceKind = SourceKind.VIDEO_FILE

    override val capabilities: SourceCapabilities = SourceCapabilities.SEEKABLE_VIDEO

    private val _playback = MutableStateFlow(PlaybackState())
    override val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private val decoderLock = Any()
    private var decoder: VideoFrameDecoder? = null

    private val looping = AtomicBoolean(false)

    override suspend fun open(configuration: FrameConfiguration) {
        beginSession(configuration)
        try {
            val metadata = withContext(Dispatchers.IO) { readMetadata(uri) }
            sourceWidth = metadata.width
            sourceHeight = metadata.height
            durationUs = metadata.durationUs
            _playback.value = PlaybackState(
                isPlaying = false,
                positionUs = 0,
                durationUs = durationUs,
                looping = looping.get(),
            )
            transition(SourceState.Ready)
        } catch (error: SourceError) {
            failWith(error)
            throw error
        } catch (throwable: Throwable) {
            val error = SourceError.UnsupportedMedia("Cannot read $uri: ${throwable.message}", throwable)
            failWith(error)
            throw error
        }
    }

    override suspend fun start() {
        if (disposed) throw SourceError.Internal("Source is closed")
        val existing = synchronized(decoderLock) { decoder }
        if (existing == null) {
            createDecoder(startPositionUs = _playback.value.positionUs)
        } else {
            existing.resume()
        }
        transition(SourceState.Running)
        _playback.value = _playback.value.copy(isPlaying = true, ended = false)
    }

    override suspend fun stop() {
        synchronized(decoderLock) { decoder }?.pause()
        if (state.value !is SourceState.Closed) {
            transition(SourceState.Paused)
        }
        _playback.value = _playback.value.copy(isPlaying = false)
    }

    override suspend fun close() {
        if (disposed) return
        disposed = true
        val current = synchronized(decoderLock) {
            val value = decoder
            decoder = null
            value
        }
        withContext(Dispatchers.Default) { current?.stop() }
        endSession()
        transition(SourceState.Closed)
        disposeScope()
    }

    override fun playbackController(): PlaybackController = this

    override suspend fun play() {
        start()
    }

    override suspend fun pause() {
        stop()
    }

    override suspend fun seekTo(positionUs: Long) {
        val target = positionUs.coerceIn(0L, maxOf(0L, durationUs))
        synchronized(decoderLock) { decoder }?.seekTo(target)
        _playback.value = _playback.value.copy(positionUs = target, ended = false)
    }

    override suspend fun setLooping(looping: Boolean) {
        this.looping.set(looping)
        synchronized(decoderLock) { decoder }?.setLooping(looping)
        _playback.value = _playback.value.copy(looping = looping)
    }

    private fun createDecoder(startPositionUs: Long) {
        val newDecoder = VideoFrameDecoder(
            context = context,
            uri = uri,
            configuration = configuration,
            bufferCount = 4,
            listener = object : VideoFrameDecoder.Listener {
                override fun onPrepared(durationUs: Long, width: Int, height: Int) {
                    if (durationUs > 0) this@VideoFileSource.durationUs = durationUs
                    if (width > 0) sourceWidth = width
                    if (height > 0) sourceHeight = height
                    _playback.value = _playback.value.copy(durationUs = this@VideoFileSource.durationUs)
                }

                override fun onFrame(frame: Frame) {
                    emit(frame)
                }

                override fun onCompleted() {
                    transition(SourceState.Completed)
                    _playback.value = _playback.value.copy(
                        isPlaying = false,
                        ended = true,
                        positionUs = durationUs,
                    )
                }

                override fun onError(error: SourceError) {
                    failWith(error)
                }

                override fun onPositionChanged(positionUs: Long) {
                    _playback.value = _playback.value.copy(positionUs = positionUs)
                }
            },
        )
        synchronized(decoderLock) { decoder = newDecoder }
        newDecoder.setLooping(looping.get())
        newDecoder.start()
        if (startPositionUs > 0L) newDecoder.seekTo(startPositionUs)
    }

    private fun readMetadata(uri: Uri): Metadata {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            if (!hasVideo || width <= 0 || height <= 0) {
                throw SourceError.UnsupportedMedia("No playable video track in $uri")
            }
            val swapped = rotation == 90 || rotation == 270
            return Metadata(
                width = if (swapped) height else width,
                height = if (swapped) width else height,
                durationUs = durationMs * 1000L,
            )
        } catch (error: SourceError) {
            throw error
        } catch (throwable: Throwable) {
            throw SourceError.UnsupportedMedia("Cannot read metadata of $uri", throwable)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private data class Metadata(val width: Int, val height: Int, val durationUs: Long)
}
