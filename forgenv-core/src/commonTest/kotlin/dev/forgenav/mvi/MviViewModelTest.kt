package dev.forgenav.mvi

import dev.forgenav.sync.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class CounterState(val count: Int = 0, val syncStatus: SyncStatus = SyncStatus.Synced)

private sealed interface CounterIntent : Intent {
    data object Increment : CounterIntent
    data object Decrement : CounterIntent
    data class Set(val value: Int) : CounterIntent
}

private sealed interface CounterEffect : Effect {
    data class MaxReached(val value: Int) : CounterEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {

    private class CounterVm(
        parentContext: kotlin.coroutines.CoroutineContext,
    ) : MviViewModel<CounterState, CounterIntent, CounterEffect>(
        initialState = CounterState(),
        parentContext = parentContext,
    ) {
        override fun reduce(state: CounterState, intent: CounterIntent): CounterState = when (intent) {
            CounterIntent.Increment -> state.copy(count = state.count + 1)
            CounterIntent.Decrement -> state.copy(count = state.count - 1)
            is CounterIntent.Set -> state.copy(count = intent.value)
        }

        override suspend fun handleSideEffect(
            intent: CounterIntent,
            previous: CounterState,
            current: CounterState,
        ) {
            if (current.count >= 10) {
                emitEffect(CounterEffect.MaxReached(current.count))
            }
        }

        fun optimisticAdd(delta: Int): String =
            applyOptimistic(transform = { state -> state.copy(count = state.count + delta) })

        fun commit(id: String) = commitOptimistic(id)
        fun rollback(id: String) = rollbackOptimistic(id)
    }

    @Test
    fun reduceIntents() = runTest(UnconfinedTestDispatcher()) {
        val vm = CounterVm(UnconfinedTestDispatcher())
        vm.dispatch(CounterIntent.Increment)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.count)

        vm.dispatch(CounterIntent.Set(5))
        advanceUntilIdle()
        assertEquals(5, vm.state.value.count)

        vm.clear()
        assertTrue(vm.isCleared.value)
    }

    @Test
    fun optimisticRollback() = runTest(UnconfinedTestDispatcher()) {
        val vm = CounterVm(UnconfinedTestDispatcher())
        vm.dispatch(CounterIntent.Set(1))
        advanceUntilIdle()

        val id = vm.optimisticAdd(10)
        assertEquals(11, vm.state.value.count)
        assertEquals(1, vm.pendingOptimisticCount)

        vm.rollback(id)
        assertEquals(1, vm.state.value.count)
        assertEquals(0, vm.pendingOptimisticCount)
        vm.clear()
    }
}
