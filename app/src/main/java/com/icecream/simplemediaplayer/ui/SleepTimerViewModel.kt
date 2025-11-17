package com.icecream.simplemediaplayer.ui

import android.app.Application
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class SleepTimerState(
    val isRunning: Boolean = false,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0
)

@HiltViewModel
class SleepTimerViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val app = getApplication<Application>()

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var timerJob: Job? = null

    @OptIn(UnstableApi::class)
    fun startTimer(hours: Int, minutes: Int) {
        val totalSeconds = (hours * 3600) + (minutes * 60)

        _state.value = SleepTimerState(
            isRunning = true,
            totalSeconds = totalSeconds,
            remainingSeconds = totalSeconds
        )

        // Start foreground service
        val intent = Intent(app, com.icecream.simplemediaplayer.player.SleepTimerService::class.java).apply {
            putExtra("TOTAL_SECONDS", totalSeconds)
        }
        ContextCompat.startForegroundService(app, intent)

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                _state.value = _state.value.copy(remainingSeconds = remaining)
            }
            // Timer finished
            stopApp()
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        _state.value = SleepTimerState()

        // Stop service
        val intent = Intent(app, com.icecream.simplemediaplayer.player.SleepTimerService::class.java)
        app.stopService(intent)
    }

    private fun stopApp() {
        // Stop all playback and kill process
        val radioApp = app as? com.icecream.simplemediaplayer.RadioApp
        radioApp?.stopAllPlayback()

        // Stop service
        val intent = Intent(app, com.icecream.simplemediaplayer.player.SleepTimerService::class.java)
        app.stopService(intent)

        // Kill the app
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    fun getRemainingTimeString(): String {
        val remaining = _state.value.remainingSeconds
        val hours = remaining / 3600
        val minutes = (remaining % 3600) / 60
        val seconds = remaining % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

