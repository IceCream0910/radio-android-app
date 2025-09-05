package com.icecream.player_service.service

import android.annotation.SuppressLint
import android.app.IntentService
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import com.icecream.player_service.R
import java.util.Calendar

class TimerService : Service() {

    companion object {
        const val EXTRA_TIMER_DURATION = "timer_duration"
        const val TIMER_INTERVAL = 1000L
        const val NOTIFICATION_ID = 1 // Example notification ID
    }

    private lateinit var serviceLooper: Looper
    private lateinit var serviceHandler: Handler
    private var countDownTimer: CountDownTimer? = null
    private var notificationManager: NotificationManagerCompat? = null


    @SuppressLint("InlinedApi")
    override fun onCreate() {
        super.onCreate()
        val handlerThread = HandlerThread("TimerServiceThread").apply {
            start()
        }
        // Get the looper from the handler thread
        serviceLooper = handlerThread.looper
        // Create a handler with the looper
        serviceHandler = Handler(serviceLooper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val timerDuration = intent?.getLongExtra(EXTRA_TIMER_DURATION, 0) ?: 0
        // Execute the timer logic on the service's handler thread
        serviceHandler.post {
            startTimer(timerDuration)
        }
        // Start the service in the foreground with a notification
        startForeground(NOTIFICATION_ID, createNotification(timerDuration, timerDuration))
        // If the system kills the service, do not recreate it until a new explicit intent is received
        return START_NOT_STICKY
    }


    private fun startTimer(timerDuration: Long) {
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(timerDuration, TIMER_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                showNotification(millisUntilFinished, timerDuration)
            }

            override fun onFinish() {
                stopAllServices()
            }
        }

        countDownTimer?.start()
    }

    private fun stopAllServices(){
        stopService(Intent(this, TimerService::class.java))
        stopService(Intent(this, SimpleMediaService::class.java))
        System.exit(0)
    }

    @SuppressLint("MissingPermission")
    @OptIn(UnstableApi::class)
    private fun createNotification(startMillis: Long, initialMillis: Long): Notification {
        val notificationManagerCompat = NotificationManagerCompat.from(this)
        if (!notificationManagerCompat.areNotificationsEnabled()) { //권한체크
            val intent = Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return Notification()
        }

        val notificationChannelId = "stop"
        NotificationUtil.createNotificationChannel(
            this,
            notificationChannelId,
            R.string.nameResourceId,
            R.string.descriptionResourceId,
            NotificationUtil.IMPORTANCE_LOW
        )

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            component = ComponentName(packageName, "com.icecream.simplemediaplayer.common.ui.SimpleMediaActivity")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, notificationChannelId).apply {
            setSmallIcon(R.drawable.baseline_radio_24)
            setContentTitle("라디오 종료 타이머 설정됨")
            setContentIntent(pendingIntent)
            addAction(R.drawable.baseline_radio_24, "Cancel Timer", getCancelTimerPendingIntent())
            priority = NotificationCompat.PRIORITY_LOW
        }

        val totalSeconds = (initialMillis / 1000).toInt()

        val futureTimeMillis = System.currentTimeMillis() + (totalSeconds * 1000)

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = futureTimeMillis

        val futureHours = calendar.get(Calendar.HOUR_OF_DAY)
        val futureMinutes = calendar.get(Calendar.MINUTE)

        builder.setContentText("${futureHours}시 ${futureMinutes}분에 앱이 종료됩니다")
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(startMillis: Long, initialMillis: Long) {
        val notification = createNotification(startMillis, initialMillis)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun getCancelTimerPendingIntent(): PendingIntent {
        val cancelIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = "ACTION_CANCEL_TIMER"
        }
        return PendingIntent.getBroadcast(this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onDestroy() {
        // Cancel the timer if the service is destroyed
        countDownTimer?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Return null as this service does not support binding
        return null
    }
}