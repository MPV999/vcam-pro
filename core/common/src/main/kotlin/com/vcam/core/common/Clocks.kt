package com.vcam.core.common

import java.util.concurrent.atomic.AtomicLong

/**
 * Monotonic clock used for frame timestamps. `System.nanoTime()` is monotonic and
 * unaffected by wall clock changes, which is what a frame pipeline needs.
 */
object MonotonicClock {
    fun nowNanos(): Long = System.nanoTime()

    fun nowUs(): Long = System.nanoTime() / 1000L
}

/**
 * Measures real delivered frame rate over a sliding one second window.
 * Thread safe; every method may be called from any thread.
 */
class FrameRateMeter(private val windowNanos: Long = 1_000_000_000L) {

    private val count = AtomicLong(0)
    private val windowStart = AtomicLong(0)
    private val currentFps = AtomicLong(0)

    fun onFrame() {
        val now = System.nanoTime()
        val start = windowStart.get()
        if (start == 0L) {
            windowStart.compareAndSet(0L, now)
            count.incrementAndGet()
            return
        }
        val total = count.incrementAndGet()
        val elapsed = now - start
        if (elapsed >= windowNanos) {
            currentFps.set((total * 1_000_000_000L) / elapsed)
            count.set(0)
            windowStart.set(now)
        }
    }

    fun fps(): Double = currentFps.get().toDouble()

    fun reset() {
        count.set(0)
        windowStart.set(0)
        currentFps.set(0)
    }
}
