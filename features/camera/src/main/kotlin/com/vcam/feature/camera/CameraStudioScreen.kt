package com.vcam.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vcam.core.camera.CameraStudioController
import com.vcam.core.camera.CaptureQuality
import com.vcam.core.database.MediaRepository
import com.vcam.core.media.MediaProbe
import com.vcam.core.storage.MediaFileStore

@Composable
fun CameraStudioScreen(
    controller: CameraStudioController,
    fileStore: MediaFileStore,
    mediaProbe: MediaProbe,
    repository: MediaRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: CameraStudioViewModel = viewModel(
        factory = CameraStudioViewModel.factory(controller, fileStore, mediaProbe, repository),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        viewModel.onPermissionResult(result[Manifest.permission.CAMERA] == true)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
        ) {
            if (state.permissionGranted) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            viewModel.attachPreview(this, lifecycleOwner)
                        }
                    },
                )
                if (state.showGrid) {
                    GridOverlay()
                }
                if (state.countdown > 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.countdown.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                        )
                    }
                }
                if (state.isRecording) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(Color(0xFFFF5C7A), CircleShape),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.camera_recording),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.camera_permission_required),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                IconButton(onClick = viewModel::toggleTorch) {
                    Icon(
                        imageVector = if (state.torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = stringResource(R.string.camera_flash),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = viewModel::toggleGrid) {
                    Icon(
                        imageVector = Icons.Filled.GridOn,
                        contentDescription = stringResource(R.string.camera_grid),
                        tint = if (state.showGrid) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                IconButton(onClick = viewModel::switchCamera) {
                    Icon(
                        imageVector = Icons.Filled.Cameraswitch,
                        contentDescription = stringResource(R.string.camera_switch),
                        tint = Color.White,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
        ) {
            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.camera_zoom),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    modifier = Modifier.weight(1f),
                    value = state.zoomRatio,
                    onValueChange = viewModel::setZoom,
                    valueRange = 1f..maxOf(1.01f, state.capabilities.maxZoomRatio),
                    enabled = state.permissionGranted,
                )
                Text(
                    text = "${"%.1f".format(state.zoomRatio)}x",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CaptureQuality.entries.forEach { quality ->
                    FilterChip(
                        selected = state.quality == quality,
                        onClick = { viewModel.setQuality(quality) },
                        label = { Text(quality.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0, 3, 10).forEach { seconds ->
                        FilterChip(
                            selected = state.timerSeconds == seconds,
                            onClick = { viewModel.setTimerSeconds(seconds) },
                            label = {
                                Text(
                                    text = if (seconds == 0) stringResource(R.string.camera_timer_off) else "${seconds}s",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = viewModel::takePhoto,
                        enabled = state.permissionGranted && !state.isBusy && !state.isRecording,
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(R.string.camera_capture),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    IconButton(
                        onClick = viewModel::toggleRecording,
                        enabled = state.permissionGranted && !state.isBusy,
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                if (state.isRecording) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.secondary,
                                CircleShape,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = stringResource(R.string.camera_record),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val stepX = size.width / 3f
        val stepY = size.height / 3f
        val color = Color.White.copy(alpha = 0.28f)
        for (index in 1..2) {
            drawLine(color, Offset(stepX * index, 0f), Offset(stepX * index, size.height), 1f)
            drawLine(color, Offset(0f, stepY * index), Offset(size.width, stepY * index), 1f)
        }
    }
}
