package com.icecream.simplemediaplayer.data.favorites

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.favoriteStore by preferencesDataStore(name = "favorites")

@Singleton
class FavoritesDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore: DataStore<Preferences> = context.favoriteStore
    private val favoritesKey = stringSetPreferencesKey("favorite_urls")

    val favorites: Flow<Set<String>> = dataStore.data
        .map { prefs -> prefs[favoritesKey] ?: emptySet() }

    suspend fun toggleFavorite(url: String) {
        dataStore.edit { prefs ->
            val current = prefs[favoritesKey] ?: emptySet()
            prefs[favoritesKey] = if (current.contains(url)) current - url else current + url
        }
    }
}
