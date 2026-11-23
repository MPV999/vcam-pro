package com.vcam.pro

import android.content.Context
import com.vcam.core.camera.CameraStudioController
import com.vcam.core.database.MediaRepository
import com.vcam.core.database.VcamDatabase
import com.vcam.core.media.MediaProbe
import com.vcam.core.media.ThumbnailLoader
import com.vcam.core.security.KeystoreVault
import com.vcam.core.storage.MediaFileStore
import com.vcam.core.storage.MediaStoreExporter
import com.vcam.core.storage.SafImporter
import com.vcam.core.storage.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** The library item the user picked to feed the virtual camera. */
data class SelectedVirtualSource(
    val mediaId: Long,
    val path: String,
    val isVideo: Boolean,
    val label: String,
)

/**
 * Manual dependency container. One instance per process, created by [VcamApplication].
 * Deliberately explicit: no reflection-based DI, so wiring errors surface at compile time.
 */
class AppContainer(private val context: Context) {

    val database: VcamDatabase by lazy { VcamDatabase.get(context) }

    val mediaRepository: MediaRepository by lazy {
        MediaRepository(database.mediaDao(), database.testSessionDao())
    }

    val fileStore: MediaFileStore by lazy { MediaFileStore(context) }

    val mediaStoreExporter: MediaStoreExporter by lazy { MediaStoreExporter(context) }

    val safImporter: SafImporter by lazy { SafImporter(context, fileStore) }

    val mediaProbe: MediaProbe by lazy { MediaProbe(context) }

    val thumbnailLoader: ThumbnailLoader by lazy { ThumbnailLoader() }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    val cameraStudio: CameraStudioController by lazy { CameraStudioController(context) }

    val diagnosticsVault: KeystoreVault by lazy { KeystoreVault() }

    /** Cross-screen selection: Media Library -> Virtual Camera Studio. */
    val selectedVirtualSource = MutableStateFlow<SelectedVirtualSource?>(null)
}
