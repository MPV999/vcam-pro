package com.vcam.sdk.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class YuvConverterTest {

    private fun solidRgba(width: Int, height: Int, r: Int, g: Int, b: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(width * height * 4)
        repeat(width * height) {
            buffer.put(r.toByte())
            buffer.put(g.toByte())
            buffer.put(b.toByte())
            buffer.put(0xFF.toByte())
        }
        buffer.position(0)
        return buffer
    }

    @Test
    fun `white maps to limited range luma and neutral chroma`() {
        val width = 2
        val height = 2
        val source = solidRgba(width, height, 255, 255, 255)
        val target = ByteBuffer.allocateDirect(width * height * 3 / 2)

        YuvConverter.rgbaToI420(source, target, width, height)

        assertEquals(235, target.get(0).toInt() and 0xFF)
        assertEquals(128, target.get(4).toInt() and 0xFF) // U
        assertEquals(128, target.get(5).toInt() and 0xFF) // V
    }

    @Test
    fun `black maps to luma 16`() {
        val source = solidRgba(2, 2, 0, 0, 0)
        val target = ByteBuffer.allocateDirect(2 * 2 * 3 / 2)

        YuvConverter.rgbaToI420(source, target, 2, 2)

        assertEquals(16, target.get(0).toInt() and 0xFF)
    }

    @Test
    fun `pure red matches bt601 coefficients`() {
        val source = solidRgba(2, 2, 255, 0, 0)
        val target = ByteBuffer.allocateDirect(2 * 2 * 3 / 2)

        YuvConverter.rgbaToI420(source, target, 2, 2)

        assertEquals(82, target.get(0).toInt() and 0xFF) // Y
        assertEquals(90, target.get(4).toInt() and 0xFF) // U
        assertEquals(240, target.get(5).toInt() and 0xFF) // V
    }

    @Test
    fun `nv21 stores V before U`() {
        val source = solidRgba(2, 2, 255, 0, 0)
        val target = ByteBuffer.allocateDirect(2 * 2 * 3 / 2)

        YuvConverter.rgbaToNv21(source, target, 2, 2)

        assertEquals(240, target.get(4).toInt() and 0xFF) // V first
        assertEquals(90, target.get(5).toInt() and 0xFF) // U second
    }

    @Test
    fun `rgba conversion is a byte copy`() {
        val width = 4
        val height = 2
        val source = solidRgba(width, height, 10, 20, 30)
        val target = ByteBuffer.allocateDirect(width * height * 4)

        YuvConverter.convert(source, target, width, height, FrameFormat.RGBA_8888)

        assertEquals(10, target.get(0).toInt() and 0xFF)
        assertEquals(20, target.get(1).toInt() and 0xFF)
        assertEquals(30, target.get(2).toInt() and 0xFF)
    }
}
