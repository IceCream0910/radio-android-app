package com.icecream.simplemediaplayer.data.favorites

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

private val Context.favoriteStore by preferencesDataStore(name = "favorites")

@Singleton
class FavoritesDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore: DataStore<Preferences> = context.favoriteStore
    private val favoritesSetKey = stringSetPreferencesKey("favorite_urls")
    private val favoritesListKey = stringPreferencesKey("favorite_urls_ordered")
    
    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, String::class.java)
    private val jsonAdapter = moshi.adapter<List<String>>(listType)

    val favorites: Flow<List<String>> = dataStore.data
        .map { prefs ->
            val orderedJson = prefs[favoritesListKey]
            if (orderedJson != null) {
                jsonAdapter.fromJson(orderedJson) ?: emptyList()
            } else {
                // Migration from old Set
                val oldSet = prefs[favoritesSetKey] ?: emptySet()
                oldSet.toList()
            }
        }

    suspend fun toggleFavorite(url: String) {
        dataStore.edit { prefs ->
            val currentList = getOrderedList(prefs)
            val newList = if (currentList.contains(url)) {
                currentList.filter { it != url }
            } else {
                currentList + url
            }
            saveOrderedList(prefs, newList)
        }
    }

    suspend fun addFavorites(urls: List<String>) {
        dataStore.edit { prefs ->
            val currentList = getOrderedList(prefs)
            val toAdd = urls.filter { !currentList.contains(it) }
            saveOrderedList(prefs, currentList + toAdd)
        }
    }

    suspend fun updateOrder(newList: List<String>) {
        dataStore.edit { prefs ->
            saveOrderedList(prefs, newList)
        }
    }

    private fun getOrderedList(prefs: Preferences): List<String> {
        val orderedJson = prefs[favoritesListKey]
        return if (orderedJson != null) {
            jsonAdapter.fromJson(orderedJson) ?: emptyList()
        } else {
            prefs[favoritesSetKey]?.toList() ?: emptyList()
        }
    }

    private fun saveOrderedList(prefs: MutablePreferences, list: List<String>) {
        prefs[favoritesListKey] = jsonAdapter.toJson(list)
        // Also keep the set in sync for a while or just clear it
        prefs[favoritesSetKey] = list.toSet()
    }
}

private typealias MutablePreferences = androidx.datastore.preferences.core.MutablePreferences
