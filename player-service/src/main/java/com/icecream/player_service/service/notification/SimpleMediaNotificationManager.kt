package com.icecream.player_service.service.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.icecream.player_service.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject


private const val NOTIFICATION_ID = 200
private const val NOTIFICATION_CHANNEL_NAME = "라디오 재생"
private const val NOTIFICATION_CHANNEL_ID = "notification channel id 1"

class SimpleMediaNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val player: ExoPlayer
) {

    private var notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    @UnstableApi
    fun startNotificationService(
        mediaSessionService: MediaSessionService,
        mediaSession: MediaSession
    ) {
        buildNotification(mediaSession)
        startForegroundNotification(mediaSessionService)
    }

    @UnstableApi
    private fun buildNotification(mediaSession: MediaSession) {
        val packageName = context.packageName
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            component = ComponentName(packageName, "com.icecream.simplemediaplayer.common.ui.SimpleMediaActivity")
        }

        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        PlayerNotificationManager.Builder(context, NOTIFICATION_ID, NOTIFICATION_CHANNEL_ID)
            .setMediaDescriptionAdapter(
                SimpleMediaNotificationAdapter(
                    context = context,
                    pendingIntent = pendingIntent
                )
            )
            .setSmallIconResourceId(R.drawable.baseline_radio_24)
            .build()
            .also {
                it.setMediaSessionToken(mediaSession.sessionCompatToken)
                it.setUseFastForwardActionInCompactView(true)
                it.setUseRewindActionInCompactView(true)
                it.setUseNextActionInCompactView(false)
                it.setPriority(NotificationCompat.PRIORITY_LOW)
                it.setPlayer(player)
            }

    }

    private fun startForegroundNotification(mediaSessionService: MediaSessionService) {

        val notification = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        mediaSessionService.startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }
}