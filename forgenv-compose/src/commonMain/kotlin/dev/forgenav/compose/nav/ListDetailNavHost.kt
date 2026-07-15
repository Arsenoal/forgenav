package dev.forgenav.compose.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.forgenav.navigation.ForgeNavigator
import dev.forgenav.navigation.NavEntry
import dev.forgenav.navigation.NavOptions
import dev.forgenav.navigation.PresentationStyle
import dev.forgenav.navigation.Route

/**
 * Adaptive list–detail host (Nav3 multi-pane analog).
 *
 * - **Compact** (width < [breakpoint]): single stack — list and detail push/pop as usual.
 * - **Expanded**: list pane stays visible; detail routes render in the right pane.
 *
 * Detail detection uses [isDetailRoute].
 *
 * ```
 * ListDetailNavHost(
 *     navigator = nav,
 *     isDetailRoute = { it is AppRoute.Detail },
 *     listContent = { entry -> TaskList(...) },
 *     detailContent = { entry -> TaskDetail(...) },
 *     emptyDetail = { Text("Select an item") },
 * )
 * ```
 */
@Composable
fun ListDetailNavHost(
    navigator: ForgeNavigator,
    modifier: Modifier = Modifier,
    breakpoint: Dp = 600.dp,
    listPaneWidth: Dp = 360.dp,
    isDetailRoute: (Route) -> Boolean = { false },
    transitionSpec: NavTransitionSpec = NavTransitions.Default,
    enableSystemBack: Boolean = true,
    emptyDetail: @Composable () -> Unit = {},
    listContent: @Composable (NavEntry) -> Unit,
    detailContent: @Composable (NavEntry) -> Unit,
) {
    CompositionLocalProvider(LocalForgeNavigator provides navigator) {
        val snapshot by navigator.backStack.collectAsState()
        val entries = snapshot.entries
        if (entries.isEmpty()) return@CompositionLocalProvider

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val dualPane = maxWidth >= breakpoint

            val listEntry = entries.firstOrNull {
                !isDetailRoute(it.route) &&
                    it.metadata.presentation == PresentationStyle.Screen
            } ?: entries.first()

            val detailEntry = entries.lastOrNull {
                isDetailRoute(it.route) &&
                    it.metadata.presentation == PresentationStyle.Screen
            }

            if (enableSystemBack) {
                val canPop = if (dualPane) {
                    detailEntry != null && snapshot.canPop
                } else {
                    snapshot.canPop
                }
                ForgeBackHandler(enabled = canPop) {
                    navigator.popBackStack()
                }
            }

            if (dualPane) {
                Row(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .width(listPaneWidth)
                            .fillMaxHeight(),
                    ) {
                        listContent(listEntry)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (detailEntry != null) {
                            detailContent(detailEntry)
                        } else {
                            emptyDetail()
                        }
                    }
                }
            } else {
                ForgeNavHostContent(
                    snapshot = snapshot,
                    onDismissModal = { navigator.popBackStack() },
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = transitionSpec,
                ) { entry ->
                    if (isDetailRoute(entry.route)) {
                        detailContent(entry)
                    } else {
                        listContent(entry)
                    }
                }
            }
        }
    }
}

/**
 * Navigate to a detail route, optionally popping back to a list route first.
 */
fun ForgeNavigator.navigateToDetail(
    route: Route,
    listRouteKey: String? = null,
    singleTop: Boolean = true,
) {
    navigate(
        route,
        NavOptions(
            singleTop = singleTop,
            launchSingleTop = singleTop,
            popUpToRouteKey = listRouteKey,
            popUpToInclusive = false,
        ),
    )
}
