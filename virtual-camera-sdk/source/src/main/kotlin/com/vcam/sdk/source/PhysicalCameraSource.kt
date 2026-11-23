package com.vcam.sdk.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.vcam.sdk.api.SourceCapabilities
import com.vcam.sdk.api.SourceError
import com.vcam.sdk.api.SourceKind
import com.vcam.sdk.api.SourceState
import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.playback.GlFrameProducer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Live frames from a physical camera, delivered through CameraX ImageAnalysis.
 *
 * Output is RGBA_8888 straight from CameraX (no YUV conversion), then composited by the
 * same GL path the video source uses so that resolution, aspect and scale mode behave
 * identically no matter which source is active.
 *
 * Backpressure: `STRATEGY_KEEP_ONLY_LATEST` means CameraX drops frames rather than
 * queueing them when the GL pipeline is busy.
 */
class PhysicalCameraSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
) : BaseCameraSource() {

    override val id: String = "camera:$lensFacing"

    override val displayName: String =
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) "Front camera" else "Back camera"

    override val kind: SourceKind = SourceKind.PHYSICAL_CAMERA

    override val capabilities: SourceCapabilities = SourceCapabilities.LIVE_CAMERA

    private val analysisExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vcam-camera-analysis")
    }

    private val bound = AtomicBoolean(false)

    @Volatile
    private var cameraProvider: ProcessCameraProvider? = null

    @Volatile
    private var imageAnalysis: ImageAnalysis? = null

    @Volatile
    private var producer: GlFrameProducer? = null

    @Volatile
    private var bitmapBuffer: Bitmap? = null

    @Volatile
    private var pixelBuffer: ByteBuffer? = null

    override suspend fun open(configuration: FrameConfiguration) {
        beginSession(configuration)
        try {
            ensurePermission()
            val provider = obtainProvider()
            cameraProvider = provider

            analysisExecutor.execute {
                if (disposed) return@execute
                val frameProducer = GlFrameProducer(configuration, bufferCount = 3) { frame -> emit(frame) }
                frameProducer.initialize()
                producer = frameProducer
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(analysisExecutor) { image -> analyze(image) }
            imageAnalysis = analysis

            withContext(Dispatchers.Main) {
                provider.unbindAll()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                if (!provider.hasCamera(selector)) {
                    throw SourceError.CameraUnavailable("No camera for lens facing $lensFacing")
                }
                provider.bindToLifecycle(lifecycleOwner, selector, analysis)
            }
            bound.set(true)
            sourceWidth = configuration.width
            sourceHeight = configuration.height
            transition(SourceState.Ready)
        } catch (error: SourceError) {
            failWith(error)
            throw error
        } catch (throwable: Throwable) {
            val error = SourceError.CameraUnavailable("Cannot open camera: ${throwable.message}", throwable)
            failWith(error)
            throw error
        }
    }

    override suspend fun start() {
        if (disposed) throw SourceError.Internal("Source is closed")
        if (!bound.get()) throw SourceError.CameraUnavailable("Camera is not bound")
        transition(SourceState.Running)
    }

    override suspend fun stop() {
        if (state.value !is SourceState.Closed) transition(SourceState.Paused)
    }

    override suspend fun close() {
        if (disposed) return
        disposed = true
        bound.set(false)
        withContext(Dispatchers.Main) {
            runCatching { imageAnalysis?.clearAnalyzer() }
            runCatching { cameraProvider?.unbindAll() }
        }
        imageAnalysis = null
        cameraProvider = null
        withContext(Dispatchers.Default) {
            analysisExecutor.execute {
                producer?.release()
                producer = null
                bitmapBuffer?.recycle()
                bitmapBuffer = null
                pixelBuffer = null
            }
            analysisExecutor.shutdown()
            runCatching { analysisExecutor.awaitTermination(3, TimeUnit.SECONDS) }
        }
        endSession()
        transition(SourceState.Closed)
        disposeScope()
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (disposed) return
            val frameProducer = producer ?: return
            if (image.format != ImageFormat.YUV_420_888 &&
                image.planes.size == 1 &&
                image.planes[0].pixelStride != 4
            ) {
                return
            }
            val bitmap = ensureBitmap(image.width, image.height) ?: return
            copyInto(bitmap, image)
            frameProducer.emitBitmap(
                bitmap = bitmap,
                rotationDegrees = image.imageInfo.rotationDegrees,
                timestampNanos = System.nanoTime(),
            )
        } catch (throwable: Throwable) {
            if (!disposed) {
                failWith(SourceError.CameraUnavailable("Frame conversion failed: ${throwable.message}", throwable))
            }
        } finally {
            image.close()
        }
    }

    private fun ensureBitmap(width: Int, height: Int): Bitmap? {
        val current = bitmapBuffer
        if (current != null && current.width == width && current.height == height && !current.isRecycled) {
            return current
        }
        current?.recycle()
        val created = runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
        bitmapBuffer = created
        return created
    }

    private fun copyInto(bitmap: Bitmap, image: ImageProxy) {
        val plane = image.planes[0]
        val source = plane.buffer
        val rowStride = plane.rowStride
        val packedRowBytes = image.width * 4
        val required = packedRowBytes * image.height

        val target = pixelBuffer?.takeIf { it.capacity() >= required }
            ?: ByteBuffer.allocateDirect(required).also { pixelBuffer = it }
        target.clear()

        if (rowStride == packedRowBytes) {
            source.position(0)
            source.limit(minOf(source.capacity(), required))
            target.put(source)
        } else {
            val row = ByteArray(rowStride)
            for (y in 0 until image.height) {
                val offset = y * rowStride
                if (offset >= source.capacity()) break
                source.position(offset)
                val available = minOf(rowStride, source.remaining())
                source.get(row, 0, available)
                target.put(row, 0, minOf(packedRowBytes, available))
            }
        }
        target.position(0)
        bitmap.copyPixelsFromBuffer(target)
    }

    private fun ensurePermission() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            throw SourceError.PermissionDenied("CAMERA permission not granted")
        }
    }

    private suspend fun obtainProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        continuation.resume(future.get())
                    } catch (throwable: Throwable) {
                        continuation.resumeWithException(throwable)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
            continuation.invokeOnCancellation { runCatching { future.cancel(true) } }
        }
}
