package dev.forgenav.navigation

import dev.forgenav.util.randomId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Immutable snapshot of a navigation backstack.
 *
 * This is the **read model**: hosts, collectors, and save/restore observe the full stack.
 * Mutations go through ops ([BackStackOp] / [BackStack.apply]), not by publishing intermediate
 * snapshots for multi-step work.
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
 * Diff-style write against a [BackStack]. Apply via [BackStack.apply] in one transaction so
 * [BackStack.snapshot] emits at most once.
 */
sealed interface BackStackOp {
    data class Push(
        val route: Route,
        val metadata: RouteMetadata = RouteMetadata(),
    ) : BackStackOp

    data class ReplaceTop(
        val route: Route,
        val metadata: RouteMetadata = RouteMetadata(),
    ) : BackStackOp

    /** Pop up to [count] entries; root is always preserved. */
    data class Pop(val count: Int = 1) : BackStackOp

    data class PopTo(
        val routeKey: String,
        val inclusive: Boolean = false,
    ) : BackStackOp

    data class PopUntil(
        val predicate: (NavEntry) -> Boolean,
    ) : BackStackOp

    /**
     * Replace the entire stack with [routes] (must be non-empty).
     * First entry uses [metadata] as-is; subsequent entries use the same metadata with
     * [RouteMetadata.clearBackStack] forced off so multi-route resets are not wiped mid-apply.
     */
    data class Reset(
        val routes: List<Route>,
        val metadata: RouteMetadata = RouteMetadata(),
    ) : BackStackOp

    /**
     * Keep root + last [maxSize]-1 entries when over capacity.
     * No-op when [maxSize] <= 1 or stack is within limit.
     */
    data class TrimToMaxSize(val maxSize: Int) : BackStackOp
}

/**
 * Outcome of a single [BackStack.apply] transaction.
 */
data class BackStackApplyResult(
    val previousTop: NavEntry?,
    val newTop: NavEntry?,
    val previousEntries: List<NavEntry>,
    val newEntries: List<NavEntry>,
    val removed: List<NavEntry>,
    val added: List<NavEntry>,
    val didChange: Boolean,
)

