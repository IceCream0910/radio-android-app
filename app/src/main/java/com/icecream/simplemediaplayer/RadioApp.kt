package com.icecream.simplemediaplayer

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.icecream.simplemediaplayer.ads.AppOpenAdManager
import com.icecream.simplemediaplayer.util.NetworkMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RadioApp : Application() {
    @Volatile
    var allowBackgroundPlayback: Boolean = true

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}

        // Initialize App Open Ad Manager
        appOpenAdManager = AppOpenAdManager(this)

        // Pre-load the first app open ad
        appOpenAdManager.loadAd()
    }

    fun stopAllPlayback() {
        allowBackgroundPlayback = false
    }
}
