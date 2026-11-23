package com.vcam.sdk.format

import java.nio.ByteBuffer

/**
 * CPU conversion between the RGBA buffer produced by the GL read-back and the YUV
 * layouts consumers of a virtual camera expect.
 *
 * All functions expect `src` positioned at 0 with `width * height * 4` readable bytes and
 * write from position 0 of `dst`. Colour maths is BT.601 limited range ("TV range"),
 * matching what Android camera pipelines emit.
 *
 * Cost: one pass over the RGBA buffer per frame. At 720p30 that is roughly 110 MB/s of
 * reads, so RGBA output should be preferred unless the consumer really needs YUV.
 */
object YuvConverter {

    fun rgbaToI420(
        src: ByteBuffer,
        dst: ByteBuffer,
        width: Int,
        height: Int,
    ) {
        require(width % 2 == 0 && height % 2 == 0) { "dimensions must be even" }
        require(dst.capacity() >= width * height * 3 / 2) { "dst too small for I420" }

        src.position(0)
        val ySize = width * height
        val uvSize = ySize / 4
        dst.position(0)
        dst.limit(width * height * 3 / 2)

        val yStart = 0
        val uStart = ySize
        val vStart = ySize + uvSize

        var yIndex = yStart
        var uIndex = uStart
        var vIndex = vStart
        val rowBytes = width * 4
        val row = ByteArray(rowBytes)

        for (rowIndex in 0 until height) {
            src.position(rowIndex * rowBytes)
            src.get(row, 0, rowBytes)
            var x = 0
            while (x < width) {
                val p = x * 4
                val r = row[p].toInt() and 0xFF
                val g = row[p + 1].toInt() and 0xFF
                val b = row[p + 2].toInt() and 0xFF

                dst.put(yIndex++, luma(r, g, b))

                // Chroma sampled from the top-left pixel of every 2x2 block.
                if (rowIndex % 2 == 0 && x % 2 == 0) {
                    dst.put(uIndex++, chromaU(r, g, b))
                    dst.put(vIndex++, chromaV(r, g, b))
                }
                x += 1
            }
        }
        dst.position(0)
    }

    fun rgbaToNv21(
        src: ByteBuffer,
        dst: ByteBuffer,
        width: Int,
        height: Int,
    ) {
        require(width % 2 == 0 && height % 2 == 0) { "dimensions must be even" }
        require(dst.capacity() >= width * height * 3 / 2) { "dst too small for NV21" }

        src.position(0)
        val ySize = width * height
        dst.position(0)
        dst.limit(width * height * 3 / 2)

        var yIndex = 0
        var vuIndex = ySize
        val rowBytes = width * 4
        val row = ByteArray(rowBytes)

        for (rowIndex in 0 until height) {
            src.position(rowIndex * rowBytes)
            src.get(row, 0, rowBytes)
            var x = 0
            while (x < width) {
                val p = x * 4
                val r = row[p].toInt() and 0xFF
                val g = row[p + 1].toInt() and 0xFF
                val b = row[p + 2].toInt() and 0xFF

                dst.put(yIndex++, luma(r, g, b))
                if (rowIndex % 2 == 0 && x % 2 == 0) {
                    dst.put(vuIndex++, chromaV(r, g, b))
                    dst.put(vuIndex++, chromaU(r, g, b))
                }
                x += 1
            }
        }
        dst.position(0)
    }

    /** Convert according to [format]; RGBA is a plain copy. */
    fun convert(
        src: ByteBuffer,
        dst: ByteBuffer,
        width: Int,
        height: Int,
        format: FrameFormat,
    ) {
        when (format) {
            FrameFormat.RGBA_8888 -> {
                src.position(0)
                src.limit(width * height * 4)
                dst.position(0)
                dst.put(src)
                dst.position(0)
                src.position(0)
            }
            FrameFormat.YUV_420_888 -> rgbaToI420(src, dst, width, height)
            FrameFormat.NV21 -> rgbaToNv21(src, dst, width, height)
        }
    }

    private fun luma(r: Int, g: Int, b: Int): Byte =
        (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()

    private fun chromaU(r: Int, g: Int, b: Int): Byte =
        (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()

    private fun chromaV(r: Int, g: Int, b: Int): Byte =
        (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
}
