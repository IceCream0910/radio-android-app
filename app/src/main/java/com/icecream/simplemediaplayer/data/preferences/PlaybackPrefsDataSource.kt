package com.icecream.simplemediaplayer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.icecream.simplemediaplayer.data.model.RadioStation
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackStore by preferencesDataStore(name = "playback_prefs")

@Singleton
class PlaybackPrefsDataSource @Inject constructor(
    @ApplicationContext context: Context,
    moshi: Moshi
) {
    private val dataStore: DataStore<Preferences> = context.playbackStore
    private val queueKey = stringPreferencesKey("playback_queue")
    private val stationUrlKey = stringPreferencesKey("last_station_url")
    private val wasPlayingKey = booleanPreferencesKey("was_playing")

    private val listType = Types.newParameterizedType(List::class.java, RadioStation::class.java)
    private val queueAdapter = moshi.adapter<List<RadioStation>>(listType)

    val playbackState: Flow<StoredPlaybackState> = dataStore.data.map { prefs ->
        val queueJson = prefs[queueKey]
        StoredPlaybackState(
            queue = queueJson?.let { runCatching { queueAdapter.fromJson(it) }.getOrNull().orEmpty() }
                ?: emptyList(),
            lastStationUrl = prefs[stationUrlKey],
            wasPlaying = prefs[wasPlayingKey] ?: false
        )
    }

    suspend fun save(queue: List<RadioStation>, stationUrl: String?, isPlaying: Boolean) {
        dataStore.edit { prefs ->
            if (queue.isEmpty()) {
                prefs.remove(queueKey)
            } else {
                prefs[queueKey] = queueAdapter.toJson(queue)
            }
            if (stationUrl.isNullOrEmpty()) {
                prefs.remove(stationUrlKey)
            } else {
                prefs[stationUrlKey] = stationUrl
            }
            prefs[wasPlayingKey] = isPlaying
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(queueKey)
            prefs.remove(stationUrlKey)
            prefs.remove(wasPlayingKey)
        }
    }
}

data class StoredPlaybackState(
    val queue: List<RadioStation> = emptyList(),
    val lastStationUrl: String? = null,
    val wasPlaying: Boolean = false
)

