package dev.forgenav.syncforge.adapter

import dev.forgenav.sync.ConflictDecision
import dev.forgenav.sync.ConflictInfo
import dev.forgenav.sync.ConflictResolver
import dev.forgenav.sync.Outbox
import dev.forgenav.sync.PendingOperation
import dev.forgenav.sync.SyncEngine
import dev.forgenav.sync.SyncFacade
import dev.forgenav.sync.SyncStatus
import dev.forgenav.syncforge.mapper.SyncStatusMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Functional bridge that plugs **any** outbox-based sync engine (including SyncForge)
 * into ForgeNav with a handful of lambdas — typically ~5 lines at the call site.
 *
 * ## SyncForge example
 * ```kotlin
 * val facade = SyncForgeBridge.bind(
 *     scope = appScope,
 *     observeStatus = { syncManager.status },
 *     mapStatus = SyncStatusMapper::fromSyncForge,
 *     observePending = { outbox.observePending().map { it.toForgePending() } },
 *     observeConflicts = { conflictStore.observe().map { it.toForgeConflicts() } },
 *     onSyncNow = { syncManager.sync() },
 *     onRetryFailed = { syncManager.retryFailed() },
 *     checkOnline = { connectivity.isOnline },
 *     onEnqueue = { op -> outbox.enqueue(...) },
 *     onAcknowledge = { ids -> outbox.markAcknowledged(ids.map { it.toLong() }) },
 *     onApplyConflict = { conflict, decision -> conflictResolver.apply(conflict, decision) },
 * )
 * ```
 */
object SyncForgeBridge {

    fun bind(
        scope: CoroutineScope,
        observeStatus: () -> Flow<Any>,
        mapStatus: (Any) -> SyncStatus = { SyncStatusMapper.mapUnknown(it) },
        observePending: () -> Flow<List<PendingOperation>>,
        observeConflicts: () -> Flow<List<ConflictInfo>> = { flowOf(emptyList()) },
        onSyncNow: suspend () -> Unit,
        onRetryFailed: suspend () -> Unit = onSyncNow,
        checkOnline: () -> Boolean = { true },
        onEnqueue: suspend (PendingOperation) -> Unit = {},
        onAcknowledge: suspend (List<String>) -> Unit = {},
        onMarkFailed: suspend (id: String, error: String, permanently: Boolean) -> Unit = { _, _, _ -> },
        onRemoveForEntity: suspend (entityType: String, entityId: String) -> Unit = { _, _ -> },
        onClearOutbox: suspend () -> Unit = {},
        autoResolve: suspend (ConflictInfo) -> ConflictDecision? = { null },
        onApplyConflict: suspend (ConflictInfo, ConflictDecision) -> Unit = { _, _ -> },
    ): SyncFacade {
        val engine = object : SyncEngine {
            override val status: StateFlow<SyncStatus> =
                observeStatus()
                    .map(mapStatus)
                    .stateIn(scope, SharingStarted.Eagerly, SyncStatus.Synced)

            override val pendingOperations: Flow<List<PendingOperation>> = observePending()
            override val conflicts: Flow<List<ConflictInfo>> = observeConflicts()

            override suspend fun syncNow() = onSyncNow()
            override suspend fun retryFailed() = onRetryFailed()
            override fun isOnline(): Boolean = checkOnline()
        }

        val outbox = object : Outbox {
            override fun observePending(): Flow<List<PendingOperation>> = observePending()
            override fun observePendingCount(): Flow<Int> =
                observePending().map { list -> list.count { !it.permanentlyFailed } }

            override suspend fun enqueue(operation: PendingOperation) = onEnqueue(operation)
            override suspend fun acknowledge(ids: List<String>) = onAcknowledge(ids)
            override suspend fun markFailed(id: String, error: String, permanently: Boolean) =
                onMarkFailed(id, error, permanently)

            override suspend fun removeForEntity(entityType: String, entityId: String) =
                onRemoveForEntity(entityType, entityId)

            override suspend fun clear() = onClearOutbox()
        }

        val resolver = object : ConflictResolver {
            override suspend fun resolve(conflict: ConflictInfo): ConflictDecision? =
                autoResolve(conflict)

            override suspend fun apply(conflict: ConflictInfo, decision: ConflictDecision) =
                onApplyConflict(conflict, decision)
        }

        return SyncFacade(engine, outbox, resolver)
    }

    /**
     * Typed overload when the engine already exposes ForgeNav [SyncStatus].
     */
    fun bindTyped(
        scope: CoroutineScope,
        status: StateFlow<SyncStatus>,
        pending: Flow<List<PendingOperation>>,
        conflicts: Flow<List<ConflictInfo>> = flowOf(emptyList()),
        onSyncNow: suspend () -> Unit,
        onRetryFailed: suspend () -> Unit = onSyncNow,
        checkOnline: () -> Boolean = { true },
        outbox: Outbox,
        conflictResolver: ConflictResolver,
    ): SyncFacade {
        val engine = object : SyncEngine {
            override val status: StateFlow<SyncStatus> = status
            override val pendingOperations: Flow<List<PendingOperation>> = pending
            override val conflicts: Flow<List<ConflictInfo>> = conflicts
            override suspend fun syncNow() = onSyncNow()
            override suspend fun retryFailed() = onRetryFailed()
            override fun isOnline(): Boolean = checkOnline()
        }
        return SyncFacade(engine, outbox, conflictResolver)
    }
}
