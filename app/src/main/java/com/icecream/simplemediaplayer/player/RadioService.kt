package com.icecream.simplemediaplayer.player

import android.app.Notification
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.fetchStations()
            } catch (e: Exception) {
                android.util.Log.e("RadioService", "Failed to load stations", e)
            }
        }

        // Create MediaLibrarySession with callback
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            playerController.player,
            MediaLibrarySessionCallback()
        ).build()

        // Build notification
        notificationManager.buildNotification(
            mediaSession = mediaLibrarySession!!,
            notificationListener = object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopAndRelease()
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
        return START_NOT_STICKY
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
        val stopIntent = Intent(this, RadioService::class.java).apply { action = ACTION_STOP_ALL }
        startService(stopIntent)
        stopService(Intent(this, RadioService::class.java))
        android.os.Process.killProcess(android.os.Process.myPid())
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
            return when (parentId) {
                "root" -> {
                    // Root: Show "Recent", "Favorites" and city folders
                    val items = mutableListOf<MediaItem>()

                    // Add Favorites folder
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

                    // Add city folders
                    val stationsByCity = getStationsByCity()
                    val cityItems = stationsByCity.keys.map { city ->
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

                    Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                "recent" -> {
                    // Show recently played stations
                    val recentUrls = runBlocking { recentStationsDataSource.recentStations.first() }
                    val allStations = getAllStations()
                    val recentStations = recentUrls.mapNotNull { url ->
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
                    // Show favorite stations
                    val favoriteUrls = runBlocking { favoritesDataSource.favorites.first() }
                    val allStations = getAllStations()
                    val favoriteStations = allStations.filter { favoriteUrls.contains(it.url) }

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
                        val stationItems = stations.map { station ->
                            createStationMediaItem(station)
                        }
                        Futures.immediateFuture(
                            LibraryResult.ofItemList(ImmutableList.copyOf(stationItems), params)
                        )
                    } else {
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
            if (mediaId.startsWith("station_")) {
                val stationUrl = mediaId.removePrefix("station_")
                val allStations = getAllStations()
                val station = allStations.find { it.url == stationUrl }

                if (station != null) {
                    val mediaItem = createPlayableStationMediaItem(station)
                    return Futures.immediateFuture(LibraryResult.ofItem(mediaItem, null))
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
            val updatedItems = mutableListOf<MediaItem>()

            mediaItems.forEach { mediaItem ->
                if (mediaItem.mediaId.startsWith("station_")) {
                    val stationUrl = mediaItem.mediaId.removePrefix("station_")
                    val allStations = getAllStations()
                    val selectedStation = allStations.find { it.url == stationUrl }

                    if (selectedStation != null) {
                        // 선택한 스테이션이 속한 카테고리의 모든 스테이션을 큐에 추가
                        val categoryStations = getCategoryStations(selectedStation)

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
                    updatedItems.add(mediaItem)
                }
            }

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
            // 먼저 즐겨찾기에 있는지 확인
            val favoriteUrls = runBlocking { favoritesDataSource.favorites.first() }
            if (favoriteUrls.contains(selectedStation.url)) {
                val allStations = getAllStations()
                return allStations.filter { favoriteUrls.contains(it.url) }
            }

            // 최근 재생 목록에 있는지 확인
            val recentUrls = runBlocking { recentStationsDataSource.recentStations.first() }
            if (recentUrls.contains(selectedStation.url)) {
                val allStations = getAllStations()
                return recentUrls.mapNotNull { url ->
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
