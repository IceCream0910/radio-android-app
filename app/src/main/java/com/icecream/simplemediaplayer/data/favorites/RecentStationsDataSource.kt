package com.icecream.simplemediaplayer.data.favorites

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentStore by preferencesDataStore(name = "recent_stations")

@Singleton
class RecentStationsDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore: DataStore<Preferences> = context.recentStore
    private val recentKey = stringPreferencesKey("recent_urls")
    private val maxRecentStations = 10

    val recentStations: Flow<List<String>> = dataStore.data
        .map { prefs ->
            val recentString = prefs[recentKey] ?: ""
            if (recentString.isEmpty()) emptyList() else recentString.split(",")
        }

    suspend fun addRecentStation(url: String) {
        dataStore.edit { prefs ->
            val currentString = prefs[recentKey] ?: ""
            val currentList = if (currentString.isEmpty()) emptyList() else currentString.split(",")

            // 이미 있으면 제거하고 맨 앞에 추가
            val updatedList = (listOf(url) + currentList.filter { it != url })
                .take(maxRecentStations)

            prefs[recentKey] = updatedList.joinToString(",")
        }
    }
}

