package com.icecream.simplemediaplayer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore: DataStore<Preferences> = context.settingsStore

    private val autoPlayKey = booleanPreferencesKey("auto_play_on_start")
    private val startTabKey = stringPreferencesKey("start_tab")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val uiScaleKey = floatPreferencesKey("ui_scale")

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            autoPlayOnStart = prefs[autoPlayKey] ?: false,
            startTab = prefs[startTabKey] ?: StartTab.HOME.value,
            themeMode = prefs[themeModeKey] ?: ThemeMode.SYSTEM.value,
            uiScale = prefs[uiScaleKey] ?: 1.0f
        )
    }

    suspend fun setAutoPlayOnStart(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[autoPlayKey] = enabled
        }
    }

    suspend fun setStartTab(tab: String) {
        dataStore.edit { prefs ->
            prefs[startTabKey] = tab
        }
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[themeModeKey] = mode
        }
    }

    suspend fun setUiScale(scale: Float) {
        dataStore.edit { prefs ->
            prefs[uiScaleKey] = scale
        }
    }
}

data class AppSettings(
    val autoPlayOnStart: Boolean = false,
    val startTab: String = StartTab.HOME.value,
    val themeMode: String = ThemeMode.SYSTEM.value,
    val uiScale: Float = 1.0f
)

enum class StartTab(val value: String, val displayName: String) {
    HOME("home", "전체"),
    FAVORITES("favorites", "자주 듣는")
}

enum class ThemeMode(val value: String, val displayName: String) {
    LIGHT("light", "라이트"),
    DARK("dark", "다크"),
    SYSTEM("system", "시스템")
}

