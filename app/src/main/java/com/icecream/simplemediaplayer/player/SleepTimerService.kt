package com.icecream.simplemediaplayer.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import com.icecream.simplemediaplayer.MainActivity
import com.icecream.simplemediaplayer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@UnstableApi
class SleepTimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    private var totalSeconds = 0
    private var remainingSeconds = 0

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "sleep_timer_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        totalSeconds = intent?.getIntExtra("TOTAL_SECONDS", 0) ?: 0
        remainingSeconds = totalSeconds

        startForeground(NOTIFICATION_ID, createNotification())

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
                updateNotification()
            }
            // Timer finished
            stopApp()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "라디오 타이머",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "라디오 타이머 알림"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val timeString = formatTime(remainingSeconds)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("라디오 타이머 작동 중")
            .setContentText("$timeString 후에 라디오를 자동으로 꺼줄게요.")
            .setSmallIcon(R.drawable.outline_podcasts_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d시간 %02d분 %02d초", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%02d분 %02d초", minutes, secs)
        }
    }

    private fun stopApp() {
        // Stop all playback
        val radioApp = application as? com.icecream.simplemediaplayer.RadioApp
        radioApp?.stopAllPlayback()

        // Stop radio service
        val radioServiceIntent = Intent(this, RadioService::class.java)
        stopService(radioServiceIntent)

        // Stop this service
        stopSelf()

        // Kill the app
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
    }
}

