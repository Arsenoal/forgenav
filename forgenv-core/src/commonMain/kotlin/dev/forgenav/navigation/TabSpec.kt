package dev.forgenav.navigation

/**
 * Definition of a primary tab / bottom-nav destination with its own back stack.
 */
data class TabSpec(
    val id: String,
    val startRoute: Route,
    val label: String = id,
    val iconKey: String? = null,
)
