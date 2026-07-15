package dev.forgenav.compose.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.forgenav.compose.nav.ForgeNavHostContent
import dev.forgenav.compose.nav.ProvideForgeNavigator
import dev.forgenav.navigation.BackStack
import dev.forgenav.navigation.BackStackSnapshot
import dev.forgenav.navigation.DefaultForgeNavigator
import dev.forgenav.navigation.ForgeNavigator
import dev.forgenav.navigation.NavEntry
import dev.forgenav.navigation.NavGraph
import dev.forgenav.navigation.Route
import dev.forgenav.navigation.RouteMetadata
import dev.forgenav.sync.DefaultSyncAwareState
import dev.forgenav.sync.NoOpSyncEngine
import dev.forgenav.sync.SyncAwareState
import dev.forgenav.sync.SyncStatus
import dev.forgenav.util.randomId

/**
 * Preview-friendly navigator that does not require platform lifecycle.
 */
@Composable
fun rememberPreviewNavigator(startRoute: Route): ForgeNavigator {
    return remember(startRoute) {
        DefaultForgeNavigator(NavGraph(id = "preview", startRoute = startRoute))
    }
}

/**
 * Build a fake [BackStackSnapshot] for @Preview composables.
 */
fun previewBackStack(vararg routes: Route, graphId: String = "preview"): BackStackSnapshot {
    val entries = routes.map { route ->
        NavEntry(
            id = randomId(),
            route = route,
            metadata = RouteMetadata(),
        )
    }
    return BackStackSnapshot(entries = entries, graphId = graphId)
}

/**
 * Host a single route for tooling previews.
 */
@Composable
fun PreviewForgeNavHost(
    route: Route,
    content: @Composable (NavEntry) -> Unit,
) {
    val navigator = rememberPreviewNavigator(route)
    ProvideForgeNavigator(navigator) {
        ForgeNavHostContent(
            snapshot = previewBackStack(route),
            onDismissModal = {},
            content = content,
        )
    }
}

/**
 * Sample [SyncAwareState] for previews.
 */
fun previewSyncState(
    status: SyncStatus = SyncStatus.Synced,
): SyncAwareState = DefaultSyncAwareState(syncStatus = status)

/**
 * No-op sync engine for preview composition locals / ViewModels.
 */
val PreviewSyncEngine = NoOpSyncEngine