/**
 * Mutable, observable backstack used by [DefaultForgeNavigator].
 *
 * **Read:** full [snapshot]. **Write:** prefer [apply] with [BackStackOp]s so multi-step
 * navigation publishes one emission. Convenience methods ([push], [pop], …) are single-op
 * wrappers over [apply].
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

    /**
     * Applies [ops] in order and publishes **at most one** [snapshot] update.
     * Empty [ops] is a no-op and does not emit.
     */
    fun apply(ops: List<BackStackOp>): BackStackApplyResult {
        if (ops.isEmpty()) {
            val snap = _snapshot.value
            return BackStackApplyResult(
                previousTop = snap.current,
                newTop = snap.current,
                previousEntries = snap.entries,
                newEntries = snap.entries,
                removed = emptyList(),
                added = emptyList(),
                didChange = false,
            )
        }

        lateinit var result: BackStackApplyResult
        _snapshot.update { current ->
            val previousEntries = current.entries
            val previousTop = current.current
            var entries = previousEntries

            for (op in ops) {
                entries = when (op) {
                    is BackStackOp.Push ->
                        foldPush(entries, op.route, op.metadata).entries
                    is BackStackOp.ReplaceTop ->
                        foldReplaceTop(entries, op.route, op.metadata).entries
                    is BackStackOp.Pop ->
                        foldPop(entries, op.count).entries
                    is BackStackOp.PopTo ->
                        foldPopTo(entries, op.routeKey, op.inclusive).entries
                    is BackStackOp.PopUntil ->
                        foldPopUntil(entries, op.predicate).entries
                    is BackStackOp.Reset -> {
                        require(op.routes.isNotEmpty()) { "Reset routes must not be empty" }
                        foldReset(op.routes, op.metadata).entries
                    }
                    is BackStackOp.TrimToMaxSize ->
                        foldTrim(entries, op.maxSize).entries
                }
            }

            val prevIds = previousEntries.map { it.id }.toSet()
            val newIds = entries.map { it.id }.toSet()
            val removedFinal = previousEntries.filter { it.id !in newIds }
            val addedFinal = entries.filter { it.id !in prevIds }
            val sameIdentityOrder = previousEntries.size == entries.size &&
                previousEntries.zip(entries).all { (a, b) -> a.id == b.id }
            val changed = !sameIdentityOrder

            result = BackStackApplyResult(
                previousTop = previousTop,
                newTop = entries.lastOrNull(),
                previousEntries = previousEntries,
                newEntries = entries,
                removed = removedFinal,
                added = addedFinal,
                didChange = changed,
            )

            if (!changed) current else BackStackSnapshot(entries = entries, graphId = graphId)
        }
        return result
    }

    fun apply(vararg ops: BackStackOp): BackStackApplyResult = apply(ops.toList())

    fun push(route: Route, metadata: RouteMetadata = RouteMetadata()) {
        apply(BackStackOp.Push(route, metadata))
    }

    fun replace(route: Route, metadata: RouteMetadata = RouteMetadata()) {
        apply(BackStackOp.ReplaceTop(route, metadata))
    }

    /**
     * Pops the top entry. Returns `false` if the stack would become empty
     * (root is preserved).
     */
    fun pop(): Boolean = apply(BackStackOp.Pop(1)).didChange

    /**
     * Pops until [predicate] matches the new top, or the root remains.
     * @return number of entries removed
     */
    fun popUntil(predicate: (NavEntry) -> Boolean): Int =
        apply(BackStackOp.PopUntil(predicate)).removed.size

    /**
     * Pops until [routeKey] is on top (or root).
     */
    fun popTo(routeKey: String, inclusive: Boolean = false): Int =
        apply(BackStackOp.PopTo(routeKey, inclusive)).removed.size

    fun reset(route: Route, metadata: RouteMetadata = RouteMetadata()) {
        apply(BackStackOp.Reset(listOf(route), metadata))
    }

    fun reset(routes: List<Route>, metadata: RouteMetadata = RouteMetadata()) {
        apply(BackStackOp.Reset(routes, metadata))
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

    private data class Fold(
        val entries: List<NavEntry>,
        val removed: List<NavEntry> = emptyList(),
        val added: List<NavEntry> = emptyList(),
    )

    private fun foldPush(
        entries: List<NavEntry>,
        route: Route,
        metadata: RouteMetadata,
    ): Fold {
        when {
            metadata.clearBackStack -> {
                val next = createEntry(route, metadata)
                return Fold(
                    entries = listOf(next),
                    removed = entries,
                    added = listOf(next),
                )
            }
            metadata.singleTop || metadata.launchSingleTop -> {
                val top = entries.lastOrNull()
                if (top != null && top.route.routeKey == route.routeKey && top.route == route) {
                    val next = createEntry(route, metadata)
                    val newEntries = entries.dropLast(1) + next
                    return Fold(
                        entries = newEntries,
                        removed = listOf(top),
                        added = listOf(next),
                    )
                }
                val next = createEntry(route, metadata)
                return Fold(
                    entries = entries + next,
                    added = listOf(next),
                )
            }
            else -> {
                val next = createEntry(route, metadata)
                return Fold(
                    entries = entries + next,
                    added = listOf(next),
                )
            }
        }
    }

    private fun foldReplaceTop(
        entries: List<NavEntry>,
        route: Route,
        metadata: RouteMetadata,
    ): Fold {
        val next = createEntry(route, metadata)
        return if (entries.isEmpty()) {
            Fold(entries = listOf(next), added = listOf(next))
        } else {
            val top = entries.last()
            Fold(
                entries = entries.dropLast(1) + next,
                removed = listOf(top),
                added = listOf(next),
            )
        }
    }

    private fun foldPop(entries: List<NavEntry>, count: Int): Fold {
        if (count <= 0 || entries.size <= 1) return Fold(entries)
        val mutable = entries.toMutableList()
        val removed = mutableListOf<NavEntry>()
        var left = count
        while (left > 0 && mutable.size > 1) {
            removed += mutable.removeAt(mutable.lastIndex)
            left--
        }
        return if (removed.isEmpty()) Fold(entries) else Fold(mutable.toList(), removed = removed)
    }

    private fun foldPopTo(
        entries: List<NavEntry>,
        routeKey: String,
        inclusive: Boolean,
    ): Fold {
        if (entries.size <= 1) return Fold(entries)
        val mutable = entries.toMutableList()
        val removed = mutableListOf<NavEntry>()
        while (mutable.size > 1) {
            val top = mutable.last()
            if (top.route.routeKey == routeKey) {
                if (inclusive && mutable.size > 1) {
                    removed += mutable.removeAt(mutable.lastIndex)
                }
                break
            }
            removed += mutable.removeAt(mutable.lastIndex)
        }
        return if (removed.isEmpty()) Fold(entries) else Fold(mutable.toList(), removed = removed)
    }

    private fun foldPopUntil(
        entries: List<NavEntry>,
        predicate: (NavEntry) -> Boolean,
    ): Fold {
        if (entries.size <= 1) return Fold(entries)
        val mutable = entries.toMutableList()
        val removed = mutableListOf<NavEntry>()
        while (mutable.size > 1 && !predicate(mutable.last())) {
            removed += mutable.removeAt(mutable.lastIndex)
        }
        return if (removed.isEmpty()) Fold(entries) else Fold(mutable.toList(), removed = removed)
    }

    private fun foldReset(routes: List<Route>, metadata: RouteMetadata): Fold {
        val newEntries = routes.mapIndexed { index, route ->
            val meta = if (index == 0) metadata else metadata.copy(clearBackStack = false)
            createEntry(route, meta)
        }
        return Fold(entries = newEntries, added = newEntries)
    }

    private fun foldTrim(entries: List<NavEntry>, maxSize: Int): Fold {
        if (maxSize <= 1 || entries.size <= maxSize) return Fold(entries)
        val keptTail = entries.takeLast(maxSize - 1)
        val root = entries.first()
        val trimmed = listOf(root) + keptTail
        // Prefer keeping root even if it was also in tail (won't be for maxSize>=2 unique)
        val newEntries = if (keptTail.any { it.id == root.id }) {
            // degenerate: root duplicated in tail — fall back to takeLast only
            entries.takeLast(maxSize)
        } else {
            trimmed
        }
        val newIds = newEntries.map { it.id }.toSet()
        val removed = entries.filter { it.id !in newIds }
        return Fold(entries = newEntries, removed = removed)
    }

    private fun createEntry(route: Route, metadata: RouteMetadata): NavEntry =
        NavEntry(
            id = randomId(),
            route = route,
            metadata = metadata,
        )
}
