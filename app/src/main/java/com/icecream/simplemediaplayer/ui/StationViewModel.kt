package com.icecream.simplemediaplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icecream.simplemediaplayer.data.favorites.FavoritesDataSource
import com.icecream.simplemediaplayer.data.model.RadioStation
import com.icecream.simplemediaplayer.data.repository.RadioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StationViewModel @Inject constructor(
    private val repository: RadioRepository,
    private val favoritesDataSource: FavoritesDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(StationUiState(isLoading = true))
    val state: StateFlow<StationUiState> = _state.asStateFlow()

    init {
        observeFavorites()
        refresh()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoritesDataSource.favorites.collectLatest { favorites ->
                _state.update { it.copy(favorites = favorites) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val stations = repository.fetchStations()
                val grouped = stations.groupBy { it.city }
                val firstCity = grouped.keys.firstOrNull()
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        groupedStations = grouped,
                        cities = grouped.keys.toList(),
                        selectedCity = current.selectedCity ?: firstCity,
                        error = null,
                        searchResults = emptyList()
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun selectCity(city: String) {
        _state.update { it.copy(selectedCity = city) }
    }

    fun toggleFavorite(url: String) {
        viewModelScope.launch {
            favoritesDataSource.toggleFavorite(url)
        }
    }

    fun addFavoritesByTitle(titles: List<String>) {
        viewModelScope.launch {
            val allStations = _state.value.groupedStations.values.flatten()
            val urlsToAdd = titles.mapNotNull { title ->
                allStations.find { station -> station.title == title }?.url
            }
            if (urlsToAdd.isNotEmpty()) {
                favoritesDataSource.addFavorites(urlsToAdd)
            }
        }
    }

    fun toggleEditMode() {
        _state.update { it.copy(isEditMode = !it.isEditMode) }
    }

    fun moveFavorite(fromIndex: Int, toIndex: Int) {
        val currentFavorites = _state.value.favorites.toMutableList()
        if (fromIndex !in currentFavorites.indices || toIndex !in currentFavorites.indices) return
        
        val item = currentFavorites.removeAt(fromIndex)
        currentFavorites.add(toIndex, item)
        
        _state.update { it.copy(favorites = currentFavorites) }
        viewModelScope.launch {
            favoritesDataSource.updateOrder(currentFavorites)
        }
    }

    fun currentStations(): List<RadioStation> {
        val currentCity = _state.value.selectedCity
        return currentCity?.let { _state.value.groupedStations[it].orEmpty() } ?: emptyList()
    }

    fun searchStations(query: String) {
        val trimmed = query.trim()
        val allStations = _state.value.groupedStations.values.flatten()
        val results = if (trimmed.isEmpty()) emptyList() else {
            val lower = trimmed.lowercase()
            allStations.filter {
                it.title.lowercase().contains(lower) ||
                        (it.city?.lowercase()?.contains(lower) ?: false)
            }
        }
        _state.update { it.copy(searchQuery = query, searchResults = results) }
    }

    fun clearSearch() {
        _state.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }
}
