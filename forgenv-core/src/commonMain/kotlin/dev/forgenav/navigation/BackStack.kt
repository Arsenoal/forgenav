package dev.forgenav.navigation

import dev.forgenav.util.randomId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Immutable snapshot of a navigation backstack.
 */
data class BackStackSnapshot(
    val entries: List<NavEntry>,
    val graphId: String,
) {
    val current: NavEntry? get() = entries.lastOrNull()
    val canPop: Boolean get() = entries.size > 1
    val size: Int get() = entries.size

    fun isEmpty(): Boolean = entries.isEmpty()
}

/**
 * Mutable, observable backstack used by [DefaultForgeNavigator].
 *
 * Thread-safety: all mutations go through [MutableStateFlow.update] and are safe from
 * multiple coroutines. UI layers should collect [snapshot] on the main dispatcher.
 */
class BackStack(
    val graphId: String,
    initialRoute: Route,
    initialMetadata: RouteMetadata = RouteMetadata(),
) {
    private val _snapshot = MutableStateFlow(
        BackStackSnapshot(
            entries = listOf(createEntry(initialRoute, initialMetadata)),
            graphId = graphId,
        ),
    )
    val snapshot: StateFlow<BackStackSnapshot> = _snapshot.asStateFlow()

    val current: NavEntry?
        get() = _snapshot.value.current

    val canPop: Boolean
        get() = _snapshot.value.canPop

    fun push(route: Route, metadata: RouteMetadata = RouteMetadata()) {
        _snapshot.update { current ->
            val entries = current.entries.toMutableList()
            when {
                metadata.clearBackStack -> {
                    BackStackSnapshot(listOf(createEntry(route, metadata)), graphId)
                }
                metadata.singleTop || metadata.launchSingleTop -> {
                    val top = entries.lastOrNull()
                    if (top != null && top.route.routeKey == route.routeKey && top.route == route) {
                        // Replace top with a fresh entry (new args / metadata)
                        entries[entries.lastIndex] = createEntry(route, metadata)
                        current.copy(entries = entries)
                    } else {
                        entries.add(createEntry(route, metadata))
                        current.copy(entries = entries)
                    }
                }
                else -> {
                    entries.add(createEntry(route, metadata))
                    current.copy(entries = entries)
                }
            }
        }
    }

    fun replace(route: Route, metadata: RouteMetadata = RouteMetadata()) {
        _snapshot.update { current ->
            if (current.entries.isEmpty()) {
                BackStackSnapshot(listOf(createEntry(route, metadata)), graphId)
            } else {
                val entries = current.entries.dropLast(1) + createEntry(route, metadata)
                current.copy(entries = entries)
            }
        }
    }

    /**
     * Pops the top entry. Returns `false` if the stack would become empty
     * (root is preserved).
     */
    fun pop(): Boolean {
        var didPop = false
        _snapshot.update { current ->
            if (current.entries.size <= 1) {
                current
            } else {
                didPop = true
                current.copy(entries = current.entries.dropLast(1))
            }
        }
        return didPop
    }

    /**
     * Pops until [predicate] matches the new top, or the root remains.
     * @return number of entries removed
     */
    fun popUntil(predicate: (NavEntry) -> Boolean): Int {
        var removed = 0
        _snapshot.update { current ->
            if (current.entries.size <= 1) return@update current
            val entries = current.entries.toMutableList()
            while (entries.size > 1 && !predicate(entries.last())) {
                entries.removeAt(entries.lastIndex)
                removed++
            }
            // If the last remaining non-root still doesn't match and size > 1, pop one more
            // only when predicate matches after loop — root is always kept.
            current.copy(entries = entries)
        }
        return removed
    }

    /**
     * Pops until [routeKey] is on top (or root).
     */
    fun popTo(routeKey: String, inclusive: Boolean = false): Int {
        var removed = 0
        _snapshot.update { current ->
            if (current.entries.size <= 1) return@update current
            val entries = current.entries.toMutableList()
            while (entries.size > 1) {
                val top = entries.last()
                if (top.route.routeKey == routeKey) {
                    if (inclusive && entries.size > 1) {
                        entries.removeAt(entries.lastIndex)
                        removed++
                    }
                    break
                }
                entries.removeAt(entries.lastIndex)
                removed++
            }
            current.copy(entries = entries)
        }
        return removed
    }

    fun reset(route: Route, metadata: RouteMetadata = RouteMetadata()) {
        _snapshot.value = BackStackSnapshot(listOf(createEntry(route, metadata)), graphId)
    }

    fun saveStateForTop(key: String, value: String) {
        _snapshot.update { current ->
            val top = current.current ?: return@update current
            val updated = top.copy(savedState = top.savedState + (key to value))
            current.copy(entries = current.entries.dropLast(1) + updated)
        }
    }

    fun restore(snapshot: BackStackSnapshot) {
        require(snapshot.graphId == graphId) {
            "Cannot restore snapshot for graph ${snapshot.graphId} into $graphId"
        }
        _snapshot.value = snapshot
    }

    private fun createEntry(route: Route, metadata: RouteMetadata): NavEntry =
        NavEntry(
            id = randomId(),
            route = route,
            metadata = metadata,
        )
}
