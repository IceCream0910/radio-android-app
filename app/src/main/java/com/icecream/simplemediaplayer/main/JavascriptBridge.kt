package com.icecream.simplemediaplayer.main

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.icecream.simplemediaplayer.common.ui.SimpleMediaViewModel


class JavascriptBridge(private val viewModel: SimpleMediaViewModel) {

    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun play(url: String, station: String) {
        try {
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
}