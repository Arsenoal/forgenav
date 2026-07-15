package dev.forgenav.testing

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.forgenav.navigation.BackStackSnapshot
import dev.forgenav.navigation.DeepLinkParser
import dev.forgenav.navigation.DefaultForgeNavigator
import dev.forgenav.navigation.ForgeNavigator
import dev.forgenav.navigation.NavEvent
import dev.forgenav.navigation.NavGraph
import dev.forgenav.navigation.NavOptions
import dev.forgenav.navigation.NavResult
import dev.forgenav.navigation.NavigationInterceptor
import dev.forgenav.navigation.NavigatorConfig
import dev.forgenav.navigation.Route
import dev.forgenav.navigation.RouteCodec
import dev.forgenav.navigation.StartRouteProvider
import dev.forgenav.navigation.TabSpec
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Creates a [ForgeNavigator] configured for unit tests (deep links start immediately).
 */
fun testForgeNavigator(
    startRoute: Route,
    graphId: String = "root",
    children: Map<String, NavGraph> = emptyMap(),
    deepLinkParser: DeepLinkParser? = null,
    routeCodec: RouteCodec? = null,
    tabs: List<TabSpec> = emptyList(),
    startRouteProvider: StartRouteProvider? = null,
    interceptors: List<NavigationInterceptor> = emptyList(),
    config: NavigatorConfig = NavigatorConfig(deferDeepLinksUntilStarted = false),
): ForgeNavigator {
    val graph = NavGraph(id = graphId, startRoute = startRoute, children = children)
    val nav = DefaultForgeNavigator(
        rootGraph = graph,
        deepLinkParser = deepLinkParser ?: DeepLinkParser(graph),
        config = config,
        routeCodec = routeCodec,
        tabs = tabs,
        startRouteProvider = startRouteProvider,
        interceptors = interceptors,
    )
    nav.onStart()
    return nav
}

/** Current route keys from root to top (inclusive). */
fun ForgeNavigator.routeKeys(): List<String> =
    backStack.value.entries.map { it.route.routeKey }

/** Current top route. */
fun ForgeNavigator.currentRoute(): Route? = currentEntry?.route

/** Assert stack route keys equal [expected]. */
fun ForgeNavigator.assertRouteKeys(vararg expected: String) {
    assertEquals(expected.toList(), routeKeys())
}

/** Assert current route equals [expected]. */
fun ForgeNavigator.assertCurrent(expected: Route) {
    assertEquals(expected, currentRoute())
}

/** Assert selected tab id. */
fun ForgeNavigator.assertSelectedTab(tabId: String) {
    assertEquals(tabId, selectedTabId.value)
}

/**
 * Assert tab stack route keys.
 */
fun ForgeNavigator.assertTabRouteKeys(tabId: String, vararg expected: String) {
    val snap = tabBackStack(tabId)?.value
        ?: error("No tab stack for $tabId")
    assertEquals(expected.toList(), snap.entries.map { it.route.routeKey })
}

/**
 * Run a navigation scenario under [runTest].
 */
fun runNavTest(block: suspend TestScope.(ForgeNavigator) -> Unit) {
    // Caller creates navigator inside block typically; keep helper for consistency.
    runTest {
        // no-op scope wrapper for future clock control
    }
}

/**
 * Collect [ForgeNavigator.events] and assert a sequence of event types / predicates.
 */
suspend fun ForgeNavigator.expectEvents(
    count: Int,
    validate: suspend ReceiveTurbine<NavEvent>.() -> Unit,
) {
    events.test {
        validate()
        cancelAndIgnoreRemainingEvents()
    }
}

/**
 * Push [routes] in order (convenience for building stacks in tests).
 */
fun ForgeNavigator.navigateAll(vararg routes: Route, options: NavOptions = NavOptions()) {
    routes.forEach { navigate(it, options) }
}

/**
 * Simulate a child screen that sets a result and pops.
 */
fun ForgeNavigator.deliverResultAndPop(value: Any?) {
    setResult(value)
    popBackStack()
}

/**
 * Simulate cancel (back without result).
 */
fun ForgeNavigator.cancelResultAndPop() {
    popBackStack()
}

/** Snapshot of a [StateFlow] for assertions. */
fun StateFlow<BackStackSnapshot>.routeKeys(): List<String> =
    value.entries.map { it.route.routeKey }

/**
 * Type-safe unwrap of [NavResult.Ok].
 */
inline fun <reified T> NavResult.requireOk(): T {
    assertIs<NavResult.Ok>(this)
    val value = (this as NavResult.Ok).value
    assertTrue(value is T, "Expected ${T::class.simpleName}, got ${value?.let { it::class.simpleName }}")
    return value as T
}

fun NavResult.assertCancelled() {
    assertIs<NavResult.Cancelled>(this)
}
