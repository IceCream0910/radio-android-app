package com.icecream.simplemediaplayer.common.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Black,
    secondary = Black,
    tertiary = Pink80,
    background = Black,
    surface = Black
)

private val LightColorScheme = lightColorScheme(
    primary = Black,
    secondary = Black,
    tertiary = Pink40,
    background = Color.White,
    surface = Color.White
)

@Composable
fun SimpleMediaPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Edge-to-Edge UI를 위해 시스템 바 제어를 Activity로 이관
    // 여기서는 시스템 바 스타일 설정을 제거

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}