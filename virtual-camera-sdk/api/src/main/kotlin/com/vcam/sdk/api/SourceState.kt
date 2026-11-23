package com.vcam.sdk.api

/** What kind of imagery a [CameraSource] produces. */
enum class SourceKind {
    PHYSICAL_CAMERA,
    VIDEO_FILE,
    IMAGE_FILE,
}

/** Lifecycle state of a source. */
sealed interface SourceState {
    /** Created but [CameraSource.open] has not been called. */
    data object Idle : SourceState

    /** Opening the underlying camera or decoder. */
    data object Opening : SourceState

    /** Opened and configured, frames not flowing yet. */
    data object Ready : SourceState

    /** Frames are being delivered. */
    data object Running : SourceState

    /** Frames paused; the source keeps its position. */
    data object Paused : SourceState

    /** Media finished (non looping). */
    data object Completed : SourceState

    /** Something went wrong. The source is unusable until re-opened. */
    data class Failed(val error: SourceError) : SourceState

    /** Released. */
    data object Closed : SourceState
}

/** Typed failures, so integrations can react instead of parsing strings. */
sealed class SourceError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Container/codec the platform cannot play. */
    class UnsupportedMedia(detail: String, cause: Throwable? = null) : SourceError(detail, cause)

    /** The requested resolution/fps/format cannot be honoured. */
    class UnsupportedConfiguration(detail: String) : SourceError(detail)

    /** MediaCodec refused to initialise. */
    class DecoderInitFailed(detail: String, cause: Throwable? = null) : SourceError(detail, cause)

    /** Decoding failed mid-stream. */
    class DecodeFailed(detail: String, cause: Throwable? = null) : SourceError(detail, cause)

    /** File could not be read. */
    class IoFailed(detail: String, cause: Throwable? = null) : SourceError(detail, cause)

    /** Camera permission missing or revoked. */
    class PermissionDenied(detail: String) : SourceError(detail)

    /** No usable physical camera. */
    class CameraUnavailable(detail: String, cause: Throwable? = null) : SourceError(detail, cause)

    /** Anything unexpected. */
    class Internal(detail: String, cause: Throwable? = null) : SourceError(detail, cause)
}

/** Static description of what a source can do. */
data class SourceCapabilities(
    val kind: SourceKind,
    val supportsPause: Boolean,
    val supportsSeek: Boolean,
    val supportsLooping: Boolean,
    val isLive: Boolean,
) {
    companion object {
        val LIVE_CAMERA = SourceCapabilities(
            kind = SourceKind.PHYSICAL_CAMERA,
            supportsPause = false,
            supportsSeek = false,
            supportsLooping = false,
            isLive = true,
        )

        val SEEKABLE_VIDEO = SourceCapabilities(
            kind = SourceKind.VIDEO_FILE,
            supportsPause = true,
            supportsSeek = true,
            supportsLooping = true,
            isLive = false,
        )

        val STATIC_IMAGE = SourceCapabilities(
            kind = SourceKind.IMAGE_FILE,
            supportsPause = true,
            supportsSeek = false,
            supportsLooping = true,
            isLive = false,
        )
    }
}
