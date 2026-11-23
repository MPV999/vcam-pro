package com.vcam.sdk.frames

import com.vcam.sdk.format.FrameFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class FrameDispatcherTest {

    private fun frame(pool: FrameBufferPool, sequence: Long): Frame = Frame(
        data = pool.acquire() ?: error("pool exhausted in test"),
        width = 2,
        height = 2,
        format = FrameFormat.RGBA_8888,
        timestampNanos = sequence * 1_000_000L,
        mediaTimestampUs = sequence * 1_000L,
        sequenceNumber = sequence,
        pool = pool,
    )

    @Test
    fun `slow consumer causes drop-oldest instead of unbounded growth`() = runTest {
        val pool = FrameBufferPool(frameSizeBytes = 16, capacity = 8)
        val received = mutableListOf<Long>()
        val dispatcher = FrameDispatcher(this) {
            FrameConsumer { frame ->
                received += frame.sequenceNumber
                frame.release()
            }
        }
        dispatcher.start()

        // Nothing is drained yet: only one frame can be pending, the rest are dropped.
        repeat(5) { index -> dispatcher.submit(frame(pool, index.toLong())) }
        val statsBeforeDrain = dispatcher.stats()
        assertEquals(4, statsBeforeDrain.dropped)
        assertEquals(0, statsBeforeDrain.delivered)

        advanceUntilIdle()
        assertEquals(1, received.size)
        assertEquals(4L, received.first())

        dispatcher.stop()
    }

    @Test
    fun `null consumer releases frames instead of leaking them`() = runTest {
        val pool = FrameBufferPool(frameSizeBytes = 16, capacity = 4)
        val dispatcher = FrameDispatcher(this) { null }
        dispatcher.start()

        dispatcher.submit(frame(pool, 1))
        advanceUntilIdle()

        assertEquals(4, pool.available)
        assertTrue(dispatcher.stats().dropped >= 1)

        dispatcher.stop()
    }

    @Test
    fun `consumer exceptions do not kill the pipeline`() = runTest {
        val pool = FrameBufferPool(frameSizeBytes = 16, capacity = 4)
        val calls = AtomicInteger(0)
        val dispatcher = FrameDispatcher(this) {
            FrameConsumer {
                calls.incrementAndGet()
                throw IllegalStateException("boom")
            }
        }
        dispatcher.start()

        dispatcher.submit(frame(pool, 1))
        advanceUntilIdle()
        dispatcher.submit(frame(pool, 2))
        advanceUntilIdle()

        assertEquals(2, calls.get())
        assertEquals(4, pool.available)

        dispatcher.stop()
    }

    @Test
    fun `clear drops the pending frame`() = runTest {
        val pool = FrameBufferPool(frameSizeBytes = 16, capacity = 4)
        val received = AtomicInteger(0)
        val dispatcher = FrameDispatcher(this) {
            FrameConsumer {
                received.incrementAndGet()
                it.release()
            }
        }
        dispatcher.start()

        dispatcher.submit(frame(pool, 1))
        dispatcher.clear()
        advanceUntilIdle()

        assertEquals(0, received.get())
        assertEquals(4, pool.available)

        dispatcher.stop()
    }
}
