package dev.forgenav.navigation

import kotlinx.serialization.Serializable

/**
 * Marker for a type-safe navigation destination.
 *
 * Routes should be `@Serializable` sealed interfaces (or data classes nested under a sealed
 * hierarchy) so [DeepLinkParser] and platform deep-link handlers can encode/decode arguments
 * without reflection or hand-written parsers.
 *
 * Example:
 * ```
 * @Serializable
 * sealed interface AppRoute : Route {
 *     @Serializable data object Home : AppRoute
 *     @Serializable data class Detail(val id: String) : AppRoute
 * }
 * ```
 */
interface Route {
    /**
     * Stable identity for equality and backstack dedupe.
     * Defaults to the runtime class name; override for dynamic routes if needed.
     */
    val routeKey: String
        get() = this::class.simpleName ?: "Route"
}

/**
 * How a [Route] is presented in the UI layer.
 * Nested navigators and modal surfaces (dialog / bottom sheet) use this to pick a host.
 */
@Serializable
enum class PresentationStyle {
    /** Full-screen destination inside the current [NavGraph]. */
    Screen,

    /** Modal dialog over the current backstack. */
    Dialog,

    /** Bottom sheet (or platform equivalent) over the current backstack. */
    BottomSheet,
}

/**
 * Optional metadata attached to a destination when it is pushed onto a stack.
 */
@Serializable
data class RouteMetadata(
    val presentation: PresentationStyle = PresentationStyle.Screen,
    val clearBackStack: Boolean = false,
    val singleTop: Boolean = false,
    val launchSingleTop: Boolean = false,
    /** Opaque extras for analytics / deep-link provenance. */
    val extras: Map<String, String> = emptyMap(),
)

/**
 * A concrete entry living on a [BackStack].
 *
 * Not kotlinx-serializable as a whole because [Route] is an open hierarchy;
 * persist via your own route codec or [DeepLinkParser.buildUri].
 */
data class NavEntry(
    val id: String,
    val route: Route,
    val metadata: RouteMetadata = RouteMetadata(),
    val savedState: Map<String, String> = emptyMap(),
)

/**
 * Named navigation graph for nested navigation.
 *
 * Graphs form a tree: the root graph owns the primary backstack; child graphs
 * are pushed as nested hosts (tabs, feature modules, multi-pane layouts).
 */
data class NavGraph(
    val id: String,
    val startRoute: Route,
    val children: Map<String, NavGraph> = emptyMap(),
)
