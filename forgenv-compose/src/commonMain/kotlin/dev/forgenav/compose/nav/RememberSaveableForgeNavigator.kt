package dev.forgenav.compose.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import dev.forgenav.lifecycle.LifecycleEvent
import dev.forgenav.lifecycle.MutableLifecycle
import dev.forgenav.lifecycle.bindTo
import dev.forgenav.navigation.DeepLinkParser
import dev.forgenav.navigation.ForgeNavigator
import dev.forgenav.navigation.NavGraph
import dev.forgenav.navigation.NavStateSerializer
import dev.forgenav.navigation.NavigationInterceptor
import dev.forgenav.navigation.NavigatorConfig
import dev.forgenav.navigation.Route
import dev.forgenav.navigation.RouteCodec
import dev.forgenav.navigation.StartRouteProvider
import dev.forgenav.navigation.TabSpec

/**
 * Creates and remembers a [ForgeNavigator] for the composition lifetime.
 */
@Composable
fun rememberForgeNavigator(
    startRoute: Route,
    graphId: String = "root",
    children: Map<String, NavGraph> = emptyMap(),
    deepLinkParser: DeepLinkParser? = null,
    config: NavigatorConfig = NavigatorConfig(),
    routeCodec: RouteCodec? = null,
    tabs: List<TabSpec> = emptyList(),
    startRouteProvider: StartRouteProvider? = null,
    interceptors: List<NavigationInterceptor> = emptyList(),
    bindLifecycle: Boolean = true,
): ForgeNavigator {
    val navigator = remember(
        startRoute,
        graphId,
        tabs,
        config,
        routeCodec,
    ) {
        ForgeNavigator(
            startRoute = startRoute,
            graphId = graphId,
            children = children,
            deepLinkParser = deepLinkParser,
            config = config,
            routeCodec = routeCodec,
            tabs = tabs,
            startRouteProvider = startRouteProvider,
            interceptors = interceptors,
        )
    }
    if (bindLifecycle) {
        bindNavigatorLifecycle(navigator)
    }
    return navigator
}

/**
 * [rememberForgeNavigator] that survives process death / configuration change via [rememberSaveable].
 *
 * Requires a [RouteCodec] with every navigable route family registered.
 */
@Composable
fun rememberSaveableForgeNavigator(
    startRoute: Route,
    routeCodec: RouteCodec,
    graphId: String = "root",
    children: Map<String, NavGraph> = emptyMap(),
    deepLinkParser: DeepLinkParser? = null,
    config: NavigatorConfig = NavigatorConfig(),
    tabs: List<TabSpec> = emptyList(),
    startRouteProvider: StartRouteProvider? = null,
    interceptors: List<NavigationInterceptor> = emptyList(),
    bindLifecycle: Boolean = true,
): ForgeNavigator {
    val saver = remember(startRoute, graphId, routeCodec, config, tabs) {
        forgeNavigatorSaver(
            startRoute = startRoute,
            graphId = graphId,
            children = children,
            routeCodec = routeCodec,
            deepLinkParser = deepLinkParser,
            config = config,
            tabs = tabs,
            startRouteProvider = startRouteProvider,
            interceptors = interceptors,
        )
    }
    val navigator = rememberSaveable(
        startRoute,
        graphId,
        saver = saver,
    ) {
        ForgeNavigator(
            startRoute = startRoute,
            graphId = graphId,
            children = children,
            deepLinkParser = deepLinkParser,
            config = config,
            routeCodec = routeCodec,
            tabs = tabs,
            startRouteProvider = startRouteProvider,
            interceptors = interceptors,
        )
    }
    if (bindLifecycle) {
        bindNavigatorLifecycle(navigator)
    }
    return navigator
}

@Composable
private fun bindNavigatorLifecycle(navigator: ForgeNavigator) {
    val lifecycle = remember { MutableLifecycle() }
    DisposableEffect(navigator) {
        navigator.bindTo(lifecycle)
        lifecycle.handleLifecycleEvent(LifecycleEvent.ON_CREATE)
        lifecycle.handleLifecycleEvent(LifecycleEvent.ON_START)
        lifecycle.handleLifecycleEvent(LifecycleEvent.ON_RESUME)
        onDispose {
            lifecycle.handleLifecycleEvent(LifecycleEvent.ON_PAUSE)
            lifecycle.handleLifecycleEvent(LifecycleEvent.ON_STOP)
        }
    }
}

/**
 * Saver that persists [ForgeNavigator] as a JSON string inside the Compose saved-state registry.
 */
fun forgeNavigatorSaver(
    startRoute: Route,
    graphId: String = "root",
    children: Map<String, NavGraph> = emptyMap(),
    routeCodec: RouteCodec,
    deepLinkParser: DeepLinkParser? = null,
    config: NavigatorConfig = NavigatorConfig(),
    tabs: List<TabSpec> = emptyList(),
    startRouteProvider: StartRouteProvider? = null,
    interceptors: List<NavigationInterceptor> = emptyList(),
): Saver<ForgeNavigator, String> {
    val serializer = NavStateSerializer(routeCodec)
    return Saver(
        save = { navigator ->
            val state = navigator.saveState() ?: return@Saver null
            serializer.encodeToString(state)
        },
        restore = { encoded ->
            val state = runCatching { serializer.decodeFromString(encoded) }.getOrNull()
            ForgeNavigator(
                startRoute = startRoute,
                graphId = graphId,
                children = children,
                deepLinkParser = deepLinkParser,
                config = config,
                routeCodec = routeCodec,
                savedState = state,
                tabs = tabs,
                startRouteProvider = startRouteProvider,
                interceptors = interceptors,
            )
        },
    )
}
