package dev.forgenav.compose.nav

import androidx.compose.runtime.Composable

/**
 * Platform back integration for [ForgeNavHost].
 *
 * - **Android:** [androidx.activity.compose.BackHandler] + predictive back (API 34+)
 * - **Desktop / iOS:** no-op by default (host apps can wire Escape / gestures)
 */
@Composable
expect fun ForgeBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
