package com.vcam.feature.virtualcamera

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vcam.core.common.formatDurationUs
import com.vcam.core.database.MediaRepository
import com.vcam.feature.playback.VideoPreview
import com.vcam.sdk.api.SessionState
import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.format.ScaleMode
import java.io.File
import android.net.Uri

@Composable
fun VirtualCameraStudioScreen(
    repository: MediaRepository,
    selectedPath: String?,
    selectedIsVideo: Boolean,
    selectedLabel: String,
    onOpenLibrary: () -> Unit,
    onSelectionConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: VirtualCameraStudioViewModel = viewModel(
        factory = VirtualCameraStudioViewModel.factory(context, repository),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(selectedPath, selectedIsVideo) {
        if (selectedPath != null) {
            if (selectedIsVideo) {
                viewModel.attachVideo(selectedPath, selectedLabel)
            } else {
                viewModel.attachImage(selectedPath, selectedLabel)
            }
            onSelectionConsumed()
        }
    }

    LaunchedEffect(state.sessionState) {
        // Keeps the transport slider in sync while a video source is playing.
        while (true) {
            viewModel.refreshPlayback()
            kotlinx.coroutines.delay(500)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.virtual_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.virtual_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Live preview of the frames the SDK is delivering.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color.Black, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = state.previewBitmap
            if (bitmap != null && !bitmap.isRecycled) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.virtual_preview),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = stringResource(R.string.virtual_no_source),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Text(
                text = state.sourceLabel.ifBlank { stringResource(R.string.virtual_no_source) },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Color(0x99000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        val activePath = state.activePath
        if (activePath != null && state.activeIsVideo) {
            Text(
                text = stringResource(R.string.virtual_source_preview),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VideoPreview(
                uri = Uri.fromFile(File(activePath)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                StatusRow(
                    stringResource(R.string.virtual_state),
                    when (val sessionState = state.sessionState) {
                        is SessionState.Streaming -> "streaming (${sessionState.sourceKind.name})"
                        is SessionState.Failed -> "failed: ${sessionState.error.message}"
                        SessionState.Idle -> "idle"
                        SessionState.Starting -> "starting"
                        SessionState.Stopped -> "stopped"
                        SessionState.Closed -> "closed"
                    },
                )
                StatusRow(
                    stringResource(R.string.virtual_fps),
                    "%.1f".format(state.measuredFps),
                )
                StatusRow(
                    stringResource(R.string.virtual_resolution),
                    "${state.configuration.width}x${state.configuration.height}",
                )
                StatusRow(
                    stringResource(R.string.virtual_source_size),
                    if (state.sourceWidth > 0) "${state.sourceWidth}x${state.sourceHeight}" else "—",
                )
                StatusRow(
                    stringResource(R.string.virtual_frames),
                    "${state.deliveredFrames} / dropped ${state.droppedFrames}",
                )
                state.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenLibrary) {
                Text(stringResource(R.string.virtual_pick_media))
            }
            Button(
                onClick = {
                    viewModel.attachPhysicalCamera(lifecycleOwner)
                },
            ) {
                Text(stringResource(R.string.virtual_use_camera))
            }
            IconButton(onClick = viewModel::stopSession) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.virtual_stop))
            }
        }

        // Transport
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::togglePlayPause) {
                        Icon(
                            imageVector = if (state.playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.virtual_play_pause),
                        )
                    }
                    IconButton(onClick = viewModel::toggleLoop) {
                        Icon(
                            imageVector = Icons.Filled.Loop,
                            contentDescription = stringResource(R.string.virtual_loop),
                            tint = if (state.playback.looping) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = "${formatDurationUs(state.playback.positionUs)} / ${formatDurationUs(state.playback.durationUs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                Slider(
                    value = if (state.playback.durationUs > 0) {
                        state.playback.positionUs.toFloat() / state.playback.durationUs
                    } else {
                        0f
                    },
                    onValueChange = { fraction ->
                        viewModel.seekTo((fraction * state.playback.durationUs).toLong())
                    },
                    enabled = state.playback.durationUs > 0,
                )
            }
        }

        // Configuration
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.virtual_scale_mode),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScaleMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.scaleMode == mode,
                            onClick = { viewModel.setScaleMode(mode) },
                            label = { Text(mode.name, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.virtual_output_size),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FrameConfiguration.PRESETS.forEach { preset ->
                        FilterChip(
                            selected = state.configuration.width == preset.width &&
                                state.configuration.height == preset.height,
                            onClick = { viewModel.setResolution(preset.width, preset.height) },
                            label = {
                                Text(
                                    text = "${preset.width}x${preset.height}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.virtual_output_fps),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FrameConfiguration.COMMON_FPS.forEach { fps ->
                        FilterChip(
                            selected = state.configuration.fps == fps,
                            onClick = { viewModel.setFps(fps) },
                            label = { Text("$fps", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }

        if (state.log.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.virtual_log),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.log.takeLast(8).forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
