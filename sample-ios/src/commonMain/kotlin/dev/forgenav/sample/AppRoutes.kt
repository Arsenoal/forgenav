package dev.forgenav.sample

import dev.forgenav.navigation.Route
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : Route {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data class TaskDetail(val id: String) : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object CreateTask : AppRoute
}
