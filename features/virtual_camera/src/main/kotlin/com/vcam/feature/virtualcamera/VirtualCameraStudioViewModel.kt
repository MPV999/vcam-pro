package com.vcam.feature.virtualcamera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vcam.core.common.VcamLog
import com.vcam.core.database.MediaRepository
import com.vcam.core.database.TestSessionEntity
import com.vcam.sdk.api.CameraSession
import com.vcam.sdk.api.CameraSource
import com.vcam.sdk.api.PlaybackState
import com.vcam.sdk.api.SessionState
import com.vcam.sdk.api.SourceError
import com.vcam.sdk.api.SourceKind
import com.vcam.sdk.api.VirtualCameraConfig
import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.format.FrameFormat
import com.vcam.sdk.format.ScaleMode
import com.vcam.sdk.frames.Frame
import com.vcam.sdk.frames.FrameConsumer
import com.vcam.sdk.frames.FrameStats
import com.vcam.sdk.source.VirtualCameraSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VirtualCameraUiState(
    val sourceLabel: String = "",
    val sourceKind: SourceKind? = null,
    val sessionState: SessionState = SessionState.Idle,
    val playback: PlaybackState = PlaybackState(),
    val stats: FrameStats = FrameStats(),
    val measuredFps: Double = 0.0,
    val configuration: FrameConfiguration = FrameConfiguration.P720,
    val scaleMode: ScaleMode = ScaleMode.CROP,
    val previewBitmap: Bitmap? = null,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val error: String? = null,
    val log: List<String> = emptyList(),
    val activePath: String? = null,
    val activeIsVideo: Boolean = false,
) {
    val deliveredFrames: Long get() = stats.delivered
    val droppedFrames: Long get() = stats.dropped
}

/**
 * Drives the Virtual Camera Studio. The app itself is an SDK client here: it creates a
 * session, attaches a source and consumes frames exactly the way the sample app does.
 *
 * The preview bitmap is a real decoded frame from the SDK pipeline, throttled to ~15 fps
 * and down-scaled so the UI stays responsive while the pipeline runs at full rate.
 */
