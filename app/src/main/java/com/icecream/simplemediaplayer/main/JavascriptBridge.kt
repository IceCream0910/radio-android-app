package com.icecream.simplemediaplayer.main

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import com.icecream.player_service.service.TimerService
import com.icecream.simplemediaplayer.common.ui.SimpleMediaViewModel


class JavascriptBridge(private val viewModel: SimpleMediaViewModel, private val context: Context, private val startService: () -> Unit) {

    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun updateSetting(item: String, value: Boolean) {
        try {
            val sharedPreferences = context.getSharedPreferences("com.icecream.simplemediaplayer.PREFERENCE", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putBoolean(item, value)
            editor.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun play(url: String, station: String) {
        try {
            startService()
            if (url == "undefined" || station == "undefined") return
            handler.post {
                viewModel.loadData("https://radio.yuntae.in$url", station, "라디오 스트리밍 중")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun pause() {
        try {
            handler.post {
                viewModel.pause()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun setSleepTimer(timerDuration: Long) {
        val timerIntent = Intent(context, TimerService::class.java)
        timerIntent.putExtra(TimerService.EXTRA_TIMER_DURATION, timerDuration)
        context.startService(timerIntent)
    }

    @JavascriptInterface
    fun cancelSleepTimer() {
        context.stopService(Intent(context, TimerService::class.java))
    }

    @JavascriptInterface
    fun backHandlerApp() {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
    }
}