package com.vcam.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `bytes are formatted in base 1024`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1024L * 1024L * 3 / 2))
        assertEquals("2.0 GB", formatBytes(2L * 1024L * 1024L * 1024L))
    }

    @Test
    fun `durations use mm ss and h mm ss`() {
        assertEquals("00:00", formatDuration(0))
        assertEquals("01:05", formatDuration(65_000))
        assertEquals("1:00:00", formatDuration(3_600_000))
    }

    @Test
    fun `microsecond durations are converted`() {
        assertEquals("00:05", formatDurationUs(5_000_000))
    }

    @Test
    fun `resolution and fps formatting`() {
        assertEquals("1280x720", formatResolution(1280, 720))
        assertEquals("29.9 fps", formatFps(29.94))
    }

    @Test
    fun `frame rate meter counts frames per second window`() {
        val meter = FrameRateMeter(windowNanos = 0)
        repeat(30) { meter.onFrame() }
        assertEquals(30.0, meter.fps(), 0.001)
        meter.reset()
        assertEquals(0.0, meter.fps(), 0.001)
    }
}
