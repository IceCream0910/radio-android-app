package com.icecream.simplemediaplayer.common.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.icecream.player_service.service.SimpleMediaService
import com.icecream.simplemediaplayer.common.ui.theme.SimpleMediaPlayerTheme
import com.icecream.simplemediaplayer.main.SimpleMediaScreen
import com.icecream.simplemediaplayer.secondary.SecondaryScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SimpleMediaActivity : ComponentActivity() {

    private val viewModel: SimpleMediaViewModel by viewModels()
    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SimpleMediaPlayerTheme {
                val navController = rememberNavController()

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


    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, SimpleMediaService::class.java))
        isServiceRunning = false
        ActivityCompat.finishAffinity(this)
        System.exit(0)
    }

    private fun startService() {
        if (!isServiceRunning) {
            val intent = Intent(this, SimpleMediaService::class.java)
            startForegroundService(intent)
            isServiceRunning = true
        }
    }
}