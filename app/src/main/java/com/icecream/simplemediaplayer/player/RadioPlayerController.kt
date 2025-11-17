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
    private val repository: RadioRepository
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
                stationUrl = mediaItem?.mediaId
            )
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
                            .setArtworkUri(Uri.parse("https://i.imgur.com/u7N8nbD.png"))
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

    fun playStream(title: String, url: String) {
        val relId = relativeId(url)
        val existingIndex = (0 until exoPlayer.mediaItemCount).firstOrNull { exoPlayer.getMediaItemAt(it).mediaId == relId }
        if (existingIndex != null) {
            exoPlayer.seekTo(existingIndex, C.TIME_UNSET)
            exoPlayer.playWhenReady = true
            return
        }
        val mediaItem = MediaItem.Builder()
            .setUri(if (url.startsWith(baseUrl)) url else baseUrl + relId)
            .setMediaId(relId)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMaxPlaybackSpeed(1.0f)
                    .build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("라디오 스트리밍 중")
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
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
            exoPlayer.pause()
        } else {
            exoPlayer.playWhenReady = true
        }
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
    val isBuffering: Boolean = false
)
