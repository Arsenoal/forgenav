package dev.forgenav.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converts live [BackStackSnapshot] / navigator stacks to [SavedNavigatorState] and back.
 */
class NavStateSerializer(
    private val routeCodec: RouteCodec,
    private val json: Json = RouteCodec.defaultJson,
) {
    fun encodeBackStack(snapshot: BackStackSnapshot): SavedBackStackState =
        SavedBackStackState(
            graphId = snapshot.graphId,
            entries = snapshot.entries.map { entry ->
                SavedNavEntry(
                    id = entry.id,
                    route = routeCodec.encode(entry.route),
                    metadata = entry.metadata,
                    entrySavedState = entry.savedState,
                )
            },
        )

    fun decodeBackStack(saved: SavedBackStackState): BackStackSnapshot {
        require(saved.entries.isNotEmpty()) {
            "Cannot restore empty backstack for graph ${saved.graphId}"
        }
        val entries = saved.entries.map { entry ->
            val route = routeCodec.decode(entry.route)
            NavEntry(
                id = entry.id,
                route = route,
                metadata = entry.metadata,
                savedState = entry.entrySavedState,
            )
        }
        return BackStackSnapshot(entries = entries, graphId = saved.graphId)
    }

    fun encodeNavigator(
        root: BackStackSnapshot,
        nested: Map<String, BackStackSnapshot>,
        pendingDeepLinks: List<String> = emptyList(),
    ): SavedNavigatorState =
        SavedNavigatorState(
            version = SavedNavigatorState.CURRENT_VERSION,
            root = encodeBackStack(root),
            nested = nested.mapValues { (_, snap) -> encodeBackStack(snap) },
            pendingDeepLinks = pendingDeepLinks,
        )

    fun decodeNavigator(state: SavedNavigatorState): DecodedNavigatorState {
        require(state.version <= SavedNavigatorState.CURRENT_VERSION) {
            "Unsupported SavedNavigatorState version ${state.version}"
        }
        return DecodedNavigatorState(
            root = decodeBackStack(state.root),
            nested = state.nested.mapValues { (_, saved) -> decodeBackStack(saved) },
            pendingDeepLinks = state.pendingDeepLinks,
        )
    }

    fun encodeToString(state: SavedNavigatorState): String =
        json.encodeToString(SavedNavigatorState.serializer(), state)

    fun decodeFromString(payload: String): SavedNavigatorState =
        json.decodeFromString(SavedNavigatorState.serializer(), payload)
}

data class DecodedNavigatorState(
    val root: BackStackSnapshot,
    val nested: Map<String, BackStackSnapshot>,
    val pendingDeepLinks: List<String>,
)
