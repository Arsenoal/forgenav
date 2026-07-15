package dev.forgenav.navigation

/**
 * Intercepts navigation before it mutates the stack (auth gates, feature flags, redirects).
 */
fun interface NavigationInterceptor {
    fun intercept(request: NavigationRequest): InterceptResult
}

data class NavigationRequest(
    val route: Route,
    val options: NavOptions,
    val stackId: String,
)

sealed interface InterceptResult {
    /** Apply the original navigation. */
    data object Proceed : InterceptResult

    /** Drop the navigation (no stack change). */
    data object Cancel : InterceptResult

    /** Navigate somewhere else instead. */
    data class Redirect(
        val route: Route,
        val options: NavOptions = NavOptions(),
    ) : InterceptResult
}

/**
 * Provider for conditional start destinations (e.g. logged-in vs logged-out).
 */
fun interface StartRouteProvider {
    fun startRoute(): Route
}
