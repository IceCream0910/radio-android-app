package com.icecream.simplemediaplayer.data.network

import com.icecream.simplemediaplayer.data.model.ProgramInfo
import com.icecream.simplemediaplayer.data.model.RadioStation
import com.icecream.simplemediaplayer.data.model.SongInfo
import retrofit2.http.GET
import retrofit2.http.Url

interface RadioApi {
    @GET("radioStations.json")
    suspend fun fetchStations(): List<RadioStation>

    @GET
    suspend fun fetchProgramInfo(@Url url: String): ProgramInfo

    @GET
    suspend fun fetchSongInfo(@Url url: String): SongInfo
}

