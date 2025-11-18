package com.icecream.simplemediaplayer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun EqualizerIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Canvas(modifier = modifier.size(16.dp)) {
        val barWidth = size.width / 5f
        val spacing = barWidth / 2f
        val maxHeight = size.height
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

        // Bar 1
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, maxHeight * (1f - bar1Height)),
            size = Size(barWidth, maxHeight * bar1Height),
            cornerRadius = cornerRadius
        )

        // Bar 2
        drawRoundRect(
            color = color,
            topLeft = Offset(barWidth + spacing, maxHeight * (1f - bar2Height)),
            size = Size(barWidth, maxHeight * bar2Height),
            cornerRadius = cornerRadius
        )

        // Bar 3
        drawRoundRect(
            color = color,
            topLeft = Offset((barWidth + spacing) * 2, maxHeight * (1f - bar3Height)),
            size = Size(barWidth, maxHeight * bar3Height),
            cornerRadius = cornerRadius
        )
    }
}

