package com.icecream.simplemediaplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.icecream.simplemediaplayer.MainActivity
import com.icecream.simplemediaplayer.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val NOTIFICATION_CHANNEL_ID = "radio_playback"
private const val NOTIFICATION_CHANNEL_NAME = "라디오 재생"

@UnstableApi
class RadioNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val player: ExoPlayer
) {

    private var notificationManager: PlayerNotificationManager? = null
    private var listener: PlayerNotificationManager.NotificationListener? = null

    init {
        createNotificationChannel()
    }

    fun buildNotification(
        mediaSession: MediaSession,
        notificationListener: PlayerNotificationManager.NotificationListener
    ) {
        this.listener = notificationListener

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        notificationManager = PlayerNotificationManager.Builder(
            context,
            NOTIFICATION_ID,
            NOTIFICATION_CHANNEL_ID
        )
            .setMediaDescriptionAdapter(
                RadioNotificationAdapter(
                    context = context,
                    pendingIntent = pendingIntent
                )
            )
            .setSmallIconResourceId(R.drawable.outline_podcasts_24)
            .setChannelNameResourceId(R.string.app_name)
            .setNotificationListener(notificationListener)
            .build()
            .apply {
                setMediaSessionToken(mediaSession.platformToken)
                setUsePreviousAction(true)
                setUseNextAction(true)
                setUsePlayPauseActions(true)
                setUseStopAction(false)
                setUseFastForwardAction(false)
                setUseRewindAction(false)
                setPriority(NotificationCompat.PRIORITY_LOW)
                setPlayer(player)
            }
    }

    fun hideNotification() {
        notificationManager?.setPlayer(null)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "라디오 재생 컨트롤"
            setShowBadge(false)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}

