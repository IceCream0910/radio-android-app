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
    private var favoriteUrls: List<String> = emptyList()

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
                        isFavorite = isFavorite,
                        hasPrev = playerState.hasPrev,
                        hasNext = playerState.hasNext
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
        programRefreshJob?.cancel()
        songRefreshJob?.cancel()

        station?.url?.let { url ->
            viewModelScope.launch {
                recentStationsDataSource.addRecentStation(url)
            }
        }

        lastSongValue = null
        songChangeTimestamp = 0L

        _state.update { it.copy(programTitle = null, songTitle = null) }

        station?.let {
            if (!it.program.isNullOrEmpty()) {
                startProgramRefresh(it.program)
            }
            if (!it.song.isNullOrEmpty()) {
                startSongRefresh(it.song)
            }
        }

        // Update media session artist to default when station changes (until program fetched)
        playerController.updateCurrentArtist(null)
    }

    private fun startProgramRefresh(programUrl: String) {
        programRefreshJob = viewModelScope.launch {
            val baseUrl = "https://radio.yuntae.in"
            val fullUrl = baseUrl + programUrl

            radioRepository.fetchProgramInfo(fullUrl).onSuccess { programInfo ->
                _state.update { it.copy(programTitle = programInfo.title) }
                // Reflect program title to media session artist
                playerController.updateCurrentArtist(programInfo.title)
            }.onFailure { e ->
                android.util.Log.e("PlayerViewModel", "Failed to fetch program info", e)
            }

            while (true) {
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

                // 다음 갱신 실행
                radioRepository.fetchProgramInfo(fullUrl).onSuccess { programInfo ->
                    _state.update { it.copy(programTitle = programInfo.title) }
                    playerController.updateCurrentArtist(programInfo.title)
                }.onFailure { e ->
                    android.util.Log.e("PlayerViewModel", "Failed to fetch program info", e)
                }
            }
        }
    }

    private fun startSongRefresh(songUrl: String) {
        songRefreshJob = viewModelScope.launch {
            val baseUrl = "https://radio.yuntae.in"
            val fullUrl = baseUrl + songUrl

            radioRepository.fetchSongInfo(fullUrl).onSuccess { songInfo ->
                val newSongValue = songInfo.song
                lastSongValue = newSongValue
                _state.update { it.copy(songTitle = newSongValue) }
            }.onFailure { e ->
                android.util.Log.e("PlayerViewModel", "Failed to fetch song info", e)
            }

            while (true) {
                delay(30_000)

                val currentTime = System.currentTimeMillis()
                val timeSinceChange = currentTime - songChangeTimestamp
                val oneMinuteInMillis = 1 * 60 * 1000L

                // 곡이 바뀐 지 1분이 지나지 않았으면 갱신 스킵
                if (songChangeTimestamp != 0L && timeSinceChange < oneMinuteInMillis) {
                    continue
                }

                radioRepository.fetchSongInfo(fullUrl).onSuccess { songInfo ->
                    val newSongValue = songInfo.song

                    if (lastSongValue != null && lastSongValue != newSongValue) {
                        songChangeTimestamp = currentTime
                    }

                    lastSongValue = newSongValue
                    _state.update { it.copy(songTitle = newSongValue) }
                }.onFailure { e ->
                    android.util.Log.e("PlayerViewModel", "Failed to fetch song info", e)
                }
            }
        }
    }

    fun restoreLastPlayback(autoPlay: Boolean) {
        viewModelScope.launch {
            try {
                val stored = cachedStoredPlayback ?: playbackPrefs.playbackState.first().also {
                    cachedStoredPlayback = it
                    applyStoredPlayback(it)
                }

                val targetStation = stored.lastStationUrl?.let { url ->
                    stored.queue.firstOrNull { it.url == url }
                } ?: _state.value.currentStation

                if (targetStation != null && autoPlay) {
                    android.util.Log.d("PlayerViewModel", "Restoring playback: ${targetStation.title}, autoPlay=$autoPlay")
                    // 포그라운드 서비스 시작 확인
                    ensureServiceRunning()

                    // 약간의 지연을 두고 재생 시도 (서비스 초기화 완료 대기)
                    delay(500)
                    playStation(targetStation, playlist = _currentStations.value, autostart = true)
                } else {
                    android.util.Log.d("PlayerViewModel", "No station to restore or autoPlay disabled")
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Failed to restore playback", e)
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
        val found = stations.firstOrNull { it.url == url }
        return found
    }

    fun setCurrentStations(stations: List<RadioStation>) {
        if (stations.isEmpty()) return
        if (stations == _currentStations.value) return

        // Do not override queue if we already have an active station (playing or paused).
        if (_state.value.currentStation != null) return

        _currentStations.value = stations
        playerController.updateStations(stations, activeStationUrl = null)
    }

    private fun playStation(
        station: RadioStation,
        playlist: List<RadioStation>?,
        autostart: Boolean = false
    ) {
        android.util.Log.d("PlayerViewModel", "playStation: ${station.title}, artwork=${station.artwork}")

        ensureServiceRunning()
        val playlistCandidate = playlist ?: _currentStations.value
        val effectiveList = when {
            playlistCandidate.isNullOrEmpty() -> listOf(station)
            playlistCandidate.any { it.url == station.url } -> playlistCandidate
            else -> playlistCandidate + station
        }

        val stationInList = effectiveList.any { it.url == station.url }
        val forcePlaylist = playlist != null && effectiveList != _currentStations.value

        if (!stationInList || forcePlaylist) {
            android.util.Log.d("PlayerViewModel", "playStation: rebuilding playlist (force=${forcePlaylist})")
            _currentStations.value = effectiveList
            playerController.setPlaylistAndPlay(effectiveList, station.url)
        } else {
            _currentStations.value = effectiveList
            if (!playerController.hasMediaItem(station.url)) {
                android.util.Log.d("PlayerViewModel", "playStation: media item missing in ExoPlayer -> resetting playlist")
                playerController.setPlaylistAndPlay(effectiveList, station.url)
            } else {
                playerController.playStationByUrl("https://radio.yuntae.in" + station.url)
            }
        }

        if (!autostart) {
            val isFavorite = station.url.let(favoriteUrls::contains)
            _state.update { it.copy(currentStation = station, isPlaying = true, isFavorite = isFavorite) }
        }
        viewModelScope.launch {
            playbackPrefs.save(_currentStations.value, station.url, true)
        }
    }

    fun playStation(station: RadioStation) = playStation(station, playlist = _currentStations.value, autostart = false)

    fun playStation(station: RadioStation, playlist: List<RadioStation>) = playStation(station, playlist = playlist, autostart = false)

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
