package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object LiveCamera : AppRoute

    @Serializable
    data object PhotoStudio : AppRoute
}
