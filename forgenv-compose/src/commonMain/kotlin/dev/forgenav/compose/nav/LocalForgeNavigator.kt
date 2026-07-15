package dev.forgenav.compose.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dev.forgenav.lifecycle.LifecycleEvent
import dev.forgenav.lifecycle.MutableLifecycle
import dev.forgenav.lifecycle.bindTo
import dev.forgenav.navigation.ForgeNavigator
import dev.forgenav.navigation.NavGraph
import dev.forgenav.navigation.Route

/**
 * Ambient navigator for descendants of [ForgeNavHost].
 */
val LocalForgeNavigator = staticCompositionLocalOf<ForgeNavigator> {
    error("No ForgeNavigator provided. Wrap your UI in ForgeNavHost or provide LocalForgeNavigator.")
}

/**
 * Creates and remembers a [ForgeNavigator] for the composition lifetime.
 *
 * Does **not** survive process death — prefer [rememberSaveableForgeNavigator] with a [dev.forgenav.navigation.RouteCodec].
 */
@Composable
fun rememberForgeNavigator(
    startRoute: Route,
    graphId: String = "root",
    children: Map<String, NavGraph> = emptyMap(),
    bindLifecycle: Boolean = true,
    routeCodec: dev.forgenav.navigation.RouteCodec? = null,
): ForgeNavigator {
    val navigator = remember(startRoute, graphId, routeCodec) {
        dev.forgenav.navigation.ForgeNavigator(
            startRoute = startRoute,
            graphId = graphId,
            children = children,
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
                lifecycle.handleLifecycleEvent(LifecycleEvent.ON_DESTROY)
            }
        }
    }
    return navigator
}

@Composable
fun ProvideForgeNavigator(
    navigator: ForgeNavigator,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalForgeNavigator provides navigator, content = content)
}
