package com.icecream.player_service.service

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.icecream.player_service.repository.RadioStationRepository
import com.icecream.player_service.service.notification.SimpleMediaNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SimpleMediaService : MediaLibraryService() {

    private lateinit var mediaLibrarySession: MediaLibrarySession

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var notificationManager: SimpleMediaNotificationManager

    @Inject
    lateinit var radioStationRepository: RadioStationRepository


    override fun onCreate() {
        super.onCreate()
        mediaLibrarySessionCallback.setRepository(radioStationRepository)
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            mediaLibrarySessionCallback
        ).build()

        // 앱 시작 시 라디오 스테이션 데이터를 미리 로드
        CoroutineScope(Dispatchers.IO).launch {
            radioStationRepository.getRadioStations()
        }
    }

    @UnstableApi
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationManager.startNotificationService(
            mediaSessionService = this,
            mediaSession = mediaLibrarySession
        )

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaLibrarySession.run {
            release()
            if (player.playbackState != Player.STATE_IDLE) {
                player.seekTo(0)
                player.playWhenReady = false
                player.stop()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaLibrarySession

    class MediaLibrarySessionCallback : MediaLibrarySession.Callback {
        private lateinit var repository: RadioStationRepository

        fun setRepository(repository: RadioStationRepository) {
            this.repository = repository
        }

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

        var cityLabel : Map<String, String> = mapOf(
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
                    val cityItems = repository.getStationsByCity().keys.map { city ->
                        MediaItem.Builder()
                            .setMediaId("city_$city")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(cityLabel.get(city) ?: city)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                    .setIsPlayable(false)
                                    .setIsBrowsable(true)
                                    .build()
                            )
                            .build()
                    }
                    Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(cityItems), params)
                    )
                }

                else -> {
                    if (parentId.startsWith("city_")) {
                        val city = parentId.removePrefix("city_")
                        val stations = repository.getStationsByCity()[city] ?: emptyList()
                        val stationItems = stations.map { station ->
                            MediaItem.Builder()
                                .setMediaId("station_${station.title}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(station.title)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                        .setIsPlayable(true)
                                        .setIsBrowsable(false)
                                        .build()
                                )
                                .build()
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
                val stationTitle = mediaId.removePrefix("station_")
                val allStations = repository.getStationsByCity().values.flatten()
                val station = allStations.find { it.title == stationTitle }

                if (station != null) {
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(mediaId)
                        .setUri("https://radio.yuntae.in${station.url}")
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(station.title)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .setIsPlayable(true)
                                .build()
                        )
                        .build()

                    return Futures.immediateFuture(LibraryResult.ofItem(mediaItem, null))
                }
            }

            return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
        }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updatedItems = mediaItems.map { mediaItem ->
                if (mediaItem.mediaId.startsWith("station_")) {
                    val stationTitle = mediaItem.mediaId.removePrefix("station_")
                    val allStations = repository.getStationsByCity().values.flatten()
                    val station = allStations.find { it.title == stationTitle }

                    if (station != null) {
                        MediaItem.Builder()
                            .setMediaId(mediaItem.mediaId)
                            .setUri("https://radio.yuntae.in${station.url}")
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(station.title)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .setArtworkUri(Uri.parse("https://radio.yuntae.in/albumart.png"))
                                    .setIsPlayable(true)
                                    .build()
                            )
                            .build()
                    } else {
                        Log.d("SimpleMediaService", "Station not found: $stationTitle")
                        mediaItem
                    }
                } else {
                    Log.d("SimpleMediaService", "Processing unknown item: ${mediaItem.mediaId}")
                    mediaItem
                }
            }.toMutableList()

            return Futures.immediateFuture(updatedItems)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val searchQuery = query.lowercase().trim()
            val allStations = repository.getStationsByCity().values.flatten()

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
            val allStations = repository.getStationsByCity().values.flatten()

            val searchResults = allStations.filter { station ->
                station.title.lowercase().contains(searchQuery)
            }.map { station ->
                MediaItem.Builder()
                    .setMediaId("station_${station.title}")
                    .setUri("https://radio.yuntae.in${station.url}")
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(station.title)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                            .setArtworkUri(Uri.parse("https://radio.yuntae.in/albumart.png"))
                            .setIsPlayable(true)
                            .setIsBrowsable(false)
                            .build()
                    )
                    .build()
            }

            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(searchResults), params)
            )
        }
    }
}
