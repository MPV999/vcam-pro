package com.vcam.feature.dashboard

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vcam.core.database.MediaItemEntity
import com.vcam.core.database.MediaRepository
import com.vcam.core.database.TestSessionEntity
import com.vcam.core.storage.MediaFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Real device facts, read from the platform — never invented. */
data class DeviceSnapshot(
    val model: String = Build.MODEL,
    val androidRelease: String = Build.VERSION.RELEASE,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val cameraCount: Int = 0,
    val hasFlash: Boolean = false,
)

data class DashboardUiState(
    val loading: Boolean = true,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val libraryBytes: Long = 0,
    val diskBytes: Long = 0,
    val recent: List<MediaItemEntity> = emptyList(),
    val sessions: List<TestSessionEntity> = emptyList(),
    val device: DeviceSnapshot = DeviceSnapshot(),
) {
    val itemCount: Int get() = photoCount + videoCount
}

class DashboardViewModel(
    private val repository: MediaRepository,
    private val fileStore: MediaFileStore,
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeAll(),
                repository.observeTestSessions(3),
            ) { items, sessions -> items to sessions }
                .collect { (items, sessions) ->
                    val disk = withContext(Dispatchers.IO) { fileStore.usageBytes() }
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            photoCount = items.count { !it.isVideo },
                            videoCount = items.count { it.isVideo },
                            libraryBytes = items.sumOf { it.sizeBytes },
                            diskBytes = disk,
                            recent = items.take(6),
                            sessions = sessions,
                            device = readDevice(),
                        )
                    }
                }
        }
    }

    private fun readDevice(): DeviceSnapshot {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return DeviceSnapshot()
        var count = 0
        var flash = false
        runCatching {
            for (id in manager.cameraIdList) {
                count++
                val characteristics = manager.getCameraCharacteristics(id)
                if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    flash = true
                }
            }
        }
        return DeviceSnapshot(cameraCount = count, hasFlash = flash)
    }

    companion object {
        fun factory(
            repository: MediaRepository,
            fileStore: MediaFileStore,
            context: Context,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(repository, fileStore, context) }
        }
    }
}
