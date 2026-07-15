package dev.forgenav.compose.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import dev.forgenav.navigation.ForgeNavigator

/**
 * Ambient navigator for descendants of [ForgeNavHost] / [TabNavHost].
 */
val LocalForgeNavigator = staticCompositionLocalOf<ForgeNavigator> {
    error("No ForgeNavigator provided. Wrap your UI in ForgeNavHost or provide LocalForgeNavigator.")
}

@Composable
fun ProvideForgeNavigator(
    navigator: ForgeNavigator,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalForgeNavigator provides navigator, content = content)
}
