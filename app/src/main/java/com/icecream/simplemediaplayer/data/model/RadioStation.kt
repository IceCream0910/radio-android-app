package com.icecream.simplemediaplayer.data.model

import com.squareup.moshi.Json

data class RadioStation(
    @field:Json(name = "title") val title: String,
    @field:Json(name = "city") val city: String,
    @field:Json(name = "url") val url: String,
    @field:Json(name = "program") val program: String? = null,
    @field:Json(name = "song") val song: String? = null,
    @field:Json(name = "artwork") val artwork: String? = null
)

