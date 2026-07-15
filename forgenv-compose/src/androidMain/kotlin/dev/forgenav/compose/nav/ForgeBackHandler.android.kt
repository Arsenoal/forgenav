package dev.forgenav.compose.nav

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException

/**
 * System back + predictive back gesture (Android 13+ / OnBackInvokedCallback).
 *
 * When the gesture or back button completes, [onBack] runs
 * (typically [dev.forgenav.navigation.ForgeNavigator.popBackStack]).
 * If the predictive gesture is cancelled mid-swipe, the stack is unchanged.
 *
 * Uses a single [PredictiveBackHandler] (not also [androidx.activity.compose.BackHandler])
 * so we never double-pop.
 */
@Composable
actual fun ForgeBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { progress ->
        try {
            progress.collect {
                // Future: expose progress for shared-element / parallax polish
            }
            onBack()
        } catch (_: CancellationException) {
            // Gesture cancelled
        }
    }
}
