package com.icecream.simplemediaplayer.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.icecream.simplemediaplayer.data.favorites.FavoritesDataSource
import com.icecream.simplemediaplayer.data.favorites.RecentStationsDataSource
import com.icecream.simplemediaplayer.data.model.RadioStation
import com.icecream.simplemediaplayer.data.preferences.PlaybackPrefsDataSource
import com.icecream.simplemediaplayer.data.preferences.StoredPlaybackState
import com.icecream.simplemediaplayer.data.repository.RadioRepository
import com.icecream.simplemediaplayer.player.RadioPlayerController
import com.icecream.simplemediaplayer.player.RadioService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject @UnstableApi constructor(
    application: Application,
    private val playerController: RadioPlayerController,
    private val playbackPrefs: PlaybackPrefsDataSource,
    private val favoritesDataSource: FavoritesDataSource,
    private val recentStationsDataSource: RecentStationsDataSource,
    private val radioRepository: RadioRepository
) : AndroidViewModel(application) {

    private val app = getApplication<Application>()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private val _currentStations = MutableStateFlow<List<RadioStation>>(emptyList())
    private var cachedStoredPlayback: StoredPlaybackState? = null
    private var favoriteUrls: Set<String> = emptySet()

    // Program/Song refresh state
    private var programRefreshJob: Job? = null
    private var songRefreshJob: Job? = null
    private var lastSongValue: String? = null
    private var songChangeTimestamp: Long = 0L
    private var currentStationUrl: String? = null

    init {
        viewModelScope.launch {
            favoritesDataSource.favorites.collectLatest { favorites ->
                favoriteUrls = favorites
                val currentStation = _state.value.currentStation
                _state.update { state ->
                    state.copy(isFavorite = currentStation?.url?.let(favorites::contains) ?: false)
                }
            }
        }
        viewModelScope.launch {
            val stored = playbackPrefs.playbackState.first()
            cachedStoredPlayback = stored
            applyStoredPlayback(stored)
        }
        viewModelScope.launch {
            playerController.playerState.collectLatest { playerState ->
                val resolvedStation =
                    findStationByUrl(playerState.stationUrl) ?: _state.value.currentStation
                _state.update {
                    val isFavorite = resolvedStation?.url?.let(favoriteUrls::contains) ?: false
                    it.copy(
                        currentStation = resolvedStation,
                        isPlaying = playerState.isPlaying,
                        isBuffering = playerState.isBuffering,
                        isFavorite = isFavorite
                    )
                }

                // Check if station changed
                if (resolvedStation?.url != currentStationUrl) {
                    currentStationUrl = resolvedStation?.url
                    onStationChanged(resolvedStation)
                }

                playbackPrefs.save(
                    _currentStations.value,
                    playerState.stationUrl,
                    playerState.isPlaying
                )
            }
        }
    }

    private fun onStationChanged(station: RadioStation?) {
        // Cancel existing refresh jobs
        programRefreshJob?.cancel()
        songRefreshJob?.cancel()

        // Record to recent stations
        station?.url?.let { url ->
            viewModelScope.launch {
                recentStationsDataSource.addRecentStation(url)
            }
        }

        lastSongValue = null
        songChangeTimestamp = 0L

        // Clear existing data
        _state.update { it.copy(programTitle = null, songTitle = null) }

        station?.let {
            if (!it.program.isNullOrEmpty()) {
                startProgramRefresh(it.program)
            }
            if (!it.song.isNullOrEmpty()) {
                startSongRefresh(it.song)
            }
        }
    }

    private fun startProgramRefresh(programUrl: String) {
        programRefreshJob = viewModelScope.launch {
            val baseUrl = "https://radio.yuntae.in"
            val fullUrl = baseUrl + programUrl

            while (true) {
                // Fetch program info
                radioRepository.fetchProgramInfo(fullUrl).onSuccess { programInfo ->
                    _state.update { it.copy(programTitle = programInfo.title) }
                }

                // 5분 단위로 갱신
                val now = LocalTime.now()
                val currentMinutes = now.minute
                val nextRefreshMinutes = ((currentMinutes / 5) + 1) * 5
                val minutesToWait = if (nextRefreshMinutes >= 60) {
                    60 - currentMinutes
                } else {
                    nextRefreshMinutes - currentMinutes
                }
                val secondsToWait = 60 - now.second
                val totalSecondsToWait = (minutesToWait * 60 + secondsToWait).toLong()

                delay(totalSecondsToWait * 1000)
            }
        }
    }

    private fun startSongRefresh(songUrl: String) {
        songRefreshJob = viewModelScope.launch {
            val baseUrl = "https://radio.yuntae.in"
            val fullUrl = baseUrl + songUrl

            while (true) {
                // 이전과 달라지면 2분간 갱신 중단
                val currentTime = System.currentTimeMillis()
                val timeSinceChange = currentTime - songChangeTimestamp
                val twoMinutesInMillis = 2 * 60 * 1000L

                if (songChangeTimestamp == 0L || timeSinceChange >= twoMinutesInMillis) {
                    // Fetch song info
                    radioRepository.fetchSongInfo(fullUrl).onSuccess { songInfo ->
                        val newSongValue = songInfo.song

                        // Check if song changed
                        if (lastSongValue != null && lastSongValue != newSongValue) {
                            // Song changed, start 2-minute pause
                            songChangeTimestamp = currentTime
                        }

                        lastSongValue = newSongValue
                        _state.update { it.copy(songTitle = newSongValue) }
                    }
                }

                delay(30_000)
            }
        }
    }

    fun restoreLastPlayback(autoPlay: Boolean) {
        viewModelScope.launch {
            val stored = cachedStoredPlayback ?: playbackPrefs.playbackState.first().also {
                cachedStoredPlayback = it
                applyStoredPlayback(it)
            }
            val targetStation = stored.lastStationUrl?.let { url ->
                stored.queue.firstOrNull { it.url == url }
            } ?: _state.value.currentStation
            if (targetStation != null && autoPlay) {
                playStation(targetStation, autostart = true)
            }
        }
    }

    private fun applyStoredPlayback(stored: StoredPlaybackState) {
        if (stored.queue.isNotEmpty()) {
            val activeUrl = stored.lastStationUrl ?: _state.value.currentStation?.url
            _currentStations.value = stored.queue
            playerController.updateStations(stored.queue, activeStationUrl = activeUrl)
        }
        stored.lastStationUrl?.let { url ->
            stored.queue.firstOrNull { it.url == url }?.let { matched ->
                _state.update {
                    it.copy(
                        currentStation = matched,
                        isFavorite = matched.url.let(favoriteUrls::contains)
                    )
                }
            }
        }
    }

    private fun ensureServiceRunning() {
        val intent = Intent(app, RadioService::class.java)
        ContextCompat.startForegroundService(app, intent)
    }

    private fun findStationByUrl(url: String?, stations: List<RadioStation> = _currentStations.value): RadioStation? {
        if (url.isNullOrEmpty()) return null
        return stations.firstOrNull { it.url == url }
    }

    fun setCurrentStations(stations: List<RadioStation>) {
        if (stations.isEmpty()) return
        if (_state.value.isPlaying || _state.value.currentStation != null) return
        if (stations == _currentStations.value) return
        _currentStations.value = stations
        playerController.updateStations(stations, activeStationUrl = _state.value.currentStation?.url)
    }

    private fun playStation(station: RadioStation, autostart: Boolean = false) {
        ensureServiceRunning()
        if (_currentStations.value.isEmpty()) {
            _currentStations.value = listOf(station)
            playerController.updateStations(_currentStations.value)
        }
        playerController.playStream(station.title, "https://radio.yuntae.in" + station.url)
        if (!autostart) {
            val isFavorite = station.url.let(favoriteUrls::contains)
            _state.update { it.copy(currentStation = station, isPlaying = true, isFavorite = isFavorite) }
        }
        viewModelScope.launch {
            playbackPrefs.save(_currentStations.value, station.url, true)
        }
    }

    fun playStation(station: RadioStation) = playStation(station, autostart = false)

    fun togglePlayPause() {
        if (_state.value.currentStation != null) {
            ensureServiceRunning()
            playerController.togglePlayPause()
            viewModelScope.launch {
                playbackPrefs.save(
                    _currentStations.value,
                    _state.value.currentStation?.url,
                    !_state.value.isPlaying
                )
            }
        }
    }

    fun toggleFavorite() {
        val station = _state.value.currentStation ?: return
        viewModelScope.launch {
            favoritesDataSource.toggleFavorite(station.url)
        }
    }

    fun playPrev() {
        ensureServiceRunning()
        playerController.playPrev()
    }

    fun playNext() {
        ensureServiceRunning()
        playerController.playNext()
    }
}
