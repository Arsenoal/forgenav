package dev.forgenav.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Cross-platform navigation API.
 *
 * Implementations own one or more [BackStack]s (root + nested graphs) and expose a single
 * [currentEntry] / [backStack] for the active graph. Compose hosts observe these flows and
 * render destinations.
 *
 * Lifecycle: call [onStart] / [onStop] from the platform lifecycle owner so deep-link
 * processing and pending navigations can be deferred while backgrounded.
 *
 * Process death: [saveState] / [restoreState] with a [RouteCodec] (see [rememberSaveableForgeNavigator]).
 */
interface ForgeNavigator {
    val backStack: StateFlow<BackStackSnapshot>
    val currentEntry: NavEntry?
    val canPop: Boolean
    val events: SharedFlow<NavEvent>

    fun navigate(route: Route, metadata: RouteMetadata = RouteMetadata())
    fun replace(route: Route, metadata: RouteMetadata = RouteMetadata())
    fun popBackStack(): Boolean
    fun popBackStack(routeKey: String, inclusive: Boolean = false): Boolean
    fun popUntil(predicate: (NavEntry) -> Boolean): Boolean
    fun reset(route: Route, metadata: RouteMetadata = RouteMetadata())

    /** Nested graph APIs — push a child navigator or switch active nested stack. */
    fun navigateNested(graphId: String, route: Route, metadata: RouteMetadata = RouteMetadata())
    fun popNested(graphId: String): Boolean
    fun nestedBackStack(graphId: String): StateFlow<BackStackSnapshot>?

    /** Deep link entry points. */
    fun handleDeepLink(uri: String): Boolean
    fun handleDeepLink(deepLink: DeepLink): Boolean

    /**
     * Snapshot the full navigator (root + nested + pending deep links) for process death.
     * Returns `null` when no [RouteCodec] is configured.
     */
    fun saveState(): SavedNavigatorState?

    /**
     * Hydrate from [saveState]. Returns `false` if codec missing, version unsupported,
     * or any route fails to decode.
     */
    fun restoreState(state: SavedNavigatorState): Boolean

    fun onStart()
    fun onStop()
    fun dispose()
}

/**
 * Side-channel events for analytics, logging, and Compose effects (e.g. snackbars).
 */
sealed interface NavEvent {
    data class Navigated(val from: NavEntry?, val to: NavEntry, val metadata: RouteMetadata) : NavEvent
    data class Popped(val from: NavEntry, val to: NavEntry?) : NavEvent
    data class Replaced(val from: NavEntry?, val to: NavEntry) : NavEvent
    data class DeepLinkHandled(val deepLink: DeepLink, val route: Route) : NavEvent
    data class DeepLinkFailed(val uri: String, val reason: String) : NavEvent
    data class NestedNavigated(val graphId: String, val route: Route) : NavEvent
    data class StateRestored(val entryCount: Int) : NavEvent
}

/**
 * Default in-memory [ForgeNavigator] suitable for all KMP targets.
 */
