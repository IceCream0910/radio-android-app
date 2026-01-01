package com.icecream.simplemediaplayer.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.TaskStackBuilder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.icecream.simplemediaplayer.MainActivity
import com.icecream.simplemediaplayer.data.model.RadioStation
import com.icecream.simplemediaplayer.data.repository.RadioRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton

class RadioPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayer: ExoPlayer,
    @Suppress("unused") private val repository: RadioRepository
) {
    private val baseUrl = "https://radio.yuntae.in"

    private val prevCommand = SessionCommand(ACTION_PREV, Bundle.EMPTY)
    private val nextCommand = SessionCommand(ACTION_NEXT, Bundle.EMPTY)

    private val mediaButtonLayout = listOf(
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName("Previous")
            .setSessionCommand(prevCommand)
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName("Next")
            .setSessionCommand(nextCommand)
            .build()
    )

    private val sessionActivity: PendingIntent = TaskStackBuilder.create(context).run {
        addNextIntentWithParentStack(Intent(context, MainActivity::class.java))
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
        getPendingIntent(REQUEST_CODE_ACTIVITY, flags)
    } ?: throw IllegalStateException("Unable to create session activity pending intent")

    @UnstableApi
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(prevCommand)
                .add(nextCommand)
                .build()
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                .buildUpon()
                .remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                .remove(Player.COMMAND_SEEK_FORWARD)
                .remove(Player.COMMAND_SEEK_BACK)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_PREV -> playPrev()
                ACTION_NEXT -> playNext()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private var suppressPlayWhenReadyHandling = false

    val mediaSession: MediaSession = MediaSession.Builder(context, exoPlayer)
        .setId("RadioPlayerSession")
        .setCallback(sessionCallback)
        .setSessionActivity(sessionActivity)
        .setMediaButtonPreferences(mediaButtonLayout)
        .build()

    val player: Player get() = exoPlayer

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val mediaItem = player.currentMediaItem
            _playerState.value = _playerState.value.copy(
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                title = mediaItem?.mediaMetadata?.title?.toString() ?: "",
                stationUrl = mediaItem?.mediaId,
                hasPrev = player.hasPreviousMediaItem(),
                hasNext = player.hasNextMediaItem()
            )
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (suppressPlayWhenReadyHandling) return

            val userDriven = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST ||
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE

            if (!userDriven) return // let ExoPlayer handle focus/noisy cases

            suppressPlayWhenReadyHandling = true
            if (playWhenReady) {
                restartFromCurrent()
            } else {
                pauseAndResetCurrent()
            }
            suppressPlayWhenReadyHandling = false
        }
    }

    init {
        exoPlayer.addListener(listener)
    }

    private fun relativeId(fullOrRelative: String): String =
        if (fullOrRelative.startsWith(baseUrl)) fullOrRelative.removePrefix(baseUrl) else fullOrRelative

    fun updateStations(stations: List<RadioStation>, activeStationUrl: String? = null) {
        if (stations.isNotEmpty()) {
            val mediaItems = stations.mapIndexed { index, station ->
                val artworkUri = if (!station.artwork.isNullOrEmpty()) {
                    Uri.parse(station.artwork)
                } else {
                    Uri.parse("https://i.imgur.com/u7N8nbD.png")
                }

                MediaItem.Builder()
                    .setUri(baseUrl + station.url)
                    .setMediaId(station.url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(station.title)
                            .setSubtitle(station.city)
                            .setArtist("라디오 스트리밍 중")
                            .setIsPlayable(true)
                            .setIsBrowsable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                            .setArtworkUri(artworkUri)
                            .setExtras(Bundle().apply { putInt(KEY_LIST_INDEX, index) })
                            .build()
                    )
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder().setMaxPlaybackSpeed(1.0f).build()
                    )
                    .build()
            }
            val desiredUrl = activeStationUrl ?: _playerState.value.stationUrl
            val index = desiredUrl?.let { url ->
                mediaItems.indexOfFirst { it.mediaId == url }.takeIf { it >= 0 }
            } ?: 0
            exoPlayer.setMediaItems(mediaItems, index, C.TIME_UNSET)
            exoPlayer.prepare()
        }
    }

    fun playStationByUrl(stationUrl: String) {
        val relId = relativeId(stationUrl)
        val existingIndex = (0 until exoPlayer.mediaItemCount).firstOrNull {
            exoPlayer.getMediaItemAt(it).mediaId == relId
        }
        if (existingIndex != null) {
            exoPlayer.seekTo(existingIndex, C.TIME_UNSET)
            exoPlayer.playWhenReady = true
        } else {
            android.util.Log.w("RadioPlayerController", "Station $stationUrl not in current playlist")
        }
    }

    fun hasMediaItem(mediaId: String): Boolean {
        val relId = relativeId(mediaId)
        return (0 until exoPlayer.mediaItemCount).any { exoPlayer.getMediaItemAt(it).mediaId == relId }
    }

    fun setPlaylistAndPlay(stations: List<RadioStation>, targetUrl: String) {
        if (stations.isEmpty()) return
        val mediaItems = stations.mapIndexed { index, station ->
            val artworkUri = if (!station.artwork.isNullOrEmpty()) {
                Uri.parse(station.artwork)
            } else {
                Uri.parse("https://i.imgur.com/u7N8nbD.png")
            }
            MediaItem.Builder()
                .setUri(baseUrl + station.url)
                .setMediaId(station.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.title)
                        .setSubtitle(station.city)
                        .setArtist("라디오 스트리밍 중")
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setArtworkUri(artworkUri)
                        .setExtras(Bundle().apply { putInt(KEY_LIST_INDEX, index) })
                        .build()
                )
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder().setMaxPlaybackSpeed(1.0f).build()
                )
                .build()
        }
        val desiredIndex = mediaItems.indexOfFirst { it.mediaId == targetUrl }.let { if (it >= 0) it else 0 }
        exoPlayer.setMediaItems(mediaItems, desiredIndex, C.TIME_UNSET)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    /**
     * Update current media item's artist field. If artist is null or blank, set default.
     */
    fun updateCurrentArtist(artist: String?) {
        val index = exoPlayer.currentMediaItemIndex
        if (index == C.INDEX_UNSET || exoPlayer.mediaItemCount == 0) return
        val current = exoPlayer.getMediaItemAt(index)
        val defaultArtist = artist?.takeIf { it.isNotBlank() } ?: "라디오 스트리밍 중"

        val oldMeta = current.mediaMetadata
        val newMeta = MediaMetadata.Builder()
            .setTitle(oldMeta.title)
            .setSubtitle(oldMeta.subtitle)
            .setArtist(defaultArtist)
            .setArtworkUri(oldMeta.artworkUri)
            .setIsPlayable(oldMeta.isPlayable)
            .setIsBrowsable(oldMeta.isBrowsable)
            .setMediaType(oldMeta.mediaType)
            .setExtras(oldMeta.extras)
            .build()

        val newItem = MediaItem.Builder()
            .setMediaId(current.mediaId)
            .setMediaMetadata(newMeta)
            .apply {
                current.localConfiguration?.let { lc ->
                    setUri(lc.uri)
                    lc.mimeType?.let { setMimeType(it) }
                }
                setLiveConfiguration(current.liveConfiguration)
            }
            .build()

        exoPlayer.replaceMediaItem(index, newItem)
    }

    fun playPrev() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
            exoPlayer.playWhenReady = true
        }
    }

    fun playNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.playWhenReady = true
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pauseAndResetCurrent()
        } else {
            restartFromCurrent()
        }
    }

    fun pauseAndResetCurrent() {
        exoPlayer.stop()
    }

    fun restartFromCurrent() {
        if (exoPlayer.mediaItemCount == 0) return
        val targetIndex = exoPlayer.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: 0
        exoPlayer.seekTo(targetIndex, C.TIME_UNSET)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun release() {
        exoPlayer.removeListener(listener)
        mediaSession.release()
        exoPlayer.release()
    }

    companion object {
        const val ACTION_PREV = "radio_prev"
        const val ACTION_NEXT = "radio_next"
        const val KEY_LIST_INDEX = "station_list_index"
        const val REQUEST_CODE_ACTIVITY = 10
    }
}

data class PlayerState(
    val title: String = "",
    val stationUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false
)
