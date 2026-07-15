package dev.forgenav.compose.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import dev.forgenav.navigation.NavEntry

/**
 * Spec for screen transitions inside [ForgeNavHost].
 *
 * [isPop] is true when the backstack shrank (system back / popBackStack).
 */
fun interface NavTransitionSpec {
    fun AnimatedContentTransitionScope<NavEntry>.transform(isPop: Boolean): ContentTransform
}

/**
 * Built-in transition presets for [ForgeNavHost].
 */
object NavTransitions {
    private const val DurationMs = 280

    /** No animation — instant swap. */
    val None: NavTransitionSpec = NavTransitionSpec {
        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    }

    /** Cross-fade (good for tabs / large screens). */
    val Fade: NavTransitionSpec = NavTransitionSpec {
        fadeIn(tween(DurationMs)) togetherWith fadeOut(tween(DurationMs))
    }

    /**
     * Horizontal slide — default mobile push/pop feel.
     * Forward: new enters from end; pop: current exits toward end.
     */
    val SlideHorizontal: NavTransitionSpec = NavTransitionSpec { isPop ->
        if (isPop) {
            (slideInHorizontally(tween(DurationMs)) { width -> -width / 4 } + fadeIn(tween(DurationMs))) togetherWith
                (slideOutHorizontally(tween(DurationMs)) { width -> width / 3 } + fadeOut(tween(DurationMs)))
        } else {
            (slideInHorizontally(tween(DurationMs)) { width -> width / 3 } + fadeIn(tween(DurationMs))) togetherWith
                (slideOutHorizontally(tween(DurationMs)) { width -> -width / 4 } + fadeOut(tween(DurationMs)))
        }
    }

    /** Vertical slide — useful for modal-style full screens. */
    val SlideVertical: NavTransitionSpec = NavTransitionSpec { isPop ->
        if (isPop) {
            (slideInVertically(tween(DurationMs)) { height -> -height / 6 } + fadeIn(tween(DurationMs))) togetherWith
                (slideOutVertically(tween(DurationMs)) { height -> height / 4 } + fadeOut(tween(DurationMs)))
        } else {
            (slideInVertically(tween(DurationMs)) { height -> height / 4 } + fadeIn(tween(DurationMs))) togetherWith
                (slideOutVertically(tween(DurationMs)) { height -> -height / 6 } + fadeOut(tween(DurationMs)))
        }
    }

    val Default: NavTransitionSpec = SlideHorizontal
}
