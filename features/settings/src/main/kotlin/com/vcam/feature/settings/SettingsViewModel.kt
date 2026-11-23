package com.vcam.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vcam.core.storage.AppLanguage
import com.vcam.core.storage.AppSettings
import com.vcam.core.storage.SettingsRepository
import com.vcam.core.storage.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setLanguage(language: AppLanguage) = update { it.copy(language = language) }

    fun setMirrorPreview(enabled: Boolean) = update { it.copy(mirrorPreview = enabled) }

    fun setKeepScreenOn(enabled: Boolean) = update { it.copy(keepScreenOn = enabled) }

    fun setShowGrid(enabled: Boolean) = update { it.copy(showGrid = enabled) }

    fun setDefaultFps(fps: Int) = update { it.copy(defaultFps = fps) }

    fun setDefaultFormat(format: String) = update { it.copy(defaultFormat = format) }

    fun setDefaultScaleMode(mode: String) = update { it.copy(defaultScaleMode = mode) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    companion object {
        fun factory(repository: SettingsRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
