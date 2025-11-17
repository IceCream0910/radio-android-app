package com.icecream.simplemediaplayer.data.model

data class RadioStation(
    val title: String,
    val city: String,
    val url: String,
    val program: String? = null,
    val song: String? = null
)

