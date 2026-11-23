package com.vcam.sdk.source

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.lifecycle.LifecycleOwner
import com.vcam.sdk.api.CameraSession
import com.vcam.sdk.api.CameraSource
import com.vcam.sdk.api.VirtualCameraConfig
import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.frames.FrameConsumer

/**
 * Entry point of the VCAM Virtual Camera SDK.
 *
 * The SDK never touches another app's camera: it produces frames *inside* the host app,
 * which then displays, records or forwards them itself. There is no hooking, no root, no
 * HAL modification — an app can only see these frames because it linked this SDK.
 *
 * ```kotlin
 * val session = VirtualCameraSdk.createSession()
 * session.setConsumer { frame ->
 *     // copy what you need, then always release
 *     frame.release()
 * }
 * session.useSource(VirtualCameraSdk.videoSource(context, uri))
 * ```
 */
object VirtualCameraSdk {

    const val VERSION_NAME: String = "1.0.0"
    const val VERSION_CODE: Int = 1

    /** Creates a session with no source attached. */
    fun createSession(
        config: VirtualCameraConfig = VirtualCameraConfig(),
        consumer: FrameConsumer? = null,
    ): CameraSession {
        val session = DefaultCameraSession(
            initialConfiguration = config.configuration,
            bufferCount = config.bufferCount,
            autoStart = config.autoStart,
        )
        if (consumer != null) session.setConsumer(consumer)
        return session
    }

    /** Video file source. Decoded in a streaming fashion, never fully buffered. */
    fun videoSource(
        context: Context,
        uri: Uri,
        displayName: String? = null,
    ): CameraSource = VideoFileSource(
        context = context.applicationContext,
        uri = uri,
        displayName = displayName ?: uri.lastPathSegment ?: "video",
    )

    /** Still image source, rendered continuously at the configured FPS. */
    fun imageSource(
        context: Context,
        uri: Uri,
        displayName: String? = null,
    ): CameraSource = ImageFileSource(
        context = context.applicationContext,
        uri = uri,
        displayName = displayName ?: uri.lastPathSegment ?: "image",
    )

    /** Live physical camera source. Requires the CAMERA permission. */
    fun physicalCameraSource(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    ): CameraSource = PhysicalCameraSource(
        context = context.applicationContext,
        lifecycleOwner = lifecycleOwner,
        lensFacing = lensFacing,
    )

    /** Convenience preset list for settings UIs. */
    val PRESET_CONFIGURATIONS: List<FrameConfiguration> = FrameConfiguration.PRESETS
}
