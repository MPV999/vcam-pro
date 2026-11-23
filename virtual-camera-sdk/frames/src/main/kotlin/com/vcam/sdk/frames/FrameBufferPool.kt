package com.vcam.sdk.frames

import com.vcam.sdk.format.FrameFormat
import java.nio.ByteBuffer

/**
 * Fixed-size pool of direct byte buffers.
 *
 * Bounded on purpose: when every buffer is in flight [acquire] returns null and the
 * source drops the frame. That is the backpressure mechanism — memory never grows with
 * the number of undelivered frames.
 */
class FrameBufferPool(
    private val frameSizeBytes: Int,
    val capacity: Int = 4,
) {
    private val free = ArrayDeque<ByteBuffer>(capacity)
    private val lock = Any()

    init {
        require(frameSizeBytes > 0) { "frameSizeBytes must be > 0" }
        require(capacity > 0) { "capacity must be > 0" }
        repeat(capacity) {
            free.addLast(ByteBuffer.allocateDirect(frameSizeBytes))
        }
    }

    val available: Int get() = synchronized(lock) { free.size }

    val frameSize: Int get() = frameSizeBytes

    fun acquire(): ByteBuffer? = synchronized(lock) {
        val buffer = free.removeFirstOrNull() ?: return null
        buffer.clear()
        buffer
    }

    fun recycle(buffer: ByteBuffer) {
        synchronized(lock) {
            if (free.size < capacity) {
                buffer.clear()
                free.addLast(buffer)
            }
        }
    }

    fun releaseAll() {
        synchronized(lock) { free.clear() }
    }

    companion object {
        fun forConfiguration(format: FrameFormat, width: Int, height: Int, capacity: Int = 4) =
            FrameBufferPool(format.bytesFor(width, height), capacity)
    }
}
