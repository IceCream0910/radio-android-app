package com.icecream.simplemediaplayer.ui

import com.icecream.simplemediaplayer.data.model.RadioStation

data class StationUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val cities: List<String> = emptyList(),
    val groupedStations: Map<String, List<RadioStation>> = emptyMap(),
    val selectedCity: String? = null,
    val favorites: Set<String> = emptySet()
)

