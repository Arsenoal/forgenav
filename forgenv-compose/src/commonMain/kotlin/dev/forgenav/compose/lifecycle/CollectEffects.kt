package dev.forgenav.compose.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.forgenav.mvi.Effect
import dev.forgenav.mvi.ForgeViewModel
import dev.forgenav.mvi.Intent
import dev.forgenav.mvi.MviViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Collect one-shot [Effect]s from an [MviViewModel] without losing events on recomposition.
 */
@Composable
fun <E : Effect> CollectEffects(
    effects: kotlinx.coroutines.flow.Flow<E>,
    onEffect: suspend (E) -> Unit,
) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            onEffect(effect)
        }
    }
}

/**
 * Bind an [MviViewModel] state flow as Compose state.
 */
@Composable
fun <S> MviViewModel<S, *, *>.collectState(): S {
    val value by state.collectAsState()
    return value
}

@Composable
fun <S> StateFlow<S>.collectAsStateValue(): S {
    val value by collectAsState()
    return value
}

/**
 * Remember a [ForgeViewModel] and clear it when leaving composition.
 */
@Composable
fun <VM : ForgeViewModel> rememberForgeViewModel(
    key: Any? = null,
    factory: () -> VM,
): VM {
    val vm = remember(key) { factory() }
    DisposableEffect(vm) {
        onDispose { vm.clear() }
    }
    return vm
}

/**
 * Dispatch helper for cleaner call sites.
 */
@Composable
fun <I : Intent> rememberDispatcher(viewModel: MviViewModel<*, I, *>): (I) -> Unit {
    return remember(viewModel) { { intent: I -> viewModel.dispatch(intent) } }
}
