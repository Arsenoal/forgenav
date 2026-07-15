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
import dev.forgenav.navigation.NavigatorConfig
import dev.forgenav.navigation.Route
import dev.forgenav.navigation.RouteCodec

/**
 * [rememberForgeNavigator] that survives process death / configuration change via [rememberSaveable].
 *
 * Requires a [RouteCodec] with every navigable route family registered.
 *
 * ```
 * val codec = remember {
 *     RouteCodec().register("AppRoute", AppRoute.serializer()) { it is AppRoute }
 * }
 * val nav = rememberSaveableForgeNavigator(
 *     startRoute = AppRoute.Home,
 *     routeCodec = codec,
 * )
 * ```
 */
@Composable
fun rememberSaveableForgeNavigator(
    startRoute: Route,
    routeCodec: RouteCodec,
    graphId: String = "root",
    children: Map<String, NavGraph> = emptyMap(),
    deepLinkParser: DeepLinkParser? = null,
    config: NavigatorConfig = NavigatorConfig(),
    bindLifecycle: Boolean = true,
): ForgeNavigator {
    val saver = remember(startRoute, graphId, routeCodec, config) {
        forgeNavigatorSaver(
            startRoute = startRoute,
            graphId = graphId,
            children = children,
            routeCodec = routeCodec,
            deepLinkParser = deepLinkParser,
            config = config,
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
        )
    }
    if (bindLifecycle) {
        val lifecycle = remember { MutableLifecycle() }
        DisposableEffect(navigator) {
            navigator.bindTo(lifecycle)
            lifecycle.handleLifecycleEvent(LifecycleEvent.ON_CREATE)
            lifecycle.handleLifecycleEvent(LifecycleEvent.ON_START)
            lifecycle.handleLifecycleEvent(LifecycleEvent.ON_RESUME)
            onDispose {
                lifecycle.handleLifecycleEvent(LifecycleEvent.ON_PAUSE)
                lifecycle.handleLifecycleEvent(LifecycleEvent.ON_STOP)
                // Do not dispose navigator here — process death restore needs a live instance;
                // composition dispose on config change should not clear saved state.
            }
        }
    }
    return navigator
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
            )
        },
    )
}
