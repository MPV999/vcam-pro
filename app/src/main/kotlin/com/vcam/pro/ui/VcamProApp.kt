package com.vcam.pro.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vcam.core.storage.AppSettings
import com.vcam.core.storage.ThemeMode
import com.vcam.feature.camera.CameraStudioScreen
import com.vcam.feature.dashboard.DashboardScreen
import com.vcam.feature.device.DeviceCenterScreen
import com.vcam.feature.library.MediaLibraryScreen
import com.vcam.feature.settings.SettingsScreen
import com.vcam.feature.virtualcamera.VirtualCameraStudioScreen
import com.vcam.pro.AppContainer
import com.vcam.pro.R
import com.vcam.pro.SelectedVirtualSource
import com.vcam.pro.ui.theme.VcamTheme
import kotlinx.coroutines.runBlocking

object Routes {
    const val DASHBOARD = "dashboard"
    const val CAMERA = "camera"
    const val LIBRARY = "library"
    const val VIRTUAL = "virtual"
    const val SETTINGS = "settings"
    const val DEVICE = "device"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.Dashboard),
    TopLevelDestination(Routes.CAMERA, R.string.nav_camera, Icons.Filled.CameraAlt),
    TopLevelDestination(Routes.LIBRARY, R.string.nav_library, Icons.Filled.PhotoLibrary),
    TopLevelDestination(Routes.VIRTUAL, R.string.nav_virtual, Icons.Filled.SettingsInputComponent),
    TopLevelDestination(Routes.DEVICE, R.string.nav_device, Icons.Filled.Memory),
    TopLevelDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun VcamProApp(container: AppContainer) {
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    VcamTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val context = LocalContext.current
        val activity = context as? Activity

        KeepScreenOn(enabled = settings.keepScreenOn && currentRoute == Routes.CAMERA)

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        repository = container.mediaRepository,
                        fileStore = container.fileStore,
                        onOpenCamera = { navController.navigate(Routes.CAMERA) },
                        onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                        onOpenVirtualCamera = { navController.navigate(Routes.VIRTUAL) },
                        onOpenDeviceCenter = { navController.navigate(Routes.DEVICE) },
                    )
                }

                composable(Routes.CAMERA) {
                    CameraStudioScreen(
                        controller = container.cameraStudio,
                        fileStore = container.fileStore,
                        mediaProbe = container.mediaProbe,
                        repository = container.mediaRepository,
                    )
                }

                composable(Routes.LIBRARY) {
                    MediaLibraryScreen(
                        repository = container.mediaRepository,
                        importer = container.safImporter,
                        exporter = container.mediaStoreExporter,
                        mediaProbe = container.mediaProbe,
                        thumbnails = container.thumbnailLoader,
                        onUseAsVirtualSource = { item ->
                            container.selectedVirtualSource.value = SelectedVirtualSource(
                                mediaId = item.id,
                                path = item.filePath,
                                isVideo = item.isVideo,
                                label = item.displayName,
                            )
                            navController.navigate(Routes.VIRTUAL)
                        },
                    )
                }

                composable(Routes.VIRTUAL) {
                    val selection by container.selectedVirtualSource.collectAsStateWithLifecycle()
                    VirtualCameraStudioScreen(
                        repository = container.mediaRepository,
                        selectedPath = selection?.path,
                        selectedIsVideo = selection?.isVideo ?: false,
                        selectedLabel = selection?.label ?: "",
                        onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                        onSelectionConsumed = { container.selectedVirtualSource.value = null },
                    )
                }

                composable(Routes.DEVICE) {
                    DeviceCenterScreen()
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        repository = container.settingsRepository,
                        onLanguageChanged = { language ->
                            // Persist synchronously, then restart so resources reload with
                            // the new locale before the first frame is drawn.
                            runBlocking {
                                container.settingsRepository.update { it.copy(language = language) }
                            }
                            activity?.recreate()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
