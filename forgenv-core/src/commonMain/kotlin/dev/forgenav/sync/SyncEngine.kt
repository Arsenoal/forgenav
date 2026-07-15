package dev.forgenav.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Port for any offline-first sync engine (SyncForge, custom outbox, etc.).
 *
 * ForgeNav never depends on a concrete engine in `forgenv-core`. Optional modules
 * (`forgenv-syncforge`) adapt real engines onto these interfaces.
 */
interface SyncEngine {
    val status: StateFlow<SyncStatus>
    val pendingOperations: Flow<List<PendingOperation>>
    val conflicts: Flow<List<ConflictInfo>>

    suspend fun syncNow()
    suspend fun retryFailed()
    fun isOnline(): Boolean
}

/**
 * Outbox port — enqueue local mutations and observe pending work.
 */
interface Outbox {
    fun observePending(): Flow<List<PendingOperation>>
    fun observePendingCount(): Flow<Int>

    suspend fun enqueue(operation: PendingOperation)
    suspend fun acknowledge(ids: List<String>)
    suspend fun markFailed(id: String, error: String, permanently: Boolean = false)
    suspend fun removeForEntity(entityType: String, entityId: String)
    suspend fun clear()
}

/**
 * Strategy for resolving [ConflictInfo] — automatic or user-driven.
 */
interface ConflictResolver {
    /**
     * Attempt automatic resolution. Return null to escalate to UI.
     */
    suspend fun resolve(conflict: ConflictInfo): ConflictDecision?

    /**
     * Apply a user-selected or automatic decision to the store / outbox.
     */
    suspend fun apply(conflict: ConflictInfo, decision: ConflictDecision)
}

/**
 * Composite that wires engine + outbox + resolver for ViewModels.
 */
class SyncFacade(
    val engine: SyncEngine,
    val outbox: Outbox,
    val conflictResolver: ConflictResolver,
) {
    val status: StateFlow<SyncStatus> get() = engine.status
    val pendingOperations: Flow<List<PendingOperation>> get() = engine.pendingOperations
    val conflicts: Flow<List<ConflictInfo>> get() = engine.conflicts

    suspend fun resolveConflict(conflict: ConflictInfo, decision: ConflictDecision) {
        conflictResolver.apply(conflict, decision)
    }

    suspend fun resolveAutomatically(conflict: ConflictInfo): ConflictDecision? {
        val decision = conflictResolver.resolve(conflict) ?: return null
        conflictResolver.apply(conflict, decision)
        return decision
    }
}

/**
 * No-op implementations for previews and apps without sync.
 */
object NoOpSyncEngine : SyncEngine {
    private val _status = kotlinx.coroutines.flow.MutableStateFlow<SyncStatus>(SyncStatus.Synced)
    override val status: StateFlow<SyncStatus> = _status
    override val pendingOperations: Flow<List<PendingOperation>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    override val conflicts: Flow<List<ConflictInfo>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun syncNow() = Unit
    override suspend fun retryFailed() = Unit
    override fun isOnline(): Boolean = true
}

object NoOpOutbox : Outbox {
    override fun observePending(): Flow<List<PendingOperation>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observePendingCount(): Flow<Int> =
        kotlinx.coroutines.flow.flowOf(0)

    override suspend fun enqueue(operation: PendingOperation) = Unit
    override suspend fun acknowledge(ids: List<String>) = Unit
    override suspend fun markFailed(id: String, error: String, permanently: Boolean) = Unit
    override suspend fun removeForEntity(entityType: String, entityId: String) = Unit
    override suspend fun clear() = Unit
}

object KeepLocalConflictResolver : ConflictResolver {
    override suspend fun resolve(conflict: ConflictInfo): ConflictDecision =
        ConflictDecision.KeepLocal

    override suspend fun apply(conflict: ConflictInfo, decision: ConflictDecision) = Unit
}
