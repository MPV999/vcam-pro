package com.vcam.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vcam.core.database.MediaItemEntity
import com.vcam.core.database.MediaOrigins
import com.vcam.core.database.MediaRepository
import com.vcam.core.database.MediaTypes
import com.vcam.core.media.MediaProbe
import com.vcam.core.media.ThumbnailLoader
import com.vcam.core.storage.MediaStoreExporter
import com.vcam.core.storage.SafImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class LibraryFilter { ALL, PHOTOS, VIDEOS, FAVORITES }

data class LibraryUiState(
    val items: List<MediaItemEntity> = emptyList(),
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val loading: Boolean = true,
    val message: String? = null,
    val selected: MediaItemEntity? = null,
    val renaming: MediaItemEntity? = null,
)

class MediaLibraryViewModel(
    private val repository: MediaRepository,
    private val importer: SafImporter,
    private val exporter: MediaStoreExporter,
    private val mediaProbe: MediaProbe,
    val thumbnails: ThumbnailLoader,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow(LibraryFilter.ALL)

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeAll(), queryFlow, filterFlow) { items, query, filter ->
                Triple(items, query, filter)
            }.collect { (items, query, filter) ->
                val filtered = items.filter { item ->
                    val matchesQuery = query.isBlank() ||
                        item.displayName.contains(query, ignoreCase = true)
                    val matchesFilter = when (filter) {
                        LibraryFilter.ALL -> true
                        LibraryFilter.PHOTOS -> !item.isVideo
                        LibraryFilter.VIDEOS -> item.isVideo
                        LibraryFilter.FAVORITES -> item.isFavorite
                    }
                    matchesQuery && matchesFilter
                }
                _state.update {
                    it.copy(
                        items = filtered,
                        query = query,
                        filter = filter,
                        loading = false,
                        selected = filtered.firstOrNull { item -> item.id == it.selected?.id },
                    )
                }
            }
        }
    }

    fun setQuery(query: String) {
        queryFlow.value = query
    }

    fun setFilter(filter: LibraryFilter) {
        filterFlow.value = filter
    }

    fun select(item: MediaItemEntity?) = _state.update { it.copy(selected = item) }

    fun startRename(item: MediaItemEntity) = _state.update { it.copy(renaming = item) }

    fun cancelRename() = _state.update { it.copy(renaming = null) }

    fun confirmRename(item: MediaItemEntity, name: String) {
        viewModelScope.launch {
            repository.rename(item.id, name)
            _state.update { it.copy(renaming = null, message = "Renamed") }
        }
    }

    fun toggleFavorite(item: MediaItemEntity) {
        viewModelScope.launch {
            repository.setFavorite(item.id, !item.isFavorite)
        }
    }

    fun delete(item: MediaItemEntity) {
        viewModelScope.launch {
            val removed = repository.delete(item)
            _state.update {
                it.copy(
                    selected = null,
                    message = if (removed) "Deleted ${item.displayName}" else "Row removed, file kept",
                )
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            importer.import(uri)
                .onSuccess { imported ->
                    val info = mediaProbe.probe(imported.file)
                    val isVideo = imported.mimeType.startsWith("video/")
                    repository.add(
                        MediaItemEntity(
                            displayName = imported.displayName,
                            filePath = imported.file.absolutePath,
                            mimeType = imported.mimeType,
                            mediaType = if (isVideo) MediaTypes.VIDEO else MediaTypes.PHOTO,
                            sizeBytes = imported.sizeBytes,
                            width = info?.width ?: 0,
                            height = info?.height ?: 0,
                            durationMs = info?.durationMs ?: 0L,
                            createdAt = System.currentTimeMillis(),
                            origin = MediaOrigins.IMPORT,
                        ),
                    )
                    _state.update { it.copy(message = "Imported ${imported.displayName}") }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = "Import failed: ${error.message}") }
                }
        }
    }

    fun export(item: MediaItemEntity) {
        viewModelScope.launch {
            exporter.export(
                file = File(item.filePath),
                displayName = item.displayName,
                mimeType = item.mimeType,
                isVideo = item.isVideo,
            ).onSuccess { uri ->
                _state.update { it.copy(message = "Exported to $uri") }
            }.onFailure { error ->
                _state.update { it.copy(message = "Export failed: ${error.message}") }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    companion object {
        fun factory(
            repository: MediaRepository,
            importer: SafImporter,
            exporter: MediaStoreExporter,
            mediaProbe: MediaProbe,
            thumbnails: ThumbnailLoader,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MediaLibraryViewModel(repository, importer, exporter, mediaProbe, thumbnails)
            }
        }
    }
}
