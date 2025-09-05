package com.icecream.player_service.api

import com.icecream.player_service.data.RadioStation
import retrofit2.http.GET

interface RadioStationApi {
    @GET("radioStations.json")
    suspend fun getRadioStations(): List<RadioStation>
}
