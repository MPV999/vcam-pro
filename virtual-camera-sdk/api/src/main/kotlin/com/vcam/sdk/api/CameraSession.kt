package com.vcam.sdk.api

import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.frames.FrameConsumer
import com.vcam.sdk.frames.FrameStats
import kotlinx.coroutines.flow.StateFlow

/** State of a [CameraSession]. */
sealed interface SessionState {
    data object Idle : SessionState
    data object Starting : SessionState
    data class Streaming(val sourceKind: SourceKind) : SessionState
    data object Stopped : SessionState
    data class Failed(val error: SourceError) : SessionState
    data object Closed : SessionState
}

/**
 * A running virtual camera: one active [CameraSource] plus one [FrameConsumer].
 *
 * The session owns source lifecycle. [useSource] swaps sources safely — the previous
 * source is stopped and released before the new one is opened, and rapid repeated calls
 * cannot interleave because every mutation is serialised.
 */
interface CameraSession : AutoCloseable {

    val state: StateFlow<SessionState>

    val configuration: StateFlow<FrameConfiguration>

    val activeSource: StateFlow<CameraSource?>

    val frameStats: StateFlow<FrameStats>

    /** Attach/replace the consumer. Frames already buffered are dropped. */
    fun setConsumer(consumer: FrameConsumer?)

    /**
     * Switches the active source and starts it.
     * @return success, or the typed failure that prevented the switch.
     */
    suspend fun useSource(source: CameraSource): Result<Unit>

    /** Applies a new frame configuration by re-opening the active source. */
    suspend fun updateConfiguration(configuration: FrameConfiguration): Result<Unit>

    suspend fun start(): Result<Unit>

    suspend fun stop(): Result<Unit>

    /** Transport controls of the active source, if it has any. */
    fun playback(): PlaybackController?

    /** Releases the active source and the session. Idempotent. */
    override fun close()
}

/** SDK wide configuration. */
data class VirtualCameraConfig(
    val configuration: FrameConfiguration = FrameConfiguration.P720,
    /** Buffers in flight; 3-4 is right for 720p, 2 for 4K. */
    val bufferCount: Int = 4,
    /** Start streaming as soon as a source is attached. */
    val autoStart: Boolean = true,
)
