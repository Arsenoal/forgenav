package dev.forgenav.syncforge.adapter

import dev.forgenav.sync.ConflictDecision
import dev.forgenav.sync.ConflictInfo
import dev.forgenav.sync.ConflictResolver
import dev.forgenav.sync.Outbox
import dev.forgenav.sync.PendingOperation
import dev.forgenav.sync.SyncEngine
import dev.forgenav.sync.SyncFacade
import dev.forgenav.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [Outbox] for tests, demos, and engines that push state into ForgeNav.
 */
class InMemoryOutbox : Outbox {
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<PendingOperation>>(emptyList())

    override fun observePending(): Flow<List<PendingOperation>> = _entries

    override fun observePendingCount(): Flow<Int> = _entries.map { list ->
        list.count { !it.permanentlyFailed }
    }

    override suspend fun enqueue(operation: PendingOperation) {
        mutex.withLock {
            _entries.update { it + operation }
        }
    }

    override suspend fun acknowledge(ids: List<String>) {
        mutex.withLock {
            _entries.update { list -> list.filterNot { it.id in ids } }
        }
    }

    override suspend fun markFailed(id: String, error: String, permanently: Boolean) {
        mutex.withLock {
            _entries.update { list ->
                list.map {
                    if (it.id == id) {
                        it.copy(
                            lastError = error,
                            permanentlyFailed = permanently,
                            retryCount = it.retryCount + 1,
                        )
                    } else {
                        it
                    }
                }
            }
        }
    }

    override suspend fun removeForEntity(entityType: String, entityId: String) {
        mutex.withLock {
            _entries.update { list ->
                list.filterNot { it.entityType == entityType && it.entityId == entityId }
            }
        }
    }

    override suspend fun clear() {
        mutex.withLock { _entries.value = emptyList() }
    }
}

/**
 * Controllable [SyncEngine] for samples and unit tests.
 */
class ControllableSyncEngine(
    initial: SyncStatus = SyncStatus.Synced,
) : SyncEngine {
    private val _status = MutableStateFlow(initial)
    private val _pending = MutableStateFlow<List<PendingOperation>>(emptyList())
    private val _conflicts = MutableStateFlow<List<ConflictInfo>>(emptyList())
    private var online = true

    override val status: StateFlow<SyncStatus> = _status.asStateFlow()
    override val pendingOperations: Flow<List<PendingOperation>> = _pending
    override val conflicts: Flow<List<ConflictInfo>> = _conflicts

    fun setStatus(status: SyncStatus) {
        _status.value = status
    }

    fun setPending(operations: List<PendingOperation>) {
        _pending.value = operations
        recomputeStatus()
    }

    fun setConflicts(conflicts: List<ConflictInfo>) {
        _conflicts.value = conflicts
        recomputeStatus()
    }

    fun setOnline(value: Boolean) {
        online = value
        recomputeStatus()
    }

    private fun recomputeStatus() {
        if (!online) {
            _status.value = SyncStatus.Offline(_pending.value.size)
            return
        }
        if (_conflicts.value.isNotEmpty()) {
            _status.value = SyncStatus.Conflict(
                conflictCount = _conflicts.value.size,
                entityIds = _conflicts.value.map { it.entityId },
            )
            return
        }
        val pending = _pending.value.filterNot { it.permanentlyFailed }
        val failed = _pending.value.count { it.permanentlyFailed }
        _status.value = when {
            pending.isNotEmpty() -> SyncStatus.Pending(
                operationCount = pending.size,
                failedCount = failed,
                conflictCount = 0,
            )
            else -> SyncStatus.Synced
        }
    }

    override suspend fun syncNow() {
        if (!online) {
            _status.value = SyncStatus.Offline(_pending.value.size)
            return
        }
        _status.value = SyncStatus.Syncing()
        // Demo: clear non-failed pending
        _pending.value = _pending.value.filter { it.permanentlyFailed }
        recomputeStatus()
    }

    override suspend fun retryFailed() {
        _pending.update { list ->
            list.map { if (it.permanentlyFailed) it.copy(permanentlyFailed = false, lastError = null) else it }
        }
        recomputeStatus()
        syncNow()
    }

    override fun isOnline(): Boolean = online
}

/**
 * [ConflictResolver] that defers to the UI (always returns null from [resolve]).
 */
class UiDrivenConflictResolver(
    private val onApply: suspend (ConflictInfo, ConflictDecision) -> Unit = { _, _ -> },
) : ConflictResolver {
    override suspend fun resolve(conflict: ConflictInfo): ConflictDecision? = null

    override suspend fun apply(conflict: ConflictInfo, decision: ConflictDecision) {
        onApply(conflict, decision)
    }
}

/**
 * Prefer-local automatic resolver for low-stakes entities.
 */
class PreferLocalConflictResolver(
    private val onApply: suspend (ConflictInfo, ConflictDecision) -> Unit = { _, _ -> },
) : ConflictResolver {
    override suspend fun resolve(conflict: ConflictInfo): ConflictDecision =
        ConflictDecision.KeepLocal

    override suspend fun apply(conflict: ConflictInfo, decision: ConflictDecision) {
        onApply(conflict, decision)
    }
}

/**
 * Build a demo [SyncFacade] with in-memory adapters (~3 lines for samples).
 */
fun demoSyncFacade(
    initialStatus: SyncStatus = SyncStatus.Synced,
): SyncFacade {
    val engine = ControllableSyncEngine(initialStatus)
    val outbox = InMemoryOutbox()
    val resolver = UiDrivenConflictResolver()
    return SyncFacade(engine, outbox, resolver)
}
