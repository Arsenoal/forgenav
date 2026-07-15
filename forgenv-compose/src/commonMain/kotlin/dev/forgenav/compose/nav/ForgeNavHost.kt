package dev.forgenav.compose.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.forgenav.navigation.BackStackSnapshot
import dev.forgenav.navigation.ForgeNavigator
import dev.forgenav.navigation.NavEntry
import dev.forgenav.navigation.PresentationStyle
import dev.forgenav.navigation.Route

/**
 * Compose Multiplatform navigation host.
 *
 * Observes [ForgeNavigator.backStack] and renders destinations via [content].
 * Modal destinations ([PresentationStyle.Dialog] / [PresentationStyle.BottomSheet]) are
 * layered over the last full-screen entry.
 *
 * Features:
 * - **Transitions** via [transitionSpec] (default horizontal slide)
 * - **System / predictive back** via [enableSystemBack] → [ForgeBackHandler]
 *
 * Example:
 * ```
 * ForgeNavHost(navigator) { entry ->
 *     when (val route = entry.route) {
 *         is AppRoute.Home -> HomeScreen()
 *         is AppRoute.Detail -> DetailScreen(route.id)
 *     }
 * }
 * ```
 */
@Composable
fun ForgeNavHost(
    navigator: ForgeNavigator,
    modifier: Modifier = Modifier,
    transitionSpec: NavTransitionSpec = NavTransitions.Default,
    enableSystemBack: Boolean = true,
    content: @Composable (NavEntry) -> Unit,
) {
    CompositionLocalProvider(LocalForgeNavigator provides navigator) {
        val snapshot by navigator.backStack.collectAsState()
        val canPop = snapshot.canPop

        if (enableSystemBack) {
            ForgeBackHandler(enabled = canPop) {
                navigator.popBackStack()
            }
        }

        ForgeNavHostContent(
            snapshot = snapshot,
            onDismissModal = { navigator.popBackStack() },
            modifier = modifier,
            transitionSpec = transitionSpec,
            content = content,
        )
    }
}

/**
 * Stateless host that renders a [BackStackSnapshot] — useful for previews and tests.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ForgeNavHostContent(
    snapshot: BackStackSnapshot,
    onDismissModal: () -> Unit,
    modifier: Modifier = Modifier,
    transitionSpec: NavTransitionSpec = NavTransitions.Default,
    content: @Composable (NavEntry) -> Unit,
) {
    val entries = snapshot.entries
    if (entries.isEmpty()) return

    val lastScreenIndex = entries.indexOfLast {
        it.metadata.presentation == PresentationStyle.Screen
    }.coerceAtLeast(0)

    val screenEntry = entries[lastScreenIndex]
    val modalEntries = entries.drop(lastScreenIndex + 1)

    // Detect pop vs push for transition direction
    var previousDepth by remember { mutableIntStateOf(entries.size) }
    var previousScreenId by remember { mutableStateOf(screenEntry.id) }
    var isPop by remember { mutableStateOf(false) }

    LaunchedEffect(entries.size, screenEntry.id) {
        isPop = when {
            entries.size < previousDepth -> true
            entries.size > previousDepth -> false
            // replace top: treat as forward
            screenEntry.id != previousScreenId -> false
            else -> isPop
        }
        previousDepth = entries.size
        previousScreenId = screenEntry.id
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screenEntry,
            transitionSpec = {
                with(transitionSpec) {
                    transform(isPop)
                }
            },
            contentKey = { entry -> entry.id },
            label = "ForgeNavHost",
            modifier = Modifier.fillMaxSize(),
        ) { entry ->
            content(entry)
        }

        modalEntries.forEach { entry ->
            key(entry.id) {
                when (entry.metadata.presentation) {
                    PresentationStyle.Dialog -> {
                        AlertDialog(
                            onDismissRequest = onDismissModal,
                            confirmButton = {
                                TextButton(onClick = onDismissModal) {
                                    Text("Close")
                                }
                            },
                            text = {
                                content(entry)
                            },
                        )
                    }
                    PresentationStyle.BottomSheet -> {
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ModalBottomSheet(
                            onDismissRequest = onDismissModal,
                            sheetState = sheetState,
                        ) {
                            content(entry)
                        }
                    }
                    PresentationStyle.Screen -> {
                        // Full-screen layered above the animated base (rare)
                        content(entry)
                    }
                }
            }
        }
    }
}

/**
 * Nested navigation host for a child graph (tabs, feature modules).
 */
@Composable
fun NestedForgeNavHost(
    navigator: ForgeNavigator,
    graphId: String,
    modifier: Modifier = Modifier,
    transitionSpec: NavTransitionSpec = NavTransitions.Default,
    enableSystemBack: Boolean = false,
    content: @Composable (NavEntry) -> Unit,
) {
    val nestedFlow = navigator.nestedBackStack(graphId)
    if (nestedFlow == null) {
        return
    }
    val snapshot by nestedFlow.collectAsState()

    if (enableSystemBack && snapshot.canPop) {
        ForgeBackHandler(enabled = true) {
            navigator.popNested(graphId)
        }
    }

    ForgeNavHostContent(
        snapshot = snapshot,
        onDismissModal = { navigator.popNested(graphId) },
        modifier = modifier,
        transitionSpec = transitionSpec,
        content = content,
    )
}

/**
 * Typed helper when routes are a sealed hierarchy.
 */
@Composable
inline fun <reified R : Route> ForgeNavHostTyped(
    navigator: ForgeNavigator,
    modifier: Modifier = Modifier,
    transitionSpec: NavTransitionSpec = NavTransitions.Default,
    enableSystemBack: Boolean = true,
    crossinline content: @Composable (route: R, entry: NavEntry) -> Unit,
) {
    ForgeNavHost(
        navigator = navigator,
        modifier = modifier,
        transitionSpec = transitionSpec,
        enableSystemBack = enableSystemBack,
    ) { entry ->
        val route = entry.route
        if (route is R) {
            content(route, entry)
        }
    }
}
