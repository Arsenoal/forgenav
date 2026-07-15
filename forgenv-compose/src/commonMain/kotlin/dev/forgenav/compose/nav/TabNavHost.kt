package dev.forgenav.compose.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.forgenav.navigation.ForgeNavigator
import dev.forgenav.navigation.NavEntry
import dev.forgenav.navigation.TabSpec

/**
 * Bottom-nav / tab scaffold with independent per-tab back stacks.
 *
 * Requires a [ForgeNavigator] created with non-empty [ForgeNavigator.tabs].
 *
 * ```
 * val nav = rememberForgeNavigator(
 *     startRoute = Home,
 *     tabs = listOf(
 *         TabSpec("home", Home, "Home"),
 *         TabSpec("settings", Settings, "Settings"),
 *     ),
 * )
 * TabNavHost(nav, icons = mapOf("home" to Icons.Home, "settings" to Icons.Settings)) { entry ->
 *     when (entry.route) { ... }
 * }
 * ```
 */
@Composable
fun TabNavHost(
    navigator: ForgeNavigator,
    modifier: Modifier = Modifier,
    transitionSpec: NavTransitionSpec = NavTransitions.Default,
    enableSystemBack: Boolean = true,
    icons: Map<String, ImageVector> = emptyMap(),
    alwaysShowLabel: Boolean = true,
    content: @Composable (NavEntry) -> Unit,
) {
    require(navigator.tabs.isNotEmpty()) {
        "TabNavHost requires ForgeNavigator created with tabs = listOf(TabSpec(...))"
    }

    CompositionLocalProvider(LocalForgeNavigator provides navigator) {
        val selectedTabId by navigator.selectedTabId.collectAsState()
        val activeTabId = selectedTabId ?: navigator.tabs.first().id
        val activeStack by (navigator.tabBackStack(activeTabId)
            ?: navigator.backStack).collectAsState()

        if (enableSystemBack) {
            ForgeBackHandler(enabled = activeStack.canPop) {
                navigator.popBackStack()
            }
        }

        Column(modifier = modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Keep each tab's composition (and saveable state) alive when switching.
                navigator.tabs.forEach { tab ->
                    key(tab.id) {
                        val visible = tab.id == activeTabId
                        if (visible) {
                            val snap by (navigator.tabBackStack(tab.id)
                                ?: navigator.backStack).collectAsState()
                            ForgeNavHostContent(
                                snapshot = snap,
                                onDismissModal = { navigator.popBackStack() },
                                modifier = Modifier.fillMaxSize(),
                                transitionSpec = transitionSpec,
                                content = content,
                            )
                        }
                    }
                }
            }

            NavigationBar {
                navigator.tabs.forEach { tab ->
                    val selected = tab.id == activeTabId
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigator.selectTab(tab.id) },
                        icon = {
                            val icon = icons[tab.id] ?: icons[tab.iconKey.orEmpty()]
                            if (icon != null) {
                                Icon(icon, contentDescription = tab.label)
                            } else {
                                Text(tab.label.take(1))
                            }
                        },
                        label = if (alwaysShowLabel || selected) {
                            { Text(tab.label) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/**
 * Low-level tab scaffold: you supply chrome; we switch stacks.
 */
@Composable
fun TabStackHost(
    navigator: ForgeNavigator,
    modifier: Modifier = Modifier,
    transitionSpec: NavTransitionSpec = NavTransitions.Default,
    content: @Composable (tab: TabSpec, entry: NavEntry) -> Unit,
) {
    require(navigator.tabs.isNotEmpty())
    val selectedTabId by navigator.selectedTabId.collectAsState()
    val tab = navigator.tabs.firstOrNull { it.id == selectedTabId }
        ?: navigator.tabs.first()
    val snap by (navigator.tabBackStack(tab.id) ?: navigator.backStack).collectAsState()
    val entry = snap.current ?: return

    Box(modifier = modifier.fillMaxSize()) {
        content(tab, entry)
    }
}
