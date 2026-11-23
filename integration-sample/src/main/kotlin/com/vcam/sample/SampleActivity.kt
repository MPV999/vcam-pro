package com.vcam.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class SampleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = Color(0xFF08080C), modifier = Modifier.fillMaxSize()) {
                    SampleScreen()
                }
            }
        }
    }
}

@Composable
private fun SampleScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: SampleViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.usePhysicalCamera(lifecycleOwner, SampleViewModel.DEFAULT_LENS)
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.useVideo(uri)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.useImage(uri)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) cameraPermission.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.sample_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        Text(
            text = stringResource(R.string.sample_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9A9AAE),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(Color.Black, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val preview = state.preview
            if (preview != null && !preview.isRecycled) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "SDK frame",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = stringResource(R.string.sample_no_frames),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        InfoRow(stringResource(R.string.sample_status), state.status)
        InfoRow(stringResource(R.string.sample_source), state.sourceLabel.ifBlank { "—" })
        InfoRow(stringResource(R.string.sample_fps), "%.1f".format(state.fps))
        InfoRow(stringResource(R.string.sample_resolution), "${state.width}x${state.height}")
        InfoRow(
            stringResource(R.string.sample_frames),
            "${state.delivered} / dropped ${state.dropped}",
        )
        state.savedFramePath?.let { path ->
            Text(
                text = path,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF3DDCB4),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    viewModel.usePhysicalCamera(lifecycleOwner, CameraSelector.LENS_FACING_BACK)
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }) { Text(stringResource(R.string.sample_camera_back)) }

            Button(onClick = {
                viewModel.usePhysicalCamera(lifecycleOwner, CameraSelector.LENS_FACING_FRONT)
            }) { Text(stringResource(R.string.sample_camera_front)) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) {
                Text(stringResource(R.string.sample_pick_video))
            }
            OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                Text(stringResource(R.string.sample_pick_image))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::requestFrameCapture) {
                Text(stringResource(R.string.sample_capture))
            }
            OutlinedButton(onClick = viewModel::togglePlayback) {
                Text("Play/Pause")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::closeSession) {
                Text(stringResource(R.string.sample_close))
            }
            OutlinedButton(onClick = viewModel::reopenSession) {
                Text(stringResource(R.string.sample_reopen))
            }
        }

        Text(
            text = stringResource(R.string.sample_log),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
        state.log.takeLast(12).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9A9AAE),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9A9AAE),
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}
