package dev.forgenav.compose.nav

import androidx.compose.runtime.Composable

/**
 * iOS edge-swipe is typically handled by UIKit navigation; CMP hosts can call [onBack]
 * from a custom swipe gesture if needed.
 */
@Composable
actual fun ForgeBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // no-op
}
