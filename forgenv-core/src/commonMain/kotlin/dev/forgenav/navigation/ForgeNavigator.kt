package dev.forgenav.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-platform navigation API (Nav3-level surface for product apps).
 *
 * @see docs/NAV3_PARITY.md
 */
interface ForgeNavigator {
    val backStack: StateFlow<BackStackSnapshot>
    val currentEntry: NavEntry?
    val canPop: Boolean
    val events: SharedFlow<NavEvent>

    /** Tab / multi-stack: empty when not using tabs. */
    val tabs: List<TabSpec>
    val selectedTabId: StateFlow<String?>

    fun navigate(route: Route, metadata: RouteMetadata = RouteMetadata())
    fun navigate(route: Route, options: NavOptions)
    fun replace(route: Route, metadata: RouteMetadata = RouteMetadata())
    fun replace(route: Route, options: NavOptions)
    fun popBackStack(): Boolean
    fun popBackStack(routeKey: String, inclusive: Boolean = false): Boolean
    fun popUntil(predicate: (NavEntry) -> Boolean): Boolean
    fun popBackStack(count: Int): Boolean
    fun reset(route: Route, metadata: RouteMetadata = RouteMetadata())
    fun setBackStack(routes: List<Route>, metadata: RouteMetadata = RouteMetadata())

    fun selectTab(tabId: String)
    fun tabBackStack(tabId: String): StateFlow<BackStackSnapshot>?
    fun navigateInTab(tabId: String, route: Route, options: NavOptions = NavOptions())

    fun navigateNested(graphId: String, route: Route, metadata: RouteMetadata = RouteMetadata())
    fun popNested(graphId: String): Boolean
    fun nestedBackStack(graphId: String): StateFlow<BackStackSnapshot>?

    fun handleDeepLink(uri: String): Boolean
    fun handleDeepLink(deepLink: DeepLink): Boolean

    /**
     * Navigate and await a result when the destination calls [setResult] or is cancelled via back.
     */
    suspend fun navigateForResult(route: Route, options: NavOptions = NavOptions()): NavResult

    fun setResult(value: Any?)
    fun clearResult()

    fun addInterceptor(interceptor: NavigationInterceptor)
    fun removeInterceptor(interceptor: NavigationInterceptor)

    fun saveState(): SavedNavigatorState?
    fun restoreState(state: SavedNavigatorState): Boolean

    fun onStart()
    fun onStop()
    fun dispose()
}

sealed interface NavEvent {
    data class Navigated(val from: NavEntry?, val to: NavEntry, val metadata: RouteMetadata) : NavEvent
    data class Popped(val from: NavEntry, val to: NavEntry?) : NavEvent
    data class Replaced(val from: NavEntry?, val to: NavEntry) : NavEvent
    data class DeepLinkHandled(val deepLink: DeepLink, val route: Route) : NavEvent
    data class DeepLinkFailed(val uri: String, val reason: String) : NavEvent
    data class NestedNavigated(val graphId: String, val route: Route) : NavEvent
    data class TabSelected(val tabId: String) : NavEvent
    data class NavigationCancelled(val route: Route, val reason: String) : NavEvent
    data class StateRestored(val entryCount: Int) : NavEvent
    data class ResultDelivered(val requestId: String, val result: NavResult) : NavEvent
}

