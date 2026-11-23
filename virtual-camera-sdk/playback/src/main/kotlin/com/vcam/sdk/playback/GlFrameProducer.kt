package com.vcam.sdk.playback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.format.FrameFormat
import com.vcam.sdk.format.YuvConverter
import com.vcam.sdk.frames.Frame
import com.vcam.sdk.frames.FrameBufferPool
import java.nio.ByteBuffer

/**
 * Turns pixels into [Frame]s at the configured output size and format.
 *
 * Owns the EGL context, the compositor and the buffer pools. Every method must be called
 * on the same thread — the one that created it — because EGL contexts are thread affine.
 *
 * Memory ceiling: `bufferCount` RGBA read-back buffers plus `bufferCount` output buffers.
 * When the consumer has not released frames the pool runs dry and [emit] returns null;
 * the caller drops the frame instead of allocating.
 */
class GlFrameProducer(
    private val configuration: FrameConfiguration,
    bufferCount: Int = 4,
    private val onFrame: (Frame) -> Unit,
) {
    private val compositor = GlFrameCompositor()
    private val rgbaPool = FrameBufferPool(configuration.width * configuration.height * 4, bufferCount)
    private val outputPool = FrameBufferPool(configuration.frameSizeBytes, bufferCount)

    private var imageTexture = 0
    private var sequence = 0L

    private var rotationTarget: Bitmap? = null
    private var rotationCanvas: Canvas? = null
    private val rotationMatrix = Matrix()
    private val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    private var droppedFrames = 0L

    val dropped: Long get() = droppedFrames

    fun initialize() {
        compositor.initialize()
        compositor.setOutputSize(configuration.width, configuration.height)
        imageTexture = compositor.createImageTexture()
    }

    /** Uploads a bitmap (rotating it if the device reports a rotation) and emits one frame. */
    fun emitBitmap(bitmap: Bitmap, rotationDegrees: Int, timestampNanos: Long): Boolean {
        val upload = if (rotationDegrees % 360 == 0) bitmap else rotate(bitmap, rotationDegrees)
        compositor.uploadBitmap(imageTexture, upload)
        compositor.draw2D(
            textureId = imageTexture,
            sourceWidth = upload.width,
            sourceHeight = upload.height,
            scaleMode = configuration.scaleMode,
        )
        return readBackAndEmit(timestampNanos, mediaTimestampUs = 0L)
    }

    /** Renders the current external (decoder) frame and emits it. */
    fun emitExternal(
        texMatrix: FloatArray,
        sourceWidth: Int,
        sourceHeight: Int,
        timestampNanos: Long,
        mediaTimestampUs: Long,
    ): Boolean {
        if (sourceWidth <= 0 || sourceHeight <= 0) return false
        compositor.drawExternal(
            textureId = externalTextureId,
            texMatrix = texMatrix,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            scaleMode = configuration.scaleMode,
        )
        return readBackAndEmit(timestampNanos, mediaTimestampUs)
    }

    /** Set once [createExternalTexture] has been called. */
    @Volatile
    var externalTextureId: Int = 0
        private set

    fun initializeExternal() {
        externalTextureId = compositor.createExternalTexture()
    }

    private fun readBackAndEmit(timestampNanos: Long, mediaTimestampUs: Long): Boolean {
        val rgba = rgbaPool.acquire() ?: run {
            droppedFrames++
            return false
        }
        compositor.readPixels(rgba)

        if (configuration.format == FrameFormat.RGBA_8888) {
            val frame = Frame(
                data = rgba,
                width = configuration.width,
                height = configuration.height,
                format = FrameFormat.RGBA_8888,
                timestampNanos = timestampNanos,
                mediaTimestampUs = mediaTimestampUs,
                sequenceNumber = sequence++,
                pool = rgbaPool,
            )
            onFrame(frame)
            return true
        }

        val output = outputPool.acquire() ?: run {
            rgbaPool.recycle(rgba)
            droppedFrames++
            return false
        }
        YuvConverter.convert(
            src = rgba,
            dst = output,
            width = configuration.width,
            height = configuration.height,
            format = configuration.format,
        )
        rgbaPool.recycle(rgba)
        val frame = Frame(
            data = output,
            width = configuration.width,
            height = configuration.height,
            format = configuration.format,
            timestampNanos = timestampNanos,
            mediaTimestampUs = mediaTimestampUs,
            sequenceNumber = sequence++,
            pool = outputPool,
        )
        onFrame(frame)
        return true
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val swap = degrees % 180 != 0
        val width = if (swap) bitmap.height else bitmap.width
        val height = if (swap) bitmap.width else bitmap.height

        val target = rotationTarget
        val reusable = target != null &&
            target.width == width &&
            target.height == height &&
            !target.isRecycled
        val destination = if (reusable) target!! else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { rotationTarget = it }
        }

        val canvas = rotationCanvas ?: Canvas(destination).also { rotationCanvas = it }
        canvas.setBitmap(destination)
        destination.eraseColor(0)
        rotationMatrix.reset()
        rotationMatrix.postRotate(degrees.toFloat())
        rotationMatrix.postTranslate(if (swap) height.toFloat() else 0f, if (swap) width.toFloat() else 0f)
        canvas.drawBitmap(bitmap, rotationMatrix, drawPaint)
        return destination
    }

    fun release() {
        compositor.release()
        rgbaPool.releaseAll()
        outputPool.releaseAll()
        rotationTarget?.recycle()
        rotationTarget = null
        rotationCanvas = null
        externalTextureId = 0
    }

    /** Pool pressure, for diagnostics. */
    fun availableBuffers(): Int = rgbaPool.available + outputPool.available

    /** Exposed for tests: current configuration. */
    fun configuration(): FrameConfiguration = configuration

    /** Direct read-back buffer acquisition, used by the recorder path. */
    internal fun acquireScratchBuffer(): ByteBuffer? = rgbaPool.acquire()

    internal fun releaseScratchBuffer(buffer: ByteBuffer) = rgbaPool.recycle(buffer)
}
