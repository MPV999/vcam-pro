package com.vcam.feature.library

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vcam.core.common.formatBytes
import com.vcam.core.common.formatDuration
import com.vcam.core.database.MediaItemEntity
import com.vcam.core.database.MediaRepository
import com.vcam.core.media.MediaProbe
import com.vcam.core.media.ThumbnailLoader
import com.vcam.core.storage.MediaStoreExporter
import com.vcam.core.storage.SafImporter

@Composable
fun MediaLibraryScreen(
    repository: MediaRepository,
    importer: SafImporter,
    exporter: MediaStoreExporter,
    mediaProbe: MediaProbe,
    thumbnails: ThumbnailLoader,
    onUseAsVirtualSource: (MediaItemEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MediaLibraryViewModel = viewModel(
        factory = MediaLibraryViewModel.factory(repository, importer, exporter, mediaProbe, thumbnails),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { picker.launch(arrayOf("video/*", "image/*")) },
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.library_import))
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.library_search)) },
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LibraryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = {
                        Text(
                            text = when (filter) {
                                LibraryFilter.ALL -> stringResource(R.string.library_filter_all)
                                LibraryFilter.PHOTOS -> stringResource(R.string.library_filter_photos)
                                LibraryFilter.VIDEOS -> stringResource(R.string.library_filter_videos)
                                LibraryFilter.FAVORITES -> stringResource(R.string.library_filter_favorites)
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        if (!state.loading && state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.library_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.items, key = { it.id }) { item ->
                MediaCard(
                    item = item,
                    thumbnails = viewModel.thumbnails,
                    onClick = { viewModel.select(item) },
                )
            }
        }
    }

    state.selected?.let { item ->
        ItemDetailDialog(
            item = item,
            onDismiss = { viewModel.select(null) },
            onToggleFavorite = { viewModel.toggleFavorite(item) },
            onRename = { viewModel.startRename(item) },
            onExport = { viewModel.export(item) },
            onDelete = { viewModel.delete(item) },
            onUseAsSource = {
                viewModel.select(null)
                onUseAsVirtualSource(item)
            },
        )
    }

    state.renaming?.let { item ->
        var text by remember(item.id) { mutableStateOf(item.displayName) }
        AlertDialog(
            onDismissRequest = viewModel::cancelRename,
            title = { Text(stringResource(R.string.library_rename)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRename(item, text) }) {
                    Text(stringResource(R.string.library_save))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRename) {
                    Text(stringResource(R.string.library_cancel))
                }
            },
        )
    }
}

@Composable
private fun MediaCard(
    item: MediaItemEntity,
    thumbnails: ThumbnailLoader,
    onClick: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, item.id) {
        value = thumbnails.load(item.filePath, item.isVideo)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.Black),
            ) {
                bitmap?.let { image ->
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (item.isVideo) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                    )
                }
                if (item.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp),
                    )
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (item.isVideo) formatDuration(item.durationMs) else formatBytes(item.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ItemDetailDialog(
    item: MediaItemEntity,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onUseAsSource: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                DetailRow(stringResource(R.string.library_detail_type), item.mediaType)
                DetailRow(
                    stringResource(R.string.library_detail_resolution),
                    if (item.width > 0) "${item.width}x${item.height}" else "—",
                )
                if (item.isVideo) {
                    DetailRow(stringResource(R.string.library_detail_duration), formatDuration(item.durationMs))
                }
                DetailRow(stringResource(R.string.library_detail_size), formatBytes(item.sizeBytes))
                DetailRow(stringResource(R.string.library_detail_mime), item.mimeType)
                DetailRow(stringResource(R.string.library_detail_origin), item.origin)
                DetailRow(stringResource(R.string.library_detail_path), item.filePath, small = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onUseAsSource) {
                Text(stringResource(R.string.library_use_as_source))
            }
        },
        dismissButton = {
            Column {
                Row {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.library_favorite),
                        )
                    }
                    TextButton(onClick = onRename) { Text(stringResource(R.string.library_rename)) }
                    TextButton(onClick = onExport) { Text(stringResource(R.string.library_export)) }
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(R.string.library_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String, small: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
