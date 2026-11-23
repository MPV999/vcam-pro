package com.vcam.sdk.api

import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.frames.FrameConsumer
import kotlinx.coroutines.flow.StateFlow

/**
 * A frame provider: a physical camera, a video file or a still image.
 *
 * Sources are single-use: create, [open], [start], [stop], [close]. Re-open a closed
 * source by creating a new instance — that keeps teardown deterministic and avoids the
 * stale-codec bugs that come from resurrecting a released decoder.
 *
 * Threading: all suspend functions may be called from any dispatcher. Frames are
 * delivered on the source's own pipeline thread, never on the main thread.
 */
interface CameraSource {

    /** Stable identifier, unique per underlying media (e.g. `video:content://...`). */
    val id: String

    /** Label for UI, e.g. the file name. */
    val displayName: String

    val kind: SourceKind

    val state: StateFlow<SourceState>

    val capabilities: SourceCapabilities

    /** Nominal frame size of the decoded source, 0 until opened. */
    val sourceWidth: Int

    val sourceHeight: Int

    /** Duration of the media in microseconds, 0 for live/static sources. */
    val durationUs: Long

    /**
     * Prepares the source. Must be called before [start].
     *
     * @throws SourceError when the media, the configuration or the camera cannot be used.
     */
    suspend fun open(configuration: FrameConfiguration)

    /** Starts (or resumes) frame delivery. */
    suspend fun start()

    /** Stops frame delivery but keeps the source open. */
    suspend fun stop()

    /** Releases every native resource. Idempotent. */
    suspend fun close()

    /** Consumer for delivered frames. `null` detaches and drops frames. */
    fun setFrameConsumer(consumer: FrameConsumer?)

    /** Playback control for seekable media; `null` for live sources. */
    fun playbackController(): PlaybackController? = null
}
