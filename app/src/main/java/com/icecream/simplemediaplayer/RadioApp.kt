package com.icecream.simplemediaplayer

import android.app.Application
import com.icecream.simplemediaplayer.util.NetworkMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RadioApp : Application() {
    @Volatile
    var allowBackgroundPlayback: Boolean = true

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    fun stopAllPlayback() {
        allowBackgroundPlayback = false
    }
}