class DefaultForgeNavigator(
    private val rootGraph: NavGraph,
    private val deepLinkParser: DeepLinkParser = DeepLinkParser(rootGraph),
    private val config: NavigatorConfig = NavigatorConfig(),
    private val routeCodec: RouteCodec? = null,
) : ForgeNavigator {

    private val rootStack = BackStack(rootGraph.id, rootGraph.startRoute)
    private val nestedStacks = mutableMapOf<String, BackStack>()
    private var started = false
    private val pendingDeepLinks = ArrayDeque<String>()

    private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<NavEvent> = _events.asSharedFlow()

    override val backStack: StateFlow<BackStackSnapshot> = rootStack.snapshot
    override val currentEntry: NavEntry? get() = rootStack.current
    override val canPop: Boolean get() = rootStack.canPop

    private val stateSerializer: NavStateSerializer?
        get() = routeCodec?.let { NavStateSerializer(it) }

    init {
        rootGraph.children.forEach { (id, graph) ->
            nestedStacks[id] = BackStack(graph.id, graph.startRoute)
        }
    }

    override fun navigate(route: Route, metadata: RouteMetadata) {
        val from = rootStack.current
        when (metadata.presentation) {
            PresentationStyle.Screen,
            PresentationStyle.Dialog,
            PresentationStyle.BottomSheet,
            -> rootStack.push(route, metadata)
        }
        val to = rootStack.current
        if (to != null) {
            _events.tryEmit(NavEvent.Navigated(from, to, metadata))
        }
        trimIfNeeded()
    }

    override fun replace(route: Route, metadata: RouteMetadata) {
        val from = rootStack.current
        rootStack.replace(route, metadata)
        val to = rootStack.current
        if (to != null) {
            _events.tryEmit(NavEvent.Replaced(from, to))
        }
    }

    override fun popBackStack(): Boolean {
        val from = rootStack.current ?: return false
        val ok = rootStack.pop()
        if (ok) {
            _events.tryEmit(NavEvent.Popped(from, rootStack.current))
        }
        return ok
    }

    override fun popBackStack(routeKey: String, inclusive: Boolean): Boolean {
        val from = rootStack.current ?: return false
        val removed = rootStack.popTo(routeKey, inclusive)
        if (removed > 0) {
            _events.tryEmit(NavEvent.Popped(from, rootStack.current))
            return true
        }
        return false
    }

    override fun popUntil(predicate: (NavEntry) -> Boolean): Boolean {
        val from = rootStack.current ?: return false
        val removed = rootStack.popUntil(predicate)
        if (removed > 0) {
            _events.tryEmit(NavEvent.Popped(from, rootStack.current))
            return true
        }
        return false
    }

    override fun reset(route: Route, metadata: RouteMetadata) {
        val from = rootStack.current
        rootStack.reset(route, metadata)
        val to = rootStack.current
        if (to != null) {
            _events.tryEmit(NavEvent.Replaced(from, to))
        }
    }

    override fun navigateNested(graphId: String, route: Route, metadata: RouteMetadata) {
        val stack = nestedStacks.getOrPut(graphId) {
            val graph = rootGraph.children[graphId]
                ?: error("Unknown nested graph: $graphId")
            BackStack(graph.id, graph.startRoute)
        }
        stack.push(route, metadata)
        _events.tryEmit(NavEvent.NestedNavigated(graphId, route))
    }

    override fun popNested(graphId: String): Boolean {
        val stack = nestedStacks[graphId] ?: return false
        return stack.pop()
    }

    override fun nestedBackStack(graphId: String): StateFlow<BackStackSnapshot>? =
        nestedStacks[graphId]?.snapshot

    override fun handleDeepLink(uri: String): Boolean {
        if (!started && config.deferDeepLinksUntilStarted) {
            pendingDeepLinks.addLast(uri)
            return true
        }
        val deepLink = deepLinkParser.parse(uri)
        return if (deepLink != null) {
            handleDeepLink(deepLink)
        } else {
            _events.tryEmit(NavEvent.DeepLinkFailed(uri, "No matching route"))
            false
        }
    }

    override fun handleDeepLink(deepLink: DeepLink): Boolean {
        val route = deepLink.route
        val metadata = RouteMetadata(
            presentation = deepLink.presentation,
            clearBackStack = deepLink.clearBackStack,
            singleTop = deepLink.singleTop,
            extras = deepLink.extras,
        )
        if (deepLink.nestedGraphId != null) {
            navigateNested(deepLink.nestedGraphId, route, metadata)
        } else {
            navigate(route, metadata)
        }
        _events.tryEmit(NavEvent.DeepLinkHandled(deepLink, route))
        return true
    }

    override fun saveState(): SavedNavigatorState? {
        val serializer = stateSerializer ?: return null
        val nestedSnaps = nestedStacks.mapValues { (_, stack) -> stack.snapshot.value }
        return serializer.encodeNavigator(
            root = rootStack.snapshot.value,
            nested = nestedSnaps,
            pendingDeepLinks = pendingDeepLinks.toList(),
        )
    }

    override fun restoreState(state: SavedNavigatorState): Boolean {
        val serializer = stateSerializer ?: return false
        val decoded = runCatching { serializer.decodeNavigator(state) }.getOrNull() ?: return false
        if (decoded.root.graphId != rootGraph.id && config.strictGraphIdOnRestore) {
            return false
        }
        if (decoded.root.entries.isEmpty()) return false

        rootStack.restore(
            decoded.root.copy(graphId = rootGraph.id),
        )

        nestedStacks.clear()
        rootGraph.children.forEach { (id, graph) ->
            val saved = decoded.nested[id]
            if (saved != null && saved.entries.isNotEmpty()) {
                val stack = BackStack(graph.id, graph.startRoute)
                stack.restore(saved.copy(graphId = graph.id))
                nestedStacks[id] = stack
            } else {
                nestedStacks[id] = BackStack(graph.id, graph.startRoute)
            }
        }
        // Nested stacks present in saved state but not in graph children (feature flags, etc.)
        decoded.nested.forEach { (id, snap) ->
            if (id !in nestedStacks && snap.entries.isNotEmpty()) {
                val start = snap.entries.first().route
                val stack = BackStack(id, start)
                stack.restore(snap)
                nestedStacks[id] = stack
            }
        }

        pendingDeepLinks.clear()
        pendingDeepLinks.addAll(decoded.pendingDeepLinks)

        _events.tryEmit(NavEvent.StateRestored(entryCount = decoded.root.entries.size))
        return true
    }

    override fun onStart() {
        started = true
        while (pendingDeepLinks.isNotEmpty()) {
            handleDeepLink(pendingDeepLinks.removeFirst())
        }
    }

    override fun onStop() {
        started = false
    }

    override fun dispose() {
        nestedStacks.clear()
        pendingDeepLinks.clear()
    }

    private fun trimIfNeeded() {
        val max = config.maxBackStackSize
        if (max <= 1) return
        val snap = rootStack.snapshot.value
        if (snap.entries.size <= max) return
        // Keep root + newest destinations.
        val trimmed = listOf(snap.entries.first()) + snap.entries.takeLast(max - 1)
        rootStack.restore(snap.copy(entries = trimmed))
    }
}

/**
 * Configuration knobs for [DefaultForgeNavigator].
 */
data class NavigatorConfig(
    val deferDeepLinksUntilStarted: Boolean = true,
    val maxBackStackSize: Int = 64,
    /** When true, restore fails if saved root graphId does not match. */
    val strictGraphIdOnRestore: Boolean = false,
)

/**
 * Convenience factory.
 */
fun ForgeNavigator(
    startRoute: Route,
    graphId: String = "root",
    children: Map<String, NavGraph> = emptyMap(),
    deepLinkParser: DeepLinkParser? = null,
    config: NavigatorConfig = NavigatorConfig(),
    routeCodec: RouteCodec? = null,
    savedState: SavedNavigatorState? = null,
): ForgeNavigator {
    val graph = NavGraph(id = graphId, startRoute = startRoute, children = children)
    val navigator = DefaultForgeNavigator(
        rootGraph = graph,
        deepLinkParser = deepLinkParser ?: DeepLinkParser(graph),
        config = config,
        routeCodec = routeCodec,
    )
    if (savedState != null) {
        navigator.restoreState(savedState)
    }
    return navigator
}
