package com.vcam.sdk.frames

/**
 * Receives frames from a source.
 *
 * Called on a pipeline thread, never on the main thread. Implementations must be fast
 * and must not block: the source drops frames rather than queueing behind a slow
 * consumer. Every frame must be released (see [Frame.release]).
 */
fun interface FrameConsumer {
    fun onFrame(frame: Frame)
}
