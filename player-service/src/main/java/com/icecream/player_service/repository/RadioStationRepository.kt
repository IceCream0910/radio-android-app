package com.icecream.player_service.repository

import com.icecream.player_service.api.RadioStationApi
import com.icecream.player_service.data.RadioStation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioStationRepository @Inject constructor(
    private val api: RadioStationApi
) {
    private var cachedStations: List<RadioStation> = emptyList()

    suspend fun getRadioStations(): List<RadioStation> {
        if (cachedStations.isEmpty()) {
            cachedStations = try {
                api.getRadioStations()
            } catch (e: Exception) {
                emptyList()
            }
        }
        return cachedStations
    }

    fun getStationsByCity(): Map<String, List<RadioStation>> {
        return cachedStations.groupBy { it.city }
    }
}
