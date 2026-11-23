package com.vcam.sdk.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FrameConfigurationTest {

    @Test
    fun `frame size in bytes matches the pixel layout`() {
        assertEquals(1280 * 720 * 4, FrameConfiguration.P720.frameSizeBytes)
        assertEquals(1280 * 720 * 3 / 2, FrameConfiguration.P720.copy(format = FrameFormat.NV21).frameSizeBytes)
        assertEquals(1280 * 720 * 3 / 2, FrameConfiguration.P720.copy(format = FrameFormat.YUV_420_888).frameSizeBytes)
    }

    @Test
    fun `frame interval is derived from fps`() {
        assertEquals(33_333L, FrameConfiguration.P720.frameIntervalUs)
        assertEquals(33_333_333L, FrameConfiguration.P720.frameIntervalNanos)
        assertEquals(16_666L, FrameConfiguration.P720.copy(fps = 60).frameIntervalUs)
    }

    @Test
    fun `invalid fps is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { FrameConfiguration(640, 480, 0) }
        assertThrows(IllegalArgumentException::class.java) { FrameConfiguration(640, 480, 121) }
    }

    @Test
    fun `odd dimensions are rejected because yuv needs even planes`() {
        assertThrows(IllegalArgumentException::class.java) { FrameConfiguration(641, 480, 30) }
    }

    @Test
    fun `copy keeps untouched fields`() {
        val copy = FrameConfiguration.P1080.copy(fps = 15)
        assertEquals(1920, copy.width)
        assertEquals(1080, copy.height)
        assertEquals(15, copy.fps)
        assertEquals(ScaleMode.CROP, copy.scaleMode)
    }

    @Test
    fun `aspect ratio is width over height`() {
        assertEquals(16f / 9f, FrameConfiguration.P720.aspectRatio, 0.0001f)
        assertEquals(1f, FrameConfiguration.SQUARE.aspectRatio, 0.0001f)
    }
}
