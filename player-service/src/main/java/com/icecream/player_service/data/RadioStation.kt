package com.icecream.player_service.data

data class RadioStation(
    val title: String,
    val city: String,
    val url: String,
    val program: String? = null
)
