package com.icecream.simplemediaplayer.ui

import com.icecream.simplemediaplayer.data.model.RadioStation

data class PlayerUiState(
    val currentStation: RadioStation? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isFavorite: Boolean = false,
    val programTitle: String? = null,
    val songTitle: String? = null
)
