package com.icecream.simplemediaplayer.ui

import com.icecream.simplemediaplayer.data.model.RadioStation

data class StationUiState(
    val isLoading: Boolean = false,
    val groupedStations: Map<String, List<RadioStation>> = emptyMap(),
    val cities: List<String> = emptyList(),
    val selectedCity: String? = null,
    val favorites: List<String> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<RadioStation> = emptyList(),
    val isEditMode: Boolean = false
)
