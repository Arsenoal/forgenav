package dev.forgenav.navigation

import kotlinx.serialization.Serializable

/**
 * Versioned, kotlinx-serializable navigation state for process death / config change restore.
 *
 * Routes are stored as [SavedRoutePayload] via [RouteCodec] so apps keep ownership of the
 * sealed hierarchy without ForgeNav depending on app types.
 */
@Serializable
data class SavedNavigatorState(
    val version: Int = CURRENT_VERSION,
    val root: SavedBackStackState,
    val nested: Map<String, SavedBackStackState> = emptyMap(),
    val pendingDeepLinks: List<String> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}

@Serializable
data class SavedBackStackState(
    val graphId: String,
    val entries: List<SavedNavEntry>,
)

@Serializable
data class SavedNavEntry(
    val id: String,
    val route: SavedRoutePayload,
    val metadata: RouteMetadata = RouteMetadata(),
    val entrySavedState: Map<String, String> = emptyMap(),
)

/**
 * Opaque route blob: [family] selects a registered codec; [payloadJson] is the encoded route.
 */
@Serializable
data class SavedRoutePayload(
    val family: String,
    val payloadJson: String,
)
