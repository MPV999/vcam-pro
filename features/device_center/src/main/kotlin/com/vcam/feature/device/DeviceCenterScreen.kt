package com.vcam.feature.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DeviceCenterScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: DeviceCenterViewModel = viewModel(factory = DeviceCenterViewModel.factory(context))
    val report by viewModel.report.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.device_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        DeviceCard(title = stringResource(R.string.device_hardware)) {
            InfoRow(stringResource(R.string.device_model), "${report.manufacturer} ${report.model}")
            InfoRow(stringResource(R.string.device_android), "${report.androidRelease} (API ${report.sdkInt})")
            InfoRow(stringResource(R.string.device_abi), report.abis)
            InfoRow(stringResource(R.string.device_memory), "${report.memoryClassMb} MB")
            InfoRow(stringResource(R.string.device_screen), report.screen)
        }

        DeviceCard(title = stringResource(R.string.device_cameras)) {
            if (report.cameras.isEmpty()) {
                Text(
                    text = stringResource(R.string.device_no_cameras),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                report.cameras.forEach { camera ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            text = "Camera ${camera.id} · ${camera.facing} · ${camera.hardwareLevel}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "max JPEG ${camera.maxJpegSize} · fps ${camera.fpsRanges} · flash ${if (camera.hasFlash) "yes" else "no"} · sensor ${camera.sensorOrientation}°",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        DeviceCard(title = stringResource(R.string.device_decoders)) {
            if (report.videoDecoders.isEmpty()) {
                Text(stringResource(R.string.device_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                report.videoDecoders.forEach { codec ->
                    Text(
                        text = codec,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        DeviceCard(title = stringResource(R.string.device_encoders)) {
            if (report.videoEncoders.isEmpty()) {
                Text(stringResource(R.string.device_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                report.videoEncoders.forEach { codec ->
                    Text(
                        text = codec,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DeviceCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
