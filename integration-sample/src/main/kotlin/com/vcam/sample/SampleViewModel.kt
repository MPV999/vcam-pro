package com.vcam.sample

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.vcam.sdk.api.CameraSession
import com.vcam.sdk.api.CameraSource
import com.vcam.sdk.api.PlaybackState
import com.vcam.sdk.api.SessionState
import com.vcam.sdk.api.VirtualCameraConfig
import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.format.FrameFormat
import com.vcam.sdk.frames.Frame
import com.vcam.sdk.frames.FrameConsumer
import com.vcam.sdk.source.VirtualCameraSdk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class SampleUiState(
    val status: String = "idle",
    val sourceLabel: String = "",
    val fps: Double = 0.0,
    val width: Int = 0,
    val height: Int = 0,
    val delivered: Long = 0,
    val dropped: Long = 0,
    val preview: Bitmap? = null,
    val log: List<String> = emptyList(),
    val savedFramePath: String? = null,
    val playback: PlaybackState = PlaybackState(),
)

/**
 * Sample integration: consumes the VCAM SDK exactly the way a third-party app would.
 *
 * - creates a session,
 * - attaches a physical camera, a video file or a still image,
 * - renders the delivered frames,
 * - measures real FPS and resolution,
 * - saves a frame to prove the pixels are genuine,
 * - closes and reopens the session to prove teardown is clean.
 */
class SampleViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val _state = MutableStateFlow(SampleUiState())
    val state: StateFlow<SampleUiState> = _state.asStateFlow()

    private var session: CameraSession? = null
    private var previewBitmap: Bitmap? = null
    private var lastPreviewNanos = 0L
    private var lastDelivered = 0L
    private val captureRequested = AtomicBoolean(false)

    private val consumer = FrameConsumer { frame -> handleFrame(frame) }

    private fun handleFrame(frame: Frame) {
        try {
            if (captureRequested.compareAndSet(true, false)) {
                saveFrame(frame)
            }
            val now = System.nanoTime()
            if (now - lastPreviewNanos < PREVIEW_INTERVAL_NANOS) return
            lastPreviewNanos = now
            val bitmap = toPreview(frame) ?: return
            val previous = previewBitmap
            previewBitmap = bitmap
            _state.update {
                it.copy(
                    preview = bitmap,
                    width = frame.width,
                    height = frame.height,
                )
            }
            previous?.recycle()
        } finally {
            frame.release()
        }
    }

    private fun toPreview(frame: Frame): Bitmap? = runCatching {
        if (frame.format != FrameFormat.RGBA_8888) return null
        val full = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        val duplicate = frame.data.duplicate()
        duplicate.position(0)
        full.copyPixelsFromBuffer(duplicate)
        val targetWidth = 480
        val targetHeight = (frame.height.toFloat() / frame.width * targetWidth).toInt().coerceAtLeast(2)
        val scaled = Bitmap.createScaledBitmap(full, targetWidth, targetHeight, true)
        if (scaled !== full) full.recycle()
        scaled
    }.getOrNull()

    private fun saveFrame(frame: Frame) {
        if (frame.format != FrameFormat.RGBA_8888) {
            appendLog("Save skipped: frame format is ${frame.format}")
            return
        }
        runCatching {
            val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
            val duplicate = frame.data.duplicate()
            duplicate.position(0)
            bitmap.copyPixelsFromBuffer(duplicate)
            val file = File(context.filesDir, "sdk_frame_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            bitmap.recycle()
            _state.update { it.copy(savedFramePath = file.absolutePath) }
            appendLog("Saved ${frame.width}x${frame.height} frame to ${file.name}")
        }.onFailure { error ->
            appendLog("Save failed: ${error.message}")
        }
    }

    private fun ensureSession(): CameraSession {
        session?.let { return it }
        val created = VirtualCameraSdk.createSession(
            config = VirtualCameraConfig(
                configuration = FrameConfiguration(1280, 720, 30, FrameFormat.RGBA_8888),
                bufferCount = 4,
            ),
            consumer = consumer,
        )
        session = created
        viewModelScope.launch {
            created.state.collect { sessionState ->
                _state.update {
                    it.copy(
                        status = when (sessionState) {
                            is SessionState.Streaming -> "streaming"
                            is SessionState.Failed -> "failed: ${sessionState.error.message}"
                            SessionState.Idle -> "idle"
                            SessionState.Starting -> "starting"
                            SessionState.Stopped -> "stopped"
                            SessionState.Closed -> "closed"
                        },
                    )
                }
                appendLog("Session state: $sessionState")
            }
        }
        viewModelScope.launch {
            created.frameStats.collect { stats ->
                val delta = stats.delivered - lastDelivered
                lastDelivered = stats.delivered
                _state.update {
                    it.copy(
                        delivered = stats.delivered,
                        dropped = stats.dropped,
                        fps = (delta * 2).toDouble(),
                    )
                }
            }
        }
        appendLog("Session created")
        return created
    }

    fun usePhysicalCamera(owner: LifecycleOwner, lensFacing: Int) {
        viewModelScope.launch {
            attach(VirtualCameraSdk.physicalCameraSource(context, owner, lensFacing))
        }
    }

    fun useVideo(uri: Uri) {
        viewModelScope.launch { attach(VirtualCameraSdk.videoSource(context, uri)) }
    }

    fun useImage(uri: Uri) {
        viewModelScope.launch { attach(VirtualCameraSdk.imageSource(context, uri)) }
    }

    private suspend fun attach(source: CameraSource) {
        val activeSession = ensureSession()
        appendLog("Attaching ${source.displayName}")
        val result = activeSession.useSource(source)
        result.onSuccess {
            _state.update {
                it.copy(
                    sourceLabel = source.displayName,
                    width = source.sourceWidth,
                    height = source.sourceHeight,
                )
            }
            appendLog("Attached ${source.kind}")
        }.onFailure { error ->
            appendLog("Attach failed: ${error.message}")
            _state.update { it.copy(status = "failed: ${error.message}") }
        }
    }

    fun requestFrameCapture() {
        captureRequested.set(true)
        appendLog("Frame capture requested")
    }

    fun togglePlayback() {
        viewModelScope.launch {
            val playback = session?.playback() ?: return@launch
            if (playback.playback.value.isPlaying) playback.pause() else playback.play()
            _state.update { it.copy(playback = playback.playback.value) }
        }
    }

    fun closeSession() {
        val active = session
        session = null
        previewBitmap?.recycle()
        previewBitmap = null
        _state.update { it.copy(preview = null, status = "closing") }
        active?.close()
        appendLog("Session closed")
    }

    fun reopenSession() {
        closeSession()
        ensureSession()
    }

    private fun appendLog(line: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _state.update { it.copy(log = (it.log + "$timestamp  $line").takeLast(50)) }
    }

    override fun onCleared() {
        session?.close()
        session = null
        previewBitmap?.recycle()
        previewBitmap = null
        super.onCleared()
    }

    companion object {
        private const val PREVIEW_INTERVAL_NANOS = 66_000_000L

        val DEFAULT_LENS = CameraSelector.LENS_FACING_BACK
    }
}
