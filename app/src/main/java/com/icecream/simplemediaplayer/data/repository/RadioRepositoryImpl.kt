package com.icecream.simplemediaplayer.data.repository

import com.icecream.simplemediaplayer.data.model.ProgramInfo
import com.icecream.simplemediaplayer.data.model.RadioStation
import com.icecream.simplemediaplayer.data.model.SongInfo
import com.icecream.simplemediaplayer.data.network.RadioApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RadioRepositoryImpl(
    private val api: RadioApi
) : RadioRepository {

    private var cachedStations: List<RadioStation>? = null

    override suspend fun fetchStations(forceRefresh: Boolean): List<RadioStation> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedStations?.let { return@withContext it }
        }
        val stations = api.fetchStations()
        cachedStations = stations
        stations
    }

    override suspend fun fetchProgramInfo(programUrl: String): Result<ProgramInfo> = withContext(Dispatchers.IO) {
        try {
            val programInfo = api.fetchProgramInfo(programUrl)
            Result.success(programInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchSongInfo(songUrl: String): Result<SongInfo> = withContext(Dispatchers.IO) {
        try {
            val songInfo = api.fetchSongInfo(songUrl)
            Result.success(songInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getStationsByCity(): Map<String, List<RadioStation>> {
        return cachedStations?.groupBy { it.city } ?: emptyMap()
    }

    override fun getAllStations(): List<RadioStation> {
        return cachedStations ?: emptyList()
    }
}
