package com.vcam.sdk.frames

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One frame handed to a [FrameConsumer].
 *
 * Ownership: the receiver must call [release] when it is done with [data]. Buffers are
 * pooled, so a frame that is never released leaks a buffer and, once the pool is empty,
 * the source starts dropping frames instead of growing memory.
 *
 * [data] is a direct [ByteBuffer] positioned at 0. Row order is top-to-bottom for every
 * [com.vcam.sdk.format.FrameFormat].
 */
class Frame(
    val data: ByteBuffer,
    val width: Int,
    val height: Int,
    val format: com.vcam.sdk.format.FrameFormat,
    /** Monotonic capture time on this device, nanoseconds. */
    val timestampNanos: Long,
    /** Presentation time inside the media, microseconds. 0 for live sources. */
    val mediaTimestampUs: Long,
    /** Monotonic counter, increases by one per delivered frame. */
    val sequenceNumber: Long,
    private val pool: FrameBufferPool?,
) {
    private val released = AtomicBoolean(false)

    val sizeBytes: Int get() = data.capacity()

    val isReleased: Boolean get() = released.get()

    fun release() {
        if (released.compareAndSet(false, true)) {
            pool?.recycle(data)
        }
    }

    /** Copy the pixel data out, for consumers that need to keep a frame. */
    fun copyBytes(): ByteArray {
        val out = ByteArray(data.capacity())
        val duplicate = data.duplicate()
        duplicate.position(0)
        duplicate.get(out)
        return out
    }

    override fun toString(): String =
        "Frame(#$sequenceNumber ${width}x$height ${format.name} ${sizeBytes}B ts=${timestampNanos}ns)"
}
