package com.vcam.sdk.frames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameBufferPoolTest {

    @Test
    fun `pool hands out at most capacity buffers`() {
        val pool = FrameBufferPool(frameSizeBytes = 32, capacity = 2)
        assertEquals(2, pool.available)

        val first = pool.acquire()
        val second = pool.acquire()
        assertNotNull(first)
        assertNotNull(second)
        assertNull(pool.acquire())
        assertEquals(0, pool.available)
    }

    @Test
    fun `recycled buffers are reusable`() {
        val pool = FrameBufferPool(frameSizeBytes = 32, capacity = 1)
        val buffer = pool.acquire()
        assertNotNull(buffer)
        assertNull(pool.acquire())

        pool.recycle(buffer!!)
        assertEquals(1, pool.available)
        assertNotNull(pool.acquire())
    }

    @Test
    fun `release returns the buffer to the pool exactly once`() {
        val pool = FrameBufferPool(frameSizeBytes = 16, capacity = 1)
        val buffer = pool.acquire()!!
        val frame = Frame(
            data = buffer,
            width = 2,
            height = 2,
            format = com.vcam.sdk.format.FrameFormat.RGBA_8888,
            timestampNanos = 1L,
            mediaTimestampUs = 0L,
            sequenceNumber = 1L,
            pool = pool,
        )

        frame.release()
        frame.release()

        assertEquals(1, pool.available)
        assertTrue(frame.isReleased)
    }

    @Test
    fun `frame size follows the configuration`() {
        val pool = FrameBufferPool.forConfiguration(
            format = com.vcam.sdk.format.FrameFormat.RGBA_8888,
            width = 4,
            height = 2,
        )
        assertEquals(4 * 2 * 4, pool.frameSize)
    }
}
