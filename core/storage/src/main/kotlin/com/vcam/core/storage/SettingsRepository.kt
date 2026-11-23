package com.vcam.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage { SYSTEM, ENGLISH, VIETNAMESE }

/** User settings, all stored locally with DataStore. No account, no sync. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val defaultWidth: Int = 1280,
    val defaultHeight: Int = 720,
    val defaultFps: Int = 30,
    val defaultFormat: String = "RGBA_8888",
    val defaultScaleMode: String = "CROP",
    val mirrorPreview: Boolean = false,
    val keepScreenOn: Boolean = true,
    val showGrid: Boolean = true,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("vcam_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val language = stringPreferencesKey("language")
        val width = intPreferencesKey("default_width")
        val height = intPreferencesKey("default_height")
        val fps = intPreferencesKey("default_fps")
        val format = stringPreferencesKey("default_format")
        val scaleMode = stringPreferencesKey("default_scale_mode")
        val mirrorPreview = booleanPreferencesKey("mirror_preview")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val showGrid = booleanPreferencesKey("show_grid")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        val defaults = AppSettings()
        AppSettings(
            themeMode = preferences[Keys.themeMode]?.let { value ->
                runCatching { ThemeMode.valueOf(value) }.getOrNull()
            } ?: defaults.themeMode,
            language = preferences[Keys.language]?.let { value ->
                runCatching { AppLanguage.valueOf(value) }.getOrNull()
            } ?: defaults.language,
            defaultWidth = preferences[Keys.width] ?: defaults.defaultWidth,
            defaultHeight = preferences[Keys.height] ?: defaults.defaultHeight,
            defaultFps = preferences[Keys.fps] ?: defaults.defaultFps,
            defaultFormat = preferences[Keys.format] ?: defaults.defaultFormat,
            defaultScaleMode = preferences[Keys.scaleMode] ?: defaults.defaultScaleMode,
            mirrorPreview = preferences[Keys.mirrorPreview] ?: defaults.mirrorPreview,
            keepScreenOn = preferences[Keys.keepScreenOn] ?: defaults.keepScreenOn,
            showGrid = preferences[Keys.showGrid] ?: defaults.showGrid,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(current())
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.themeMode] = updated.themeMode.name
            preferences[Keys.language] = updated.language.name
            preferences[Keys.width] = updated.defaultWidth
            preferences[Keys.height] = updated.defaultHeight
            preferences[Keys.fps] = updated.defaultFps
            preferences[Keys.format] = updated.defaultFormat
            preferences[Keys.scaleMode] = updated.defaultScaleMode
            preferences[Keys.mirrorPreview] = updated.mirrorPreview
            preferences[Keys.keepScreenOn] = updated.keepScreenOn
            preferences[Keys.showGrid] = updated.showGrid
        }
    }
}
