package com.icecream.simplemediaplayer.player

import android.app.Notification
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.ui.PlayerNotificationManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.icecream.simplemediaplayer.RadioApp
import com.icecream.simplemediaplayer.data.favorites.FavoritesDataSource
import com.icecream.simplemediaplayer.data.favorites.RecentStationsDataSource
import com.icecream.simplemediaplayer.data.model.RadioStation
import com.icecream.simplemediaplayer.data.repository.RadioRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class RadioService : MediaLibraryService() {

    @Inject
    lateinit var playerController: RadioPlayerController

    @Inject
    lateinit var notificationManager: RadioNotificationManager

    @Inject
    lateinit var favoritesDataSource: FavoritesDataSource

    @Inject
    lateinit var recentStationsDataSource: RecentStationsDataSource

    @Inject
    lateinit var repository: RadioRepository

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var isForegroundService = false

    // 캐시 데이터
    private var cachedFavorites: List<String> = emptyList()
    private var cachedRecentStations: List<String> = emptyList()
    private var isInitialized = false

    private val cityLabel: Map<String, String> = mapOf(
        "seoul" to "수도권",
        "busan" to "부산·울산·경남",
        "daegu" to "대구·경북",
        "gwangju" to "광주·전남",
        "daejeon" to "대전·세종·충남",
        "jeonbuk" to "전북",
        "gangwon" to "강원",
        "chungbuk" to "충북",
        "jeju" to "제주"
    )

    override fun onCreate() {
        super.onCreate()
        runBlocking {
            try {
                repository.fetchStations()
                android.util.Log.d("RadioService", "Stations loaded: ${repository.getAllStations().size} items")

                cachedFavorites = favoritesDataSource.favorites.first()
                android.util.Log.d("RadioService", "Initial favorites cached: ${cachedFavorites.size} items")

                cachedRecentStations = recentStationsDataSource.recentStations.first()
                android.util.Log.d("RadioService", "Initial recent stations cached: ${cachedRecentStations.size} items")
            } catch (e: Exception) {
                android.util.Log.e("RadioService", "Failed to load initial data", e)
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                favoritesDataSource.favorites.collect { favorites ->
                    cachedFavorites = favorites
                    android.util.Log.d("RadioService", "Favorites updated: ${favorites.size} items")
                }
            } catch (e: Exception) {
                android.util.Log.e("RadioService", "Failed to collect favorites", e)
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                recentStationsDataSource.recentStations.collect { recent ->
                    cachedRecentStations = recent
                    android.util.Log.d("RadioService", "Recent stations updated: ${recent.size} items")
                }
            } catch (e: Exception) {
                android.util.Log.e("RadioService", "Failed to collect recent stations", e)
            }
        }

        // Create MediaLibrarySession with callback
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            playerController.player,
            MediaLibrarySessionCallback()
        ).build()

        isInitialized = true

        // Build notification
        notificationManager.buildNotification(
            mediaSession = mediaLibrarySession!!,
            notificationListener = object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    if (isForegroundService) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        isForegroundService = false
                    }

                    val player = playerController.player
                    val canStop = dismissedByUser &&
                        player.playbackState == Player.STATE_IDLE &&
                        player.mediaItemCount == 0

                    if (canStop) {
                        stopAndRelease()
                    }
                }

                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    if (ongoing && !isForegroundService) {
                        startForeground(notificationId, notification)
                        isForegroundService = true
                    }
                }
            }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP_ALL) {
            stopAndRelease()
            return START_NOT_STICKY
        }
        // 서비스가 종료되면 시스템이 자동으로 재시작하도록 설정
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopAndRelease()
    }

    override fun onDestroy() {
        stopAndRelease()
        super.onDestroy()
    }

    private fun stopAndRelease() {
        if (isForegroundService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundService = false
        }
        try {
            notificationManager.hideNotification()
        } catch (_: Throwable) {}
        try {
            mediaLibrarySession?.player?.pause()
        } catch (_: Throwable) {}
        try {
            playerController.release()
        } catch (_: Throwable) {}
        mediaLibrarySession = null
        (applicationContext as? RadioApp)?.allowBackgroundPlayback = false

        (application as? RadioApp)?.stopAllPlayback()
        stopSelf()
    }

    // MediaLibrarySession Callback for Android Auto
    inner class MediaLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId("root")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("라디오 스테이션")
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .build()
                )
                .build()

            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (!isInitialized) {
                android.util.Log.w("RadioService", "Service not initialized yet, returning empty list")
                return Futures.immediateFuture(
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                )
            }

            return when (parentId) {
                "root" -> {
                    // Root: Show "Favorites" and city folders
                    val items = mutableListOf<MediaItem>()

                    // Add Favorites folder (only if has favorites)
                    if (cachedFavorites.isNotEmpty()) {
                        items.add(
                            MediaItem.Builder()
                                .setMediaId("favorites")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle("자주 듣는")
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                        .setIsPlayable(false)
                                        .setIsBrowsable(true)
                                        .build()
                                )
                                .build()
                        )
                    }

                    val stationsByCity = getStationsByCity()
                    if (stationsByCity.isEmpty()) {
                        android.util.Log.w("RadioService", "No stations loaded yet")
                    } else {
                        android.util.Log.d("RadioService", "Loaded ${stationsByCity.size} city folders")
                        val cityItems = stationsByCity.keys.sorted().map { city ->
                            MediaItem.Builder()
                                .setMediaId("city_$city")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(cityLabel[city] ?: city)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                        .setIsPlayable(false)
                                        .setIsBrowsable(true)
                                        .build()
                                )
                                .build()
                        }
                        items.addAll(cityItems)
                    }

                    Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                "recent" -> {
                    // Show recently played stations (using cache)
                    val allStations = getAllStations()
                    if (allStations.isEmpty()) {
                        android.util.Log.w("RadioService", "No stations available for recent")
                        return Futures.immediateFuture(
                            LibraryResult.ofItemList(ImmutableList.of(), params)
                        )
                    }

                    val recentStations = cachedRecentStations.mapNotNull { url ->
                        allStations.find { it.url == url }
                    }

                    val stationItems = recentStations.map { station ->
                        createStationMediaItem(station)
                    }

                    Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(stationItems), params)
                    )
                }

                "favorites" -> {
                    // Show favorite stations (using cache)
                    val allStations = getAllStations()
                    if (allStations.isEmpty()) {
                        android.util.Log.w("RadioService", "No stations available for favorites")
                        return Futures.immediateFuture(
                            LibraryResult.ofItemList(ImmutableList.of(), params)
                        )
                    }

                    val favoriteStations = allStations.filter { cachedFavorites.contains(it.url) }

                    val stationItems = favoriteStations.map { station ->
                        createStationMediaItem(station)
                    }

                    Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(stationItems), params)
                    )
                }

                else -> {
                    if (parentId.startsWith("city_")) {
                        val city = parentId.removePrefix("city_")
                        val stations = getStationsByCity()[city] ?: emptyList()

                        if (stations.isEmpty()) {
                            android.util.Log.w("RadioService", "No stations found for city: $city")
                        } else {
                            android.util.Log.d("RadioService", "Loaded ${stations.size} stations for city: $city")
                        }

                        val stationItems = stations.map { station ->
                            createStationMediaItem(station)
                        }
                        Futures.immediateFuture(
                            LibraryResult.ofItemList(ImmutableList.copyOf(stationItems), params)
                        )
                    } else {
                        android.util.Log.w("RadioService", "Unknown parentId: $parentId")
                        Futures.immediateFuture(
                            LibraryResult.ofItemList(ImmutableList.of<MediaItem>(), params)
                        )
                    }
                }
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!isInitialized) {
                android.util.Log.w("RadioService", "Service not initialized, cannot get item: $mediaId")
                return Futures.immediateFuture(
                    LibraryResult.ofError(SessionError.ERROR_INVALID_STATE)
                )
            }

            if (mediaId.startsWith("station_")) {
                val stationUrl = mediaId.removePrefix("station_")
                val allStations = getAllStations()

                if (allStations.isEmpty()) {
                    android.util.Log.e("RadioService", "No stations available, cannot get item: $stationUrl")
                    return Futures.immediateFuture(
                        LibraryResult.ofError(SessionError.ERROR_INVALID_STATE)
                    )
                }

                val station = allStations.find { it.url == stationUrl }

                if (station != null) {
                    android.util.Log.d("RadioService", "Found station: ${station.title}")
                    val mediaItem = createPlayableStationMediaItem(station)
                    return Futures.immediateFuture(LibraryResult.ofItem(mediaItem, null))
                } else {
                    android.util.Log.w("RadioService", "Station not found: $stationUrl")
                }
            }

            return Futures.immediateFuture(
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            )
        }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            if (!isInitialized) {
                android.util.Log.w("RadioService", "Service not initialized, returning original items")
                return Futures.immediateFuture(mediaItems)
            }

            val updatedItems = mutableListOf<MediaItem>()

            mediaItems.forEach { mediaItem ->
                if (mediaItem.mediaId.startsWith("station_")) {
                    val stationUrl = mediaItem.mediaId.removePrefix("station_")
                    val allStations = getAllStations()

                    if (allStations.isEmpty()) {
                        android.util.Log.e("RadioService", "No stations available for adding media items")
                        // Return original item to avoid infinite loading
                        updatedItems.add(mediaItem)
                        return@forEach
                    }

                    val selectedStation = allStations.find { it.url == stationUrl }

                    if (selectedStation != null) {
                        android.util.Log.d("RadioService", "Adding station to queue: ${selectedStation.title}")
                        // 선택한 스테이션이 속한 카테고리의 모든 스테이션을 큐에 추가
                        val categoryStations = getCategoryStations(selectedStation)

                        if (categoryStations.isEmpty()) {
                            android.util.Log.w("RadioService", "No category stations found, adding single station")
                            updatedItems.add(createPlayableStationMediaItem(selectedStation))
                        } else {
                            // 선택한 스테이션이 먼저 재생되도록 정렬
                            val selectedIndex = categoryStations.indexOfFirst { it.url == stationUrl }
                            val reorderedStations = if (selectedIndex >= 0) {
                                categoryStations.drop(selectedIndex) + categoryStations.take(selectedIndex)
                            } else {
                                categoryStations
                            }

                            // 모든 스테이션을 재생 가능한 MediaItem으로 변환하여 추가
                            reorderedStations.forEach { station ->
                                updatedItems.add(createPlayableStationMediaItem(station))
                            }
                        }
                    } else {
                        android.util.Log.w("RadioService", "Station not found: $stationUrl, adding original item")
                        updatedItems.add(mediaItem)
                    }
                } else {
                    updatedItems.add(mediaItem)
                }
            }

            android.util.Log.d("RadioService", "Total media items added: ${updatedItems.size}")
            return Futures.immediateFuture(updatedItems)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val searchQuery = query.lowercase().trim()
            val allStations = getAllStations()

            val searchResultCount = allStations.count { station ->
                station.title.lowercase().contains(searchQuery)
            }

            session.notifySearchResultChanged(browser, query, searchResultCount, params)

            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val searchQuery = query.lowercase().trim()
            val allStations = getAllStations()

            val searchResults = allStations.filter { station ->
                station.title.lowercase().contains(searchQuery)
            }.map { station ->
                createPlayableStationMediaItem(station)
            }

            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(searchResults), params)
            )
        }

        private fun createStationMediaItem(station: RadioStation): MediaItem {
            return MediaItem.Builder()
                .setMediaId("station_${station.url}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.title)
                        .setSubtitle(station.city)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .build()
                )
                .build()
        }

        private fun createPlayableStationMediaItem(station: RadioStation): MediaItem {
            return MediaItem.Builder()
                .setMediaId("station_${station.url}")
                .setUri("https://radio.yuntae.in${station.url}")
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.title)
                        .setSubtitle(station.city)
                        .setArtist("라디오 스트리밍 중")
                        .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                        .setArtworkUri(Uri.parse(station.artwork ?: "https://i.imgur.com/u7N8nbD.png"))
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .build()
                )
                .build()
        }

        private fun getStationsByCity(): Map<String, List<RadioStation>> {
            return repository.getStationsByCity()
        }

        private fun getAllStations(): List<RadioStation> {
            return repository.getAllStations()
        }

        private fun getCategoryStations(selectedStation: RadioStation): List<RadioStation> {
            // 먼저 즐겨찾기에 있는지 확인 (캐시 사용)
            if (cachedFavorites.contains(selectedStation.url)) {
                val allStations = getAllStations()
                return allStations.filter { cachedFavorites.contains(it.url) }
            }

            // 최근 재생 목록에 있는지 확인 (캐시 사용)
            if (cachedRecentStations.contains(selectedStation.url)) {
                val allStations = getAllStations()
                return cachedRecentStations.mapNotNull { url ->
                    allStations.find { it.url == url }
                }
            }

            // 지역별 카테고리에서 찾기
            val stationsByCity = getStationsByCity()
            return stationsByCity[selectedStation.city] ?: getAllStations()
        }
    }

    companion object {
        const val ACTION_STOP_ALL = "radio_action_stop_all"
    }
}