class DefaultForgeNavigator(
    private val rootGraph: NavGraph,
    private val deepLinkParser: DeepLinkParser = DeepLinkParser(rootGraph),
    private val config: NavigatorConfig = NavigatorConfig(),
    private val routeCodec: RouteCodec? = null,
    tabs: List<TabSpec> = emptyList(),
    private val startRouteProvider: StartRouteProvider? = null,
    interceptors: List<NavigationInterceptor> = emptyList(),
) : ForgeNavigator {

    override val tabs: List<TabSpec> = tabs

    private val rootStack = BackStack(
        rootGraph.id,
        startRouteProvider?.startRoute() ?: rootGraph.startRoute,
    )
    private val nestedStacks = mutableMapOf<String, BackStack>()
    private val savedTabStacks = mutableMapOf<String, BackStackSnapshot>()
    private var started = false
    private val pendingDeepLinks = ArrayDeque<String>()
    private val interceptorList = interceptors.toMutableList()
    private val resultHub = NavResultHub()

    private val _selectedTabId = MutableStateFlow(tabs.firstOrNull()?.id)
    override val selectedTabId: StateFlow<String?> = _selectedTabId.asStateFlow()

    private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<NavEvent> = _events.asSharedFlow()

    private val stateSerializer: NavStateSerializer?
        get() = routeCodec?.let { NavStateSerializer(it) }

    init {
        rootGraph.children.forEach { (id, graph) ->
            nestedStacks[id] = BackStack(graph.id, graph.startRoute)
        }
        tabs.forEach { tab ->
            nestedStacks.getOrPut(tab.id) { BackStack(tab.id, tab.startRoute) }
        }
    }

    private fun activeStack(): BackStack {
        val tabId = _selectedTabId.value
        return if (tabId != null && tabs.isNotEmpty()) {
            nestedStacks.getOrPut(tabId) {
                val tab = tabs.first { it.id == tabId }
                BackStack(tab.id, tab.startRoute)
            }
        } else {
            rootStack
        }
    }

    private fun activeStackId(): String = _selectedTabId.value ?: rootGraph.id

    override val backStack: StateFlow<BackStackSnapshot>
        get() = activeStack().snapshot

    override val currentEntry: NavEntry?
        get() = activeStack().current

    override val canPop: Boolean
        get() = activeStack().canPop

    override fun navigate(route: Route, metadata: RouteMetadata) {
        navigate(route, NavOptions.fromMetadata(metadata))
    }

    override fun navigate(route: Route, options: NavOptions) {
        val resolved = intercept(route, options) ?: return
        applyNavigate(activeStack(), activeStackId(), resolved.first, resolved.second)
    }

    override fun replace(route: Route, metadata: RouteMetadata) {
        replace(route, NavOptions.fromMetadata(metadata))
    }

    override fun replace(route: Route, options: NavOptions) {
        val resolved = intercept(route, options) ?: return
        val stack = activeStack()
        val from = stack.current
        stack.replace(resolved.first, resolved.second.toMetadata())
        stack.current?.let { _events.tryEmit(NavEvent.Replaced(from, it)) }
    }

    override fun popBackStack(): Boolean {
        val stack = activeStack()
        val from = stack.current ?: return false
        cancelResultIfNeeded(from)
        val ok = stack.pop()
        if (ok) {
            _events.tryEmit(NavEvent.Popped(from, stack.current))
        }
        return ok
    }

    override fun popBackStack(routeKey: String, inclusive: Boolean): Boolean {
        val stack = activeStack()
        val from = stack.current ?: return false
        cancelResultIfNeeded(from)
        val removed = stack.popTo(routeKey, inclusive)
        if (removed > 0) {
            _events.tryEmit(NavEvent.Popped(from, stack.current))
            return true
        }
        return false
    }

    override fun popUntil(predicate: (NavEntry) -> Boolean): Boolean {
        val stack = activeStack()
        val from = stack.current ?: return false
        cancelResultIfNeeded(from)
        val removed = stack.popUntil(predicate)
        if (removed > 0) {
            _events.tryEmit(NavEvent.Popped(from, stack.current))
            return true
        }
        return false
    }

    override fun popBackStack(count: Int): Boolean {
        if (count <= 0) return false
        var any = false
        repeat(count) {
            if (!popBackStack()) return any
            any = true
        }
        return any
    }

    override fun reset(route: Route, metadata: RouteMetadata) {
        val stack = activeStack()
        val from = stack.current
        stack.reset(route, metadata)
        stack.current?.let { _events.tryEmit(NavEvent.Replaced(from, it)) }
    }

    override fun setBackStack(routes: List<Route>, metadata: RouteMetadata) {
        require(routes.isNotEmpty()) { "routes must not be empty" }
        val stack = activeStack()
        val from = stack.current
        stack.reset(routes.first(), metadata)
        routes.drop(1).forEach { stack.push(it, metadata) }
        stack.current?.let { _events.tryEmit(NavEvent.Replaced(from, it)) }
        trimIfNeeded(stack)
    }

    override fun selectTab(tabId: String) {
        require(tabs.any { it.id == tabId }) { "Unknown tab: $tabId" }
        val previous = _selectedTabId.value
        if (previous == tabId) return
        // Optionally save previous tab stack snapshot
        if (previous != null) {
            nestedStacks[previous]?.let { savedTabStacks[previous] = it.snapshot.value }
        }
        if (_selectedTabId.value != null) {
            // restore saved if present
            val saved = savedTabStacks[tabId]
            val stack = nestedStacks.getOrPut(tabId) {
                val tab = tabs.first { it.id == tabId }
                BackStack(tab.id, tab.startRoute)
            }
            if (saved != null) {
                stack.restore(saved)
            }
        }
        _selectedTabId.value = tabId
        _events.tryEmit(NavEvent.TabSelected(tabId))
    }

    override fun tabBackStack(tabId: String): StateFlow<BackStackSnapshot>? =
        nestedStacks[tabId]?.snapshot

    override fun navigateInTab(tabId: String, route: Route, options: NavOptions) {
        selectTab(tabId)
        navigate(route, options)
    }

    override fun navigateNested(graphId: String, route: Route, metadata: RouteMetadata) {
        val stack = nestedStacks.getOrPut(graphId) {
            val graph = rootGraph.children[graphId]
                ?: tabs.find { it.id == graphId }?.let { NavGraph(it.id, it.startRoute) }
                ?: error("Unknown nested graph: $graphId")
            BackStack(graph.id, graph.startRoute)
        }
        stack.push(route, metadata)
        _events.tryEmit(NavEvent.NestedNavigated(graphId, route))
    }

    override fun popNested(graphId: String): Boolean {
        val stack = nestedStacks[graphId] ?: return false
        val from = stack.current
        cancelResultIfNeeded(from)
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
        val options = NavOptions(
            presentation = deepLink.presentation,
            clearBackStack = deepLink.clearBackStack,
            singleTop = deepLink.singleTop,
            popUpToRouteKey = deepLink.popUpToRouteKey,
            popUpToInclusive = deepLink.popUpToInclusive,
            extras = deepLink.extras,
        )

        // Select tab / nested stack if requested
        deepLink.nestedGraphId?.let { graphId ->
            if (tabs.any { it.id == graphId }) {
                selectTab(graphId)
            }
        }

        val stackRoutes = deepLink.stackRoutes
        if (stackRoutes.isNotEmpty()) {
            val targetStack = when {
                deepLink.nestedGraphId != null ->
                    nestedStacks.getOrPut(deepLink.nestedGraphId) {
                        BackStack(deepLink.nestedGraphId, stackRoutes.first())
                    }
                else -> activeStack()
            }
            targetStack.reset(stackRoutes.first(), options.toMetadata())
            stackRoutes.drop(1).forEach { r ->
                targetStack.push(r, options.toMetadata().copy(clearBackStack = false))
            }
            _events.tryEmit(NavEvent.DeepLinkHandled(deepLink, stackRoutes.last()))
            return true
        }

        if (deepLink.nestedGraphId != null && tabs.none { it.id == deepLink.nestedGraphId }) {
            navigateNested(deepLink.nestedGraphId, deepLink.route, options.toMetadata())
        } else {
            navigate(deepLink.route, options)
        }
        _events.tryEmit(NavEvent.DeepLinkHandled(deepLink, deepLink.route))
        return true
    }

    override suspend fun navigateForResult(route: Route, options: NavOptions): NavResult {
        val requestId = resultHub.createRequestId()
        val deferred = resultHub.register(requestId)
        val opts = options.copy(resultRequestId = requestId)
        navigate(route, opts)
        return deferred.await()
    }

    override fun setResult(value: Any?) {
        val requestId = resultHub.pendingRequestIdOnEntry(currentEntry) ?: return
        if (resultHub.complete(requestId, NavResult.Ok(value))) {
            _events.tryEmit(NavEvent.ResultDelivered(requestId, NavResult.Ok(value)))
        }
    }

    override fun clearResult() {
        val requestId = resultHub.pendingRequestIdOnEntry(currentEntry) ?: return
        if (resultHub.cancel(requestId)) {
            _events.tryEmit(NavEvent.ResultDelivered(requestId, NavResult.Cancelled))
        }
    }

    override fun addInterceptor(interceptor: NavigationInterceptor) {
        interceptorList += interceptor
    }

    override fun removeInterceptor(interceptor: NavigationInterceptor) {
        interceptorList -= interceptor
    }

    override fun saveState(): SavedNavigatorState? {
        val serializer = stateSerializer ?: return null
        val nestedSnaps = nestedStacks.mapValues { (_, stack) -> stack.snapshot.value }
        return serializer.encodeNavigator(
            root = rootStack.snapshot.value,
            nested = nestedSnaps,
            pendingDeepLinks = pendingDeepLinks.toList(),
            selectedTabId = _selectedTabId.value,
        )
    }

    override fun restoreState(state: SavedNavigatorState): Boolean {
        val serializer = stateSerializer ?: return false
        val decoded = runCatching { serializer.decodeNavigator(state) }.getOrNull() ?: return false
        if (decoded.root.graphId != rootGraph.id && config.strictGraphIdOnRestore) {
            return false
        }
        if (decoded.root.entries.isEmpty() && tabs.isEmpty()) return false

        if (decoded.root.entries.isNotEmpty()) {
            rootStack.restore(decoded.root.copy(graphId = rootGraph.id))
        }

        nestedStacks.clear()
        rootGraph.children.forEach { (id, graph) ->
            val saved = decoded.nested[id]
            val stack = BackStack(graph.id, graph.startRoute)
            if (saved != null && saved.entries.isNotEmpty()) {
                stack.restore(saved.copy(graphId = graph.id))
            }
            nestedStacks[id] = stack
        }
        tabs.forEach { tab ->
            val saved = decoded.nested[tab.id]
            val stack = nestedStacks.getOrPut(tab.id) { BackStack(tab.id, tab.startRoute) }
            if (saved != null && saved.entries.isNotEmpty()) {
                stack.restore(saved.copy(graphId = tab.id))
            }
        }
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
        decoded.selectedTabId?.let { id ->
            if (tabs.any { it.id == id }) _selectedTabId.value = id
        }

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
        resultHub.cancelAll()
        nestedStacks.clear()
        pendingDeepLinks.clear()
        interceptorList.clear()
    }

    private fun intercept(route: Route, options: NavOptions): Pair<Route, NavOptions>? {
        var currentRoute = route
        var currentOptions = options
        for (interceptor in interceptorList.toList()) {
            when (
                val result = interceptor.intercept(
                    NavigationRequest(currentRoute, currentOptions, activeStackId()),
                )
            ) {
                InterceptResult.Proceed -> Unit
                InterceptResult.Cancel -> {
                    _events.tryEmit(NavEvent.NavigationCancelled(currentRoute, "interceptor"))
                    return null
                }
                is InterceptResult.Redirect -> {
                    currentRoute = result.route
                    currentOptions = result.options
                }
            }
        }
        return currentRoute to currentOptions
    }

    private fun applyNavigate(
        stack: BackStack,
        stackId: String,
        route: Route,
        options: NavOptions,
    ) {
        val from = stack.current
        options.popUpToRouteKey?.let { key ->
            stack.popTo(key, options.popUpToInclusive)
        }
        val metadata = options.toMetadata()
        when {
            options.clearBackStack -> stack.reset(route, metadata)
            else -> stack.push(route, metadata)
        }
        stack.current?.let { _events.tryEmit(NavEvent.Navigated(from, it, metadata)) }
        trimIfNeeded(stack)
    }

    private fun cancelResultIfNeeded(entry: NavEntry?) {
        val requestId = resultHub.pendingRequestIdOnEntry(entry) ?: return
        if (resultHub.cancel(requestId)) {
            _events.tryEmit(NavEvent.ResultDelivered(requestId, NavResult.Cancelled))
        }
    }

    private fun trimIfNeeded(stack: BackStack) {
        val max = config.maxBackStackSize
        if (max <= 1) return
        val snap = stack.snapshot.value
        if (snap.entries.size <= max) return
        val trimmed = listOf(snap.entries.first()) + snap.entries.takeLast(max - 1)
        stack.restore(snap.copy(entries = trimmed))
    }
}

data class NavigatorConfig(
    val deferDeepLinksUntilStarted: Boolean = true,
    val maxBackStackSize: Int = 64,
    val strictGraphIdOnRestore: Boolean = false,
)

fun ForgeNavigator(
    startRoute: Route,
    graphId: String = "root",
    children: Map<String, NavGraph> = emptyMap(),
    deepLinkParser: DeepLinkParser? = null,
    config: NavigatorConfig = NavigatorConfig(),
    routeCodec: RouteCodec? = null,
    savedState: SavedNavigatorState? = null,
    tabs: List<TabSpec> = emptyList(),
    startRouteProvider: StartRouteProvider? = null,
    interceptors: List<NavigationInterceptor> = emptyList(),
): ForgeNavigator {
    val graph = NavGraph(id = graphId, startRoute = startRoute, children = children)
    val navigator = DefaultForgeNavigator(
        rootGraph = graph,
        deepLinkParser = deepLinkParser ?: DeepLinkParser(graph),
        config = config,
        routeCodec = routeCodec,
        tabs = tabs,
        startRouteProvider = startRouteProvider,
        interceptors = interceptors,
    )
    if (savedState != null) {
        navigator.restoreState(savedState)
    }
    return navigator
}
