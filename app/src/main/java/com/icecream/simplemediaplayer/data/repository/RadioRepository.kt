package com.icecream.simplemediaplayer.data.repository

import com.icecream.simplemediaplayer.data.model.ProgramInfo
import com.icecream.simplemediaplayer.data.model.RadioStation
import com.icecream.simplemediaplayer.data.model.SongInfo

interface RadioRepository {
    suspend fun fetchStations(forceRefresh: Boolean = false): List<RadioStation>
    suspend fun fetchProgramInfo(programUrl: String): Result<ProgramInfo>
    suspend fun fetchSongInfo(songUrl: String): Result<SongInfo>
    fun getStationsByCity(): Map<String, List<RadioStation>>
    fun getAllStations(): List<RadioStation>
}

