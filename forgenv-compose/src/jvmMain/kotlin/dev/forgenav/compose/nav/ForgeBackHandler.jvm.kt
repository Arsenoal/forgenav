package dev.forgenav.compose.nav

import androidx.compose.runtime.Composable

/**
 * Desktop has no system back by default. Wire Escape in the window if desired:
 * ```
 * onKeyEvent = {
 *   if (it.key == Key.Escape && navigator.canPop) {
 *     navigator.popBackStack(); true
 *   } else false
 * }
 * ```
 */
@Composable
actual fun ForgeBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // no-op
}
