package dev.forgenav.syncforge.adapter

import dev.forgenav.sync.ConflictDecision
import dev.forgenav.sync.ConflictInfo
import dev.forgenav.sync.ConflictResolver
import dev.forgenav.sync.Outbox
import dev.forgenav.sync.PendingOperation
import dev.forgenav.sync.SyncEngine
import dev.forgenav.sync.SyncFacade
import dev.forgenav.sync.SyncStatus
import dev.forgenav.syncforge.mapper.SyncForgeMappers
import dev.syncforge.conflict.ConflictStore
import dev.syncforge.network.NetworkMonitor
import dev.syncforge.outbox.OutboxRepository
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Adapts a real [SyncManager] (+ outbox / conflict store) to ForgeNav [SyncFacade].
 */
class SyncForgeEngineAdapter(
    private val scope: CoroutineScope,
    private val syncManager: SyncManager,
    private val outboxRepository: OutboxRepository,
    private val conflictStore: ConflictStore? = null,
    private val networkMonitor: NetworkMonitor? = null,
    private val maxRetries: Int = 5,
) : SyncEngine {

    private val openConflicts: Flow<List<ConflictInfo>> =
        when {
            conflictStore != null ->
                conflictStore.observeOpen().map { records ->
                    records.map(SyncForgeMappers::toConflictInfo)
                }
            else ->
                syncManager.conflicts.map { list ->
                    list.map(SyncForgeMappers::toConflictInfo)
                }
        }

    override val status: StateFlow<SyncStatus> =
        combine(
            syncManager.status,
            openConflicts,
            networkMonitor?.observeOnline() ?: flowOf(true),
        ) { sfStatus, conflicts, online ->
            SyncForgeMappers.toForgeStatus(
                status = sfStatus,
                openConflictCount = conflicts.size,
                networkOnline = online,
            )
        }.stateIn(scope, SharingStarted.Eagerly, SyncStatus.Synced)

    override val pendingOperations: Flow<List<PendingOperation>> =
        outboxRepository.observeAll().map { entries ->
            entries.map { SyncForgeMappers.toPendingOperation(it, maxRetries) }
        }

    override val conflicts: Flow<List<ConflictInfo>> = openConflicts

    override suspend fun syncNow() {
        syncManager.sync()
    }

    override suspend fun retryFailed() {
        syncManager.push()
    }

    override fun isOnline(): Boolean = networkMonitor?.isOnline ?: (status.value !is SyncStatus.Offline)

    fun asOutbox(): Outbox = SyncForgeOutboxAdapter(outboxRepository, maxRetries)

    fun asConflictResolver(): ConflictResolver =
        SyncForgeConflictResolverAdapter(syncManager, conflictStore)

    fun asFacade(): SyncFacade =
        SyncFacade(
            engine = this,
            outbox = asOutbox(),
            conflictResolver = asConflictResolver(),
        )
}

class SyncForgeOutboxAdapter(
    private val repository: OutboxRepository,
    private val maxRetries: Int = 5,
) : Outbox {

    override fun observePending(): Flow<List<PendingOperation>> =
        repository.observePending().map { list ->
            list.map { SyncForgeMappers.toPendingOperation(it, maxRetries) }
        }

    override fun observePendingCount(): Flow<Int> = repository.observePendingCount()

    override suspend fun enqueue(operation: PendingOperation) {
        error(
            "Enqueue via SyncManager.enqueueChange(Change) so optimistic entity writes stay consistent. " +
                "PendingOperation is a UI projection, not a write API.",
        )
    }

    override suspend fun acknowledge(ids: List<String>) {
        repository.markAcknowledged(ids.mapNotNull { it.toLongOrNull() })
    }

    override suspend fun markFailed(id: String, error: String, permanently: Boolean) {
        val longId = id.toLongOrNull() ?: return
        repository.markFailed(
            id = longId,
            error = error,
            retryable = !permanently,
            maxRetries = maxRetries,
            retryAtMillis = null,
        )
    }

    override suspend fun removeForEntity(entityType: String, entityId: String) {
        repository.removeForEntity(entityType, entityId)
    }

    override suspend fun clear() {
        repository.clear()
    }
}

class SyncForgeConflictResolverAdapter(
    private val syncManager: SyncManager,
    private val conflictStore: ConflictStore? = null,
) : ConflictResolver {

    override suspend fun resolve(conflict: ConflictInfo): ConflictDecision? = null

    override suspend fun apply(conflict: ConflictInfo, decision: ConflictDecision) {
        if (decision is ConflictDecision.Merge) {
            syncManager.resolveConflict(
                entityType = conflict.entityType,
                entityId = conflict.entityId,
                choice = SyncForgeMappers.toSyncForgeChoice(ConflictDecision.KeepLocal),
            )
            return
        }
        syncManager.resolveConflict(
            entityType = conflict.entityType,
            entityId = conflict.entityId,
            choice = SyncForgeMappers.toSyncForgeChoice(decision),
        )
    }
}
