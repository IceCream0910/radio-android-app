package com.icecream.simplemediaplayer.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

class AppOpenAdManager(
    private val application: Application,
    private val adUnitId: String = "ca-app-pub-7178712602934912/5112244964" // Demo ad unit ID: ca-app-pub-3940256099942544/9257395921
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var loadTime: Long = 0
    private var currentActivity: Activity? = null

    // 광고 표시를 제어하기 위한 플래그
    var canShowAd = false
        private set

    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** 광고 로드 시작 */
    fun loadAd() {
        if (isLoadingAd || isAdAvailable()) {
            return
        }

        isLoadingAd = true
        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            application,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    Log.d(TAG, "App Open Ad loaded successfully")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    Log.e(TAG, "Failed to load App Open Ad: ${loadAdError.message}")
                }
            }
        )
    }

    /** 광고 표시 허용 설정 */
    fun enableAdShowing() {
        canShowAd = true
        Log.d(TAG, "Ad showing enabled")
    }

    /** 광고가 4시간 이내에 로드되었는지 확인 */
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference: Long = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    /** 사용 가능한 광고가 있는지 확인 */
    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    /** 광고 표시 */
    fun showAdIfAvailable(activity: Activity) {
        // 조건 체크: 광고 표시 허용되지 않았거나, 이미 표시 중이거나, 사용 가능한 광고가 없으면 반환
        if (!canShowAd) {
            Log.d(TAG, "Ad showing not enabled yet")
            loadAd() // 다음을 위해 미리 로드
            return
        }

        if (!isAdAvailable()) {
            Log.d(TAG, "App Open Ad is not available")
            loadAd()
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                loadAd() // 다음 광고 미리 로드
                Log.d(TAG, "App Open Ad dismissed")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                loadAd()
                Log.e(TAG, "Failed to show App Open Ad: ${adError.message}")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open Ad showed")
            }
        }

        appOpenAd?.show(activity)
    }

    // DefaultLifecycleObserver - called when app moves to foreground
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        currentActivity?.let { activity ->
            showAdIfAvailable(activity)
        }
    }

    // ActivityLifecycleCallbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            currentActivity = activity
        }
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        private const val TAG = "AppOpenAdManager"
    }
}

