package com.vcam.feature.camera

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vcam.core.camera.CameraStudioCapabilities
import com.vcam.core.camera.CameraStudioController
import com.vcam.core.camera.CaptureQuality
import com.vcam.core.database.MediaItemEntity
import com.vcam.core.database.MediaOrigins
import com.vcam.core.database.MediaRepository
import com.vcam.core.database.MediaTypes
import com.vcam.core.media.MediaProbe
import com.vcam.core.storage.MediaFileStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CameraStudioUiState(
    val permissionGranted: Boolean = false,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val quality: CaptureQuality = CaptureQuality.FHD,
    val capabilities: CameraStudioCapabilities = CameraStudioCapabilities(),
    val isRecording: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
    val timerSeconds: Int = 0,
    val countdown: Int = 0,
    val showGrid: Boolean = true,
    val torchEnabled: Boolean = false,
    val zoomRatio: Float = 1f,
    val savedCount: Int = 0,
)

class CameraStudioViewModel(
    private val controller: CameraStudioController,
    private val fileStore: MediaFileStore,
    private val mediaProbe: MediaProbe,
    private val repository: MediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CameraStudioUiState())
    val state: StateFlow<CameraStudioUiState> = _state.asStateFlow()

    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var pendingVideoFile: File? = null

    fun attachPreview(view: PreviewView, owner: LifecycleOwner) {
        previewView = view
        lifecycleOwner = owner
        if (_state.value.permissionGranted) bind()
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted) }
        if (granted) {
            bind()
        } else {
            _state.update { it.copy(message = "Camera permission denied") }
        }
    }

    private fun bind() {
        val view = previewView ?: return
        val owner = lifecycleOwner ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            val result = controller.bind(owner, view, _state.value.lensFacing, _state.value.quality)
            result.onSuccess {
                _state.update {
                    it.copy(
                        isBusy = false,
                        capabilities = controller.capabilities(),
                        message = null,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isBusy = false, message = error.message ?: "Cannot open camera") }
            }
        }
    }

    fun switchCamera() {
        val current = _state.value.lensFacing
        val next = if (current == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        val capabilities = _state.value.capabilities
        val available = if (next == CameraSelector.LENS_FACING_FRONT) {
            capabilities.hasFrontCamera
        } else {
            capabilities.hasBackCamera
        }
        if (!available) {
            _state.update { it.copy(message = "That camera is not available on this device") }
            return
        }
        _state.update { it.copy(lensFacing = next, torchEnabled = false) }
        bind()
    }

    fun setQuality(quality: CaptureQuality) {
        if (quality == _state.value.quality) return
        _state.update { it.copy(quality = quality) }
        bind()
    }

    fun setZoom(ratio: Float) {
        controller.setZoomRatio(ratio)
        _state.update { it.copy(zoomRatio = controller.currentZoomRatio()) }
    }

    fun toggleTorch() {
        val capabilities = _state.value.capabilities
        if (!capabilities.hasFlash) {
            _state.update { it.copy(message = "No flash on this camera") }
            return
        }
        val next = !_state.value.torchEnabled
        controller.setTorch(next)
        _state.update { it.copy(torchEnabled = next) }
    }

    fun toggleGrid() = _state.update { it.copy(showGrid = !it.showGrid) }

    fun setTimerSeconds(seconds: Int) = _state.update { it.copy(timerSeconds = seconds) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun takePhoto() {
        if (_state.value.isBusy || _state.value.isRecording) return
        viewModelScope.launch {
            val seconds = _state.value.timerSeconds
            if (seconds > 0) {
                for (remaining in seconds downTo 1) {
                    _state.update { it.copy(countdown = remaining) }
                    delay(1000)
                }
                _state.update { it.copy(countdown = 0) }
            }

            _state.update { it.copy(isBusy = true, message = null) }
            val target = fileStore.newPhotoFile()
            controller.takePhoto(target)
                .onSuccess { saved -> persistPhoto(saved) }
                .onFailure { error ->
                    _state.update { it.copy(isBusy = false, message = error.message ?: "Capture failed") }
                }
        }
    }

    private suspend fun persistPhoto(file: File) {
        val info = mediaProbe.probe(file)
        repository.add(
            MediaItemEntity(
                displayName = file.name,
                filePath = file.absolutePath,
                mimeType = info?.mimeType?.takeIf { it.isNotBlank() } ?: "image/jpeg",
                mediaType = MediaTypes.PHOTO,
                sizeBytes = file.length(),
                width = info?.width ?: 0,
                height = info?.height ?: 0,
                durationMs = 0L,
                createdAt = System.currentTimeMillis(),
                origin = MediaOrigins.CAPTURE,
            ),
        )
        _state.update {
            it.copy(
                isBusy = false,
                message = "Saved ${file.name}",
                savedCount = it.savedCount + 1,
            )
        }
    }

    fun toggleRecording() {
        if (_state.value.isBusy) return
        if (_state.value.isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val target = fileStore.newVideoFile()
        val result = controller.startRecording(target, withAudio = controller.hasAudioPermission)
        result.onSuccess {
            pendingVideoFile = target
            _state.update { it.copy(isRecording = true, message = "Recording…") }
        }.onFailure { error ->
            _state.update { it.copy(isRecording = false, message = error.message ?: "Cannot start recording") }
        }
    }

    private fun stopRecording() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            controller.stopRecording()
                .onSuccess { saved -> persistVideo(saved) }
                .onFailure { error ->
                    _state.update {
                        it.copy(isRecording = false, isBusy = false, message = error.message ?: "Recording failed")
                    }
                }
            pendingVideoFile = null
        }
    }

    private suspend fun persistVideo(file: File) {
        val info = mediaProbe.probe(file)
        repository.add(
            MediaItemEntity(
                displayName = file.name,
                filePath = file.absolutePath,
                mimeType = info?.mimeType?.takeIf { it.isNotBlank() } ?: "video/mp4",
                mediaType = MediaTypes.VIDEO,
                sizeBytes = file.length(),
                width = info?.width ?: 0,
                height = info?.height ?: 0,
                durationMs = info?.durationMs ?: 0L,
                createdAt = System.currentTimeMillis(),
                origin = MediaOrigins.CAPTURE,
            ),
        )
        _state.update {
            it.copy(
                isRecording = false,
                isBusy = false,
                message = "Saved ${file.name}",
                savedCount = it.savedCount + 1,
            )
        }
    }

    override fun onCleared() {
        controller.unbind()
        super.onCleared()
    }

    companion object {
        fun factory(
            controller: CameraStudioController,
            fileStore: MediaFileStore,
            mediaProbe: MediaProbe,
            repository: MediaRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CameraStudioViewModel(controller, fileStore, mediaProbe, repository) }
        }
    }
}
