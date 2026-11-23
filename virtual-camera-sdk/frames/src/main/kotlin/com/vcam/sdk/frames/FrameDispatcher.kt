package com.vcam.sdk.frames

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Counters describing what the pipeline actually did. */
data class FrameStats(
    val delivered: Long = 0,
    val dropped: Long = 0,
    val lastFrameNanos: Long = 0,
) {
    val dropRatio: Float
        get() {
            val total = delivered + dropped
            return if (total == 0L) 0f else dropped.toFloat() / total
        }
}

/**
 * Decouples a producer (decoder / camera) from a consumer.
 *
 * Exactly one frame is buffered. If the consumer is slower than the producer the newer
 * frame replaces the pending one and the older frame is released — a drop-oldest policy
 * with a hard memory ceiling of a single frame.
 */
class FrameDispatcher(
    private val scope: CoroutineScope,
    private val consumerProvider: () -> FrameConsumer?,
) {
    private val pending = AtomicReference<Frame?>(null)
    private val signal = Channel<Unit>(Channel.CONFLATED)

    private val deliveredCount = AtomicLong(0)
    private val droppedCount = AtomicLong(0)
    private val lastFrameNanos = AtomicLong(0)

    @Volatile
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            for (unused in signal) {
                val frame = pending.getAndSet(null) ?: continue
                val consumer = consumerProvider()
                if (consumer == null) {
                    frame.release()
                    droppedCount.incrementAndGet()
                    continue
                }
                try {
                    consumer.onFrame(frame)
                } catch (t: Throwable) {
                    frame.release()
                    droppedCount.incrementAndGet()
                }
                deliveredCount.incrementAndGet()
                lastFrameNanos.set(frame.timestampNanos)
            }
        }
    }

    /** Hand a frame to the pipeline. Takes ownership: the frame is released if dropped. */
    fun submit(frame: Frame) {
        val previous = pending.getAndSet(frame)
        if (previous != null) {
            previous.release()
            droppedCount.incrementAndGet()
        }
        signal.trySend(Unit)
    }

    /** Drop any buffered frame, e.g. when switching sources. */
    fun clear() {
        pending.getAndSet(null)?.release()
    }

    fun stats(): FrameStats = FrameStats(
        delivered = deliveredCount.get(),
        dropped = droppedCount.get(),
        lastFrameNanos = lastFrameNanos.get(),
    )

    fun stop() {
        job?.cancel()
        job = null
        clear()
        signal.close()
    }
}
