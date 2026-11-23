package com.vcam.sdk.api

import kotlinx.coroutines.flow.StateFlow

/** Observable playback status of a media source. */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionUs: Long = 0,
    val durationUs: Long = 0,
    val looping: Boolean = false,
    /** 0..100, always 100 for local files (no network buffering). */
    val bufferedPercent: Float = 100f,
    val ended: Boolean = false,
)

/**
 * Transport controls for seekable sources (video). Static images expose a controller too
 * so UI code does not need to special-case them, but only pause/resume are meaningful.
 */
interface PlaybackController {
    val playback: StateFlow<PlaybackState>

    suspend fun play()

    suspend fun pause()

    suspend fun seekTo(positionUs: Long)

    suspend fun setLooping(looping: Boolean)

    /** Convenience: seek by a delta, clamped to the media duration. */
    suspend fun seekBy(deltaUs: Long) {
        val state = playback.value
        val target = (state.positionUs + deltaUs).coerceIn(0, maxOf(0, state.durationUs))
        seekTo(target)
    }
}
