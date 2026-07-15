package dev.forgenav.sample

import dev.forgenav.mvi.Effect
import dev.forgenav.mvi.Intent
import dev.forgenav.mvi.MviViewModel
import dev.forgenav.sync.ConflictDecision
import dev.forgenav.sync.ConflictInfo
import dev.forgenav.sync.PendingOperation
import dev.forgenav.sync.SyncAwareState
import dev.forgenav.sync.SyncStatus
import dev.forgenav.syncforge.ForgeNavSync
import dev.forgenav.syncforge.loop.LocalSyncForgeLoop
import dev.forgenav.syncforge.loop.TaskEntity
import dev.syncforge.model.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class Task(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val pendingSync: Boolean = false,
    val inConflict: Boolean = false,
)

data class TaskListState(
    val tasks: List<Task> = emptyList(),
    override val syncStatus: SyncStatus = SyncStatus.Synced,
    override val pendingOperations: List<PendingOperation> = emptyList(),
    override val conflicts: List<ConflictInfo> = emptyList(),
    override val isOptimistic: Boolean = false,
) : SyncAwareState

sealed interface TaskIntent : Intent {
    data class AddTask(val title: String) : TaskIntent
    data class ToggleDone(val id: String) : TaskIntent
    data class DeleteTask(val id: String) : TaskIntent
    data object Refresh : TaskIntent
    data object GoOffline : TaskIntent
    data object GoOnline : TaskIntent
    data object SimulateConflict : TaskIntent
    data class ResolveConflict(val entityId: String, val decision: ConflictDecision) : TaskIntent
}

sealed interface TaskEffect : Effect {
    data class NavigateToDetail(val id: String) : TaskEffect
    data class ShowMessage(val message: String) : TaskEffect
}

/**
 * Sample ViewModel driven by a **real** [LocalSyncForgeLoop]:
 * SyncManager.enqueueChange → outbox → push/pull → conflict resolution.
 */
class TaskViewModel(
    private val loop: LocalSyncForgeLoop = ForgeNavSync.localLoop(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ),
) : MviViewModel<TaskListState, TaskIntent, TaskEffect>(
    initialState = TaskListState(),
    parentContext = Dispatchers.Main,
    syncFacade = null, // bind to loop.facade streams below (store-driven tasks)
) {
    init {
        // Seed + observe store / facade
        scope.launch {
            loop.seedDefaults()
        }
        scope.launch {
            loop.tasks.collect { entities ->
                setState { state ->
                    state.copy(
                        tasks = entities.map { it.toUiTask() },
                        isOptimistic = entities.any { it.syncState == SyncState.PENDING },
                    )
                }
            }
        }
        scope.launch {
            loop.facade.status.collect { status ->
                setState { it.copy(syncStatus = status) }
            }
        }
        scope.launch {
            loop.facade.pendingOperations.collect { ops ->
                setState { it.copy(pendingOperations = ops) }
            }
        }
        scope.launch {
            loop.facade.conflicts.collect { conflicts ->
                setState { it.copy(conflicts = conflicts) }
            }
        }
    }

    override fun reduce(state: TaskListState, intent: TaskIntent): TaskListState {
        // All mutations go through SyncForge side effects — state is store-driven.
        return state
    }

    override suspend fun handleSideEffect(
        intent: TaskIntent,
        previous: TaskListState,
        current: TaskListState,
    ) {
        when (intent) {
            is TaskIntent.AddTask -> {
                if (intent.title.isBlank()) return
                loop.addTask(intent.title)
                emitEffect(TaskEffect.ShowMessage("Task enqueued (optimistic + outbox)"))
            }
            is TaskIntent.ToggleDone -> {
                loop.toggleDone(intent.id)
            }
            is TaskIntent.DeleteTask -> {
                loop.deleteTask(intent.id)
            }
            TaskIntent.Refresh -> {
                loop.setOnline(true)
                loop.syncNow()
                emitEffect(TaskEffect.ShowMessage("Sync finished"))
            }
            TaskIntent.GoOffline -> {
                loop.setOnline(false)
                // Triggers SyncManager refreshStatus → SyncStatus.Offline
                loop.syncNow()
                emitEffect(TaskEffect.ShowMessage("Offline — mutations will queue"))
            }
            TaskIntent.GoOnline -> {
                loop.setOnline(true)
                loop.syncNow()
                emitEffect(TaskEffect.ShowMessage("Back online — synced"))
            }
            TaskIntent.SimulateConflict -> {
                loop.setOnline(true)
                loop.simulateConflict()
                emitEffect(TaskEffect.ShowMessage("Conflict injected via pull"))
            }
            is TaskIntent.ResolveConflict -> {
                val conflict = current.conflicts.firstOrNull { it.entityId == intent.entityId }
                    ?: current.conflicts.firstOrNull()
                    ?: return
                loop.facade.resolveConflict(conflict, intent.decision)
                if (intent.decision is ConflictDecision.AcceptRemote) {
                    rollbackAllOptimistic()
                }
                emitEffect(TaskEffect.ShowMessage("Conflict resolved"))
            }
        }
    }

    private fun TaskEntity.toUiTask(): Task = Task(
        id = id,
        title = title,
        done = done,
        pendingSync = syncState == SyncState.PENDING || syncState == SyncState.FAILED,
        inConflict = syncState == SyncState.CONFLICT,
    )
}
