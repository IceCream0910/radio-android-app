package com.icecream.player_service.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            "ACTION_CANCEL_TIMER" -> {
                context?.stopService(Intent(context, TimerService::class.java))
            }
        }
    }
}