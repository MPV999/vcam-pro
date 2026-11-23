package com.vcam.feature.device

import android.app.ActivityManager
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecList
import android.os.Build
import android.util.DisplayMetrics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CameraRow(
    val id: String,
    val facing: String,
    val maxJpegSize: String,
    val fpsRanges: String,
    val hasFlash: Boolean,
    val sensorOrientation: Int,
    val hardwareLevel: String,
)

data class DeviceReport(
    val loading: Boolean = true,
    val model: String = Build.MODEL,
    val manufacturer: String = Build.MANUFACTURER,
    val androidRelease: String = Build.VERSION.RELEASE,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val abis: String = Build.SUPPORTED_ABIS.joinToString(),
    val memoryClassMb: Int = 0,
    val screen: String = "",
    val cameras: List<CameraRow> = emptyList(),
    val videoDecoders: List<String> = emptyList(),
    val videoEncoders: List<String> = emptyList(),
)

/**
 * Reads the device's real capabilities: camera ids, JPEG sizes, AE fps ranges, hardware
 * level, MediaCodec video codecs, memory class and display. Everything shown here is
 * queried from the platform at runtime.
 */
class DeviceCenterViewModel(private val context: Context) : ViewModel() {

    private val _report = MutableStateFlow(DeviceReport())
    val report: StateFlow<DeviceReport> = _report.asStateFlow()

    init {
        viewModelScope.launch {
            val collected = withContext(Dispatchers.IO) { collect() }
            _report.value = collected
        }
    }

    private fun collect(): DeviceReport {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        return DeviceReport(
            loading = false,
            memoryClassMb = activityManager?.memoryClass ?: 0,
            screen = "${metrics.widthPixels}x${metrics.heightPixels} @ ${metrics.densityDpi}dpi",
            cameras = readCameras(),
            videoDecoders = readCodecs(encoder = false),
            videoEncoders = readCodecs(encoder = true),
        )
    }

    private fun readCameras(): List<CameraRow> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return emptyList()
        return runCatching {
            manager.cameraIdList.map { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                val facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "front"
                    CameraCharacteristics.LENS_FACING_BACK -> "back"
                    else -> "external"
                }
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val largest = map?.getOutputSizes(ImageFormat.JPEG)
                    ?.maxByOrNull { it.width.toLong() * it.height.toLong() }
                val fpsRanges = characteristics
                    .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    ?.joinToString(", ") { "${it.lower}-${it.upper}" }
                    .orEmpty()
                val level = when (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                    else -> "UNKNOWN"
                }
                CameraRow(
                    id = id,
                    facing = facing,
                    maxJpegSize = largest?.let { "${it.width}x${it.height}" } ?: "—",
                    fpsRanges = fpsRanges.ifBlank { "—" },
                    hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                    sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
                    hardwareLevel = level,
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun readCodecs(encoder: Boolean): List<String> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder == encoder }
            .filter { info -> info.supportedTypes.any { it.startsWith("video/") } }
            .map { info -> "${info.name} (${info.supportedTypes.joinToString()})" }
            .sorted()
    }.getOrElse { emptyList() }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { DeviceCenterViewModel(context) }
        }
    }
}
