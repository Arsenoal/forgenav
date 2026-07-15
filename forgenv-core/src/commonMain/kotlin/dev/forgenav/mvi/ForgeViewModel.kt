package dev.forgenav.mvi

import dev.forgenav.sync.ConflictDecision
import dev.forgenav.sync.ConflictInfo
import dev.forgenav.sync.OptimisticUpdate
import dev.forgenav.sync.OptimisticUpdateTracker
import dev.forgenav.sync.PendingOperation
import dev.forgenav.sync.SyncAwareState
import dev.forgenav.sync.SyncFacade
import dev.forgenav.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.forgenav.util.randomId
import kotlin.coroutines.CoroutineContext

/**
 * Lightweight multiplatform ViewModel base.
 *
 * Uses an explicit [scope] instead of AndroidX ViewModel so the same class works on
 * Android, iOS, Desktop, and Web. Hosts should cancel [scope] when the screen leaves
 * the composition (see Compose `DisposableEffect` helpers).
 */
abstract class ForgeViewModel(
    parentContext: CoroutineContext = Dispatchers.Default,
) {
    private val job = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(parentContext + job)

    private val _isCleared = MutableStateFlow(false)
    val isCleared: StateFlow<Boolean> = _isCleared.asStateFlow()

    protected open fun onCleared() = Unit

    fun clear() {
        if (_isCleared.value) return
        _isCleared.value = true
        onCleared()
        scope.cancel()
    }
}

/**
 * Marker for user / system intents handled by [MviViewModel].
 */
interface Intent

/**
 * One-shot UI side effects (navigation, snackbars, dialogs).
 */
interface Effect

/**
 * MVI ViewModel with built-in offline-first hooks:
 * - Optimistic updates + rollback
 * - Sync status observation
 * - Pending operations + conflicts
 *
 * Subclasses implement [reduce] for pure state transitions and optionally override
 * [handleSideEffect] for async work.
 */
abstract class MviViewModel<S, I : Intent, E : Effect>(
    initialState: S,
    parentContext: CoroutineContext = Dispatchers.Default,
    private val syncFacade: SyncFacade? = null,
) : ForgeViewModel(parentContext) {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    private val intentFlow = MutableSharedFlow<I>(extraBufferCapacity = 64)
    private val optimisticTracker = OptimisticUpdateTracker<S>()
    private var syncJobs: List<Job> = emptyList()

    val currentState: S get() = _state.value
    val pendingOptimisticCount: Int get() = optimisticTracker.size

    init {
        scope.launch {
            intentFlow.collect { intent ->
                processIntent(intent)
            }
        }
        bindSyncIfPresent()
    }

    /**
     * Pure reducer. Return the new state (or the same instance if unchanged).
     */
    protected abstract fun reduce(state: S, intent: I): S

    /**
     * Optional async side effects after a reduce. Default is no-op.
     */
    protected open suspend fun handleSideEffect(intent: I, previous: S, current: S) = Unit

    /**
     * Dispatch an intent from the UI thread / Compose.
     */
    fun dispatch(intent: I) {
        intentFlow.tryEmit(intent)
    }

    /**
     * Emit a one-shot [Effect] to the UI.
     */
    protected fun emitEffect(effect: E) {
        _effects.trySend(effect)
    }

    /**
     * Imperative state update (e.g. from Flow collectors). Prefer [dispatch] for user actions.
     */
    protected fun setState(reducer: (S) -> S) {
        _state.update(reducer)
    }

    protected fun updateState(newState: S) {
        _state.value = newState
    }

    /**
     * Apply an optimistic mutation: immediately update UI state, track previous for rollback.
     *
     * @return optimistic update id
     */
    protected fun applyOptimistic(
        transform: (S) -> S,
        operationId: String? = null,
    ): String {
        val id = randomId()
        val previous = _state.value
        val next = transform(previous)
        optimisticTracker.track(
            OptimisticUpdate(
                id = id,
                previousState = previous,
                optimisticState = next,
                operationId = operationId,
                createdAtMillis = currentTimeMillis(),
            ),
        )
        _state.value = next
        return id
    }

    /**
     * Confirm an optimistic update after the outbox / server acknowledges it.
     */
    protected fun commitOptimistic(id: String) {
        optimisticTracker.commit(id)
    }

    /**
     * Roll back a single optimistic update (conflict, validation error, network policy).
     */
    protected fun rollbackOptimistic(id: String) {
        val update = optimisticTracker.rollback(id) ?: return
        _state.value = update.previousState
    }

    /**
     * Roll back every in-flight optimistic update.
     */
    protected fun rollbackAllOptimistic() {
        val all = optimisticTracker.rollbackAll()
        val oldest = all.firstOrNull() ?: return
        _state.value = oldest.previousState
    }

    private suspend fun processIntent(intent: I) {
        val previous = _state.value
        val next = reduce(previous, intent)
        if (next != previous) {
            _state.value = next
        }
        handleSideEffect(intent, previous, next)
    }

    private fun bindSyncIfPresent() {
        val facade = syncFacade ?: return
        val jobs = mutableListOf<Job>()
        jobs += scope.launch {
            facade.status.collect { status ->
                onSyncStatusChanged(status)
            }
        }
        jobs += scope.launch {
            facade.pendingOperations.collect { ops ->
                onPendingOperationsChanged(ops)
            }
        }
        jobs += scope.launch {
            facade.conflicts.collect { conflicts ->
                onConflictsChanged(conflicts)
            }
        }
        syncJobs = jobs
    }

    /**
     * Override to map [SyncStatus] into your [S] when it implements [SyncAwareState].
     */
    protected open fun onSyncStatusChanged(status: SyncStatus) {
        val s = _state.value
        if (s is SyncAwareState) {
            // Subclasses with data classes should override for typed copy.
        }
    }

    protected open fun onPendingOperationsChanged(operations: List<PendingOperation>) = Unit

    protected open fun onConflictsChanged(conflicts: List<ConflictInfo>) {
        if (conflicts.isNotEmpty()) {
            // Default: rollback optimistics tied to conflicting entities when status is Conflict
            val status = syncFacade?.status?.value
            if (status is SyncStatus.Conflict) {
                // Leave full rollback policy to subclasses — automatic rollback can surprise users.
            }
        }
    }

    /**
     * Resolve a conflict via the bound [SyncFacade] and optionally roll back optimistic UI.
     */
    protected suspend fun resolveConflict(
        conflict: ConflictInfo,
        decision: ConflictDecision,
        rollbackOptimisticOnRemote: Boolean = true,
    ) {
        syncFacade?.resolveConflict(conflict, decision)
        if (rollbackOptimisticOnRemote && decision is ConflictDecision.AcceptRemote) {
            rollbackAllOptimistic()
        }
    }

    override fun onCleared() {
        syncJobs.forEach { it.cancel() }
        super.onCleared()
    }
}

/**
 * Multiplatform epoch millis — expect/actual would be ideal; use kotlinx-datetime free approach.
 */
internal expect fun currentTimeMillis(): Long