class VirtualCameraStudioViewModel(
    private val context: Context,
    private val repository: MediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VirtualCameraUiState())
    val state: StateFlow<VirtualCameraUiState> = _state.asStateFlow()

    private var session: CameraSession? = null
    private var sessionStartedAt: Long = 0L
    private var lastPreviewNanos = 0L
    private var lastDelivered = 0L
    private var previewBitmap: Bitmap? = null
    private var sessionLogged = false

    private val previewConsumer = FrameConsumer { frame -> handleFrame(frame) }

    private fun handleFrame(frame: Frame) {
        try {
            if (frame.format != FrameFormat.RGBA_8888) return
            val now = System.nanoTime()
            if (now - lastPreviewNanos < PREVIEW_INTERVAL_NANOS) return
            lastPreviewNanos = now
            val scaled = toPreviewBitmap(frame) ?: return
            val previous = previewBitmap
            previewBitmap = scaled
            _state.update { it.copy(previewBitmap = scaled) }
            previous?.recycle()
        } finally {
            frame.release()
        }
    }

    private fun toPreviewBitmap(frame: Frame): Bitmap? = runCatching {
        val full = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        val duplicate = frame.data.duplicate()
        duplicate.position(0)
        full.copyPixelsFromBuffer(duplicate)
        val targetWidth = PREVIEW_WIDTH
        val targetHeight = (frame.height.toFloat() / frame.width * targetWidth).toInt().coerceAtLeast(2)
        val scaled = Bitmap.createScaledBitmap(full, targetWidth, targetHeight, true)
        if (scaled !== full) full.recycle()
        scaled
    }.getOrNull()

    private fun ensureSession(): CameraSession {
        val existing = session
        if (existing != null) return existing
        val created = VirtualCameraSdk.createSession(
            config = VirtualCameraConfig(configuration = _state.value.configuration),
            consumer = previewConsumer,
        )
        session = created
        sessionStartedAt = System.currentTimeMillis()
        sessionLogged = false
        viewModelScope.launch {
            created.frameStats.collect { stats ->
                val delta = stats.delivered - lastDelivered
                lastDelivered = stats.delivered
                _state.update {
                    it.copy(stats = stats, measuredFps = (delta * 2).toDouble().coerceAtLeast(0.0))
                }
            }
        }
        viewModelScope.launch {
            created.state.collect { sessionState ->
                _state.update { it.copy(sessionState = sessionState) }
                if (sessionState is SessionState.Failed) {
                    _state.update { it.copy(error = sessionState.error.message) }
                    appendLog("Error: ${sessionState.error.message}")
                }
            }
        }
        viewModelScope.launch {
            created.configuration.collect { configuration ->
                _state.update { it.copy(configuration = configuration) }
            }
        }
        return created
    }

    fun attachVideo(path: String, label: String) {
        viewModelScope.launch {
            val uri = Uri.fromFile(File(path))
            if (!File(path).exists()) {
                _state.update { it.copy(error = "File is missing: $path") }
                return@launch
            }
            attach(
                source = VirtualCameraSdk.videoSource(context, uri, label),
                path = path,
                isVideo = true,
            )
        }
    }

    fun attachImage(path: String, label: String) {
        viewModelScope.launch {
            if (!File(path).exists()) {
                _state.update { it.copy(error = "File is missing: $path") }
                return@launch
            }
            attach(
                source = VirtualCameraSdk.imageSource(context, Uri.fromFile(File(path)), label),
                path = path,
                isVideo = false,
            )
        }
    }

    fun attachPhysicalCamera(lifecycleOwner: LifecycleOwner, lensFacing: Int = CameraSelector.LENS_FACING_BACK) {
        viewModelScope.launch {
            attach(
                source = VirtualCameraSdk.physicalCameraSource(context, lifecycleOwner, lensFacing),
                path = null,
                isVideo = false,
            )
        }
    }

    private suspend fun attach(source: CameraSource, path: String?, isVideo: Boolean) {
        val activeSession = ensureSession()
        appendLog("Attaching ${source.displayName}")
        val result = activeSession.useSource(source)
        result.onSuccess {
            _state.update {
                it.copy(
                    sourceLabel = source.displayName,
                    sourceKind = source.kind,
                    sourceWidth = source.sourceWidth,
                    sourceHeight = source.sourceHeight,
                    error = null,
                    activePath = path,
                    activeIsVideo = isVideo,
                    previewBitmap = null,
                )
            }
            appendLog("Streaming ${source.kind} at ${activeSession.configuration.value.width}x${activeSession.configuration.value.height}")
        }.onFailure { error ->
            _state.update { it.copy(error = error.message) }
            appendLog("Failed: ${error.message}")
        }
    }

    fun setScaleMode(mode: ScaleMode) {
        _state.update { it.copy(scaleMode = mode) }
        applyConfiguration(_state.value.configuration.copy(scaleMode = mode))
    }

    fun setResolution(width: Int, height: Int) {
        applyConfiguration(_state.value.configuration.copy(width = width, height = height))
    }

    fun setFps(fps: Int) {
        applyConfiguration(_state.value.configuration.copy(fps = fps))
    }

    fun setFormat(format: FrameFormat) {
        applyConfiguration(_state.value.configuration.copy(format = format))
    }

    private fun applyConfiguration(configuration: FrameConfiguration) {
        viewModelScope.launch {
            val activeSession = session
            if (activeSession == null) {
                _state.update { it.copy(configuration = configuration) }
                return@launch
            }
            _state.update { it.copy(configuration = configuration) }
            activeSession.updateConfiguration(configuration)
            appendLog("Configuration ${configuration.width}x${configuration.height}@${configuration.fps}")
            // The active source is released by updateConfiguration; re-attach it.
            val path = _state.value.activePath
            val kind = _state.value.sourceKind
            when {
                kind == SourceKind.PHYSICAL_CAMERA -> {
                    appendLog("Re-attach the physical camera to apply the new configuration")
                }
                path != null && _state.value.activeIsVideo -> attachVideo(path, _state.value.sourceLabel)
                path != null -> attachImage(path, _state.value.sourceLabel)
            }
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            val playback = session?.playback() ?: return@launch
            if (playback.playback.value.isPlaying) playback.pause() else playback.play()
        }
    }

    fun seekTo(positionUs: Long) {
        viewModelScope.launch {
            session?.playback()?.seekTo(positionUs)
        }
    }

    fun seekBy(deltaUs: Long) {
        viewModelScope.launch {
            session?.playback()?.seekBy(deltaUs)
        }
    }

    fun toggleLoop() {
        viewModelScope.launch {
            val playback = session?.playback() ?: return@launch
            playback.setLooping(!playback.playback.value.looping)
        }
    }

    fun refreshPlayback() {
        viewModelScope.launch {
            val playback = session?.playback()?.playback?.value ?: return@launch
            _state.update { it.copy(playback = playback) }
        }
    }

    fun stopSession() {
        viewModelScope.launch {
            val activeSession = session ?: return@launch
            activeSession.stop()
            recordSession(TestSessionEntity.OUTCOME_STOPPED, "Stopped by user")
            appendLog("Session stopped")
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun appendLog(line: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _state.update { it.copy(log = (it.log + "$timestamp  $line").takeLast(40)) }
        VcamLog.i("VirtualStudio", line)
    }

    private fun recordSession(outcome: String, detail: String) {
        if (sessionLogged) return
        sessionLogged = true
        val stats = _state.value.stats
        val entry = TestSessionEntity(
            sourceLabel = _state.value.sourceLabel.ifBlank { "none" },
            sourceKind = _state.value.sourceKind?.name ?: "NONE",
            startedAt = sessionStartedAt,
            endedAt = System.currentTimeMillis(),
            framesDelivered = stats.delivered,
            framesDropped = stats.dropped,
            averageFps = _state.value.measuredFps.toFloat(),
            width = _state.value.configuration.width,
            height = _state.value.configuration.height,
            outcome = outcome,
            detail = detail,
        )
        viewModelScope.launch(Dispatchers.IO) { repository.recordTestSession(entry) }
    }

    override fun onCleared() {
        val activeSession = session
        recordSession(
            outcome = if (_state.value.sessionState is SessionState.Failed) {
                TestSessionEntity.OUTCOME_ERROR
            } else {
                TestSessionEntity.OUTCOME_SUCCESS
            },
            detail = _state.value.error ?: "",
        )
        session = null
        previewBitmap?.recycle()
        previewBitmap = null
        activeSession?.close()
        super.onCleared()
    }

    companion object {
        private const val PREVIEW_INTERVAL_NANOS = 66_000_000L
        private const val PREVIEW_WIDTH = 480

        fun factory(context: Context, repository: MediaRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { VirtualCameraStudioViewModel(context, repository) }
            }
    }
}
