package com.icecream.simplemediaplayer.ui.navigation

enum class BottomNavDestinations(val route: String, val icon: String, val label: String) {
    HOME("home", "radio", "전체"),
    FAVORITES("favorites", "favorite", "자주 듣는"),
    SETTINGS("settings", "settings", "설정")
}

