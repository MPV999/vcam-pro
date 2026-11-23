package com.vcam.core.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Recording resolution requested by the user. Falls back automatically when unsupported. */
enum class CaptureQuality(val label: String) {
    SD("480p"),
    HD("720p"),
    FHD("1080p"),
    UHD("4K"),
    ;

    fun toCameraXQuality(): Quality = when (this) {
        SD -> Quality.SD
        HD -> Quality.HD
        FHD -> Quality.FHD
        UHD -> Quality.UHD
    }
}

data class CameraStudioCapabilities(
    val hasFrontCamera: Boolean = false,
    val hasBackCamera: Boolean = false,
    val maxZoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val hasFlash: Boolean = false,
    val exposureRange: IntRange = 0..0,
    val supportsExposureCompensation: Boolean = false,
)

/**
 * CameraX wrapper for the Camera Studio screen: preview, still capture and video capture.
 *
 * Everything the UI shows about the camera (zoom range, flash, exposure range) is queried
 * from the bound camera, so low-end devices that lack a feature simply report it missing
 * instead of offering a dead control.
 */
class CameraStudioController(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    private var recording: Recording? = null

    @Volatile
    private var recordingFile: File? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    val hasAudioPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun ensureProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
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
    }

    /** Binds preview + still capture + video capture to the lifecycle. */
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int,
        quality: CaptureQuality,
    ): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            val cameraProvider = ensureProvider()
            provider = cameraProvider

            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            if (!cameraProvider.hasCamera(selector)) {
                return@withContext Result.failure(
                    IllegalStateException("No camera for lens facing $lensFacing"),
                )
            }

            val previewUseCase = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val captureUseCase = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        quality.toCameraXQuality(),
                        FallbackStrategy.lowerQualityOrHigherThan(quality.toCameraXQuality()),
                    ),
                )
                .build()
            val videoUseCase = VideoCapture.withOutput(recorder)

            cameraProvider.unbindAll()
            val boundCamera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                previewUseCase,
                captureUseCase,
                videoUseCase,
            )

            preview = previewUseCase
            imageCapture = captureUseCase
            videoCapture = videoUseCase
            camera = boundCamera
            Result.success(Unit)
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    suspend fun takePhoto(target: File): Result<File> = try {
        Result.success(takePhotoOrThrow(target))
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    private suspend fun takePhotoOrThrow(target: File): File =
        suspendCancellableCoroutine { continuation ->
            val capture = imageCapture
            if (capture == null) {
                continuation.resumeWithException(IllegalStateException("Camera is not bound"))
                return@suspendCancellableCoroutine
            }
            val options = ImageCapture.OutputFileOptions.Builder(target).build()
            capture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (continuation.isActive) continuation.resume(target)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                },
            )
        }

    fun startRecording(target: File, withAudio: Boolean): Result<Unit> {
        if (isRecording) return Result.failure(IllegalStateException("Already recording"))
        val capture = videoCapture
            ?: return Result.failure(IllegalStateException("Camera is not bound"))

        return try {
            val options = FileOutputOptions.Builder(target).build()
            var pending = capture.output.prepareRecording(context, options)
            if (withAudio && hasAudioPermission) {
                pending = pending.withAudioEnabled()
            }
            recordingFile = target
            recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    isRecording = false
                }
            }
            isRecording = true
            Result.success(Unit)
        } catch (throwable: Throwable) {
            recording = null
            recordingFile = null
            isRecording = false
            Result.failure(throwable)
        }
    }

    /** Stops the recording and returns the finished file once CameraX has finalised it. */
    suspend fun stopRecording(): Result<File> {
        val active = recording ?: return Result.failure(IllegalStateException("Not recording"))
        val target = recordingFile
        recording = null
        recordingFile = null
        isRecording = false
        return try {
            active.stop()
            active.close()
            withContext(Dispatchers.IO) {
                // CameraX finalises asynchronously; wait briefly for the file to be flushed.
                var waited = 0L
                while (waited < 5_000L) {
                    if (target != null && target.exists() && target.length() > 0L) break
                    Thread.sleep(50L)
                    waited += 50L
                }
                if (target != null && target.exists()) {
                    Result.success(target)
                } else {
                    Result.failure(IllegalStateException("Recording file was not written"))
                }
            }
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    fun setZoomRatio(ratio: Float) {
        val control = camera?.cameraControl ?: return
        val info = camera?.cameraInfo ?: return
        val max = info.zoomState.value?.maxZoomRatio ?: 1f
        control.setZoomRatio(ratio.coerceIn(1f, max))
    }

    fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun setExposureCompensation(index: Int) {
        camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    fun currentZoomRatio(): Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    fun capabilities(): CameraStudioCapabilities {
        val info = camera?.cameraInfo
        val zoomState = info?.zoomState?.value
        val exposure = info?.exposureState
        val cameraProvider = provider
        val hasFront = cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true
        val hasBack = cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) == true

        val range = exposure?.exposureCompensationRange
        return CameraStudioCapabilities(
            hasFrontCamera = hasFront,
            hasBackCamera = hasBack,
            maxZoomRatio = zoomState?.maxZoomRatio ?: 1f,
            minZoomRatio = zoomState?.minZoomRatio ?: 1f,
            hasFlash = info?.hasFlashUnit() == true,
            exposureRange = if (range != null) range.lower..range.upper else 0..0,
            supportsExposureCompensation = exposure?.isExposureCompensationSupported == true,
        )
    }

    fun unbind() {
        runCatching { recording?.close() }
        recording = null
        recordingFile = null
        isRecording = false
        runCatching { provider?.unbindAll() }
        camera = null
        preview = null
        imageCapture = null
        videoCapture = null
    }
}
