package com.icecream.simplemediaplayer.common.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.icecream.player_service.service.SimpleMediaService
import com.icecream.player_service.service.TimerService
import com.icecream.simplemediaplayer.common.ui.theme.SimpleMediaPlayerTheme
import com.icecream.simplemediaplayer.main.SimpleMediaScreen
import com.icecream.simplemediaplayer.secondary.SecondaryScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SimpleMediaActivity : ComponentActivity() {

    private val viewModel: SimpleMediaViewModel by viewModels()
    private var isServiceRunning = false

    private fun getSystemBarStyles(isDarkTheme: Boolean): Pair<SystemBarStyle, SystemBarStyle> {
        val statusBarStyle = if (isDarkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.parseColor("#000000")
            )
        }
        val navigationBarStyle = if (isDarkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.parseColor("#000000")
            )
        }
        return statusBarStyle to navigationBarStyle
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SimpleMediaPlayerTheme {
                val isDarkTheme = isSystemInDarkTheme()

                // 테마 변경 시 시스템 바 스타일 업데이트
                LaunchedEffect(isDarkTheme) {
                    val (statusBarStyle, navigationBarStyle) = getSystemBarStyles(isDarkTheme)
                    enableEdgeToEdge(
                        statusBarStyle = statusBarStyle
                    )
                }

                val navController = rememberNavController()

                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .navigationBarsPadding()
                ) {
                    NavHost(navController = navController, startDestination = Destination.Main.route) {
                        composable(Destination.Main.route) {
                            SimpleMediaScreen(
                                vm = viewModel,
                                navController = navController,
                                startService = ::startService
                            )
                        }
                        composable(Destination.Secondary.route) {
                            SecondaryScreen(vm = viewModel)
                        }
                    }
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, TimerService::class.java))
        stopService(Intent(this, SimpleMediaService::class.java))
        isServiceRunning = false
        ActivityCompat.finishAffinity(this)
        System.exit(0)
    }

    fun startService() {
        if (!isServiceRunning) {
            val intent = Intent(this, SimpleMediaService::class.java)
            startForegroundService(intent)
            isServiceRunning = true
        }
    }
}