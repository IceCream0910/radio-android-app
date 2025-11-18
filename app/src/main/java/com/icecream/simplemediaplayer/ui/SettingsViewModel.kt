package com.icecream.simplemediaplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icecream.simplemediaplayer.data.preferences.AppSettings
import com.icecream.simplemediaplayer.data.preferences.SettingsDataSource
import com.icecream.simplemediaplayer.data.preferences.StartTab
import com.icecream.simplemediaplayer.data.preferences.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataSource: SettingsDataSource
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsDataSource.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    fun setAutoPlayOnStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataSource.setAutoPlayOnStart(enabled)
        }
    }

    fun setStartTab(tab: StartTab) {
        viewModelScope.launch {
            settingsDataSource.setStartTab(tab.value)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsDataSource.setThemeMode(mode.value)
        }
    }

    fun setUiScale(scale: Float) {
        viewModelScope.launch {
            settingsDataSource.setUiScale(scale)
        }
    }

    fun setNeedsDataRestore(needs: Boolean) {
        viewModelScope.launch {
            settingsDataSource.setNeedsDataRestore(needs)
        }
    }
}

