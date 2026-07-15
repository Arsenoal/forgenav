package dev.forgenav.syncforge

import dev.forgenav.sync.ConflictDecision
import dev.forgenav.sync.ConflictInfo
import dev.forgenav.sync.PendingOperation
import dev.forgenav.sync.SyncFacade
import dev.forgenav.sync.SyncStatus
import dev.forgenav.syncforge.adapter.ControllableSyncEngine
import dev.forgenav.syncforge.adapter.InMemoryOutbox
import dev.forgenav.syncforge.adapter.PreferLocalConflictResolver
import dev.forgenav.syncforge.adapter.SyncForgeBridge
import dev.forgenav.syncforge.adapter.SyncForgeEngineAdapter
import dev.forgenav.syncforge.adapter.UiDrivenConflictResolver
import dev.forgenav.syncforge.adapter.demoSyncFacade
import dev.forgenav.syncforge.loop.LocalSyncForgeLoop
import dev.syncforge.conflict.ConflictStore
import dev.syncforge.outbox.OutboxRepository
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Public entry points for SyncForge integration.
 *
 * Prefer [fromSyncManager] for production and [localLoop] for samples / offline demos.
 */
object ForgeNavSync {

    /**
     * Production path: bind a live [SyncManager] (and its outbox) to ForgeNav.
     *
     * ```kotlin
     * val facade = ForgeNavSync.fromSyncManager(
     *     scope = appScope,
     *     syncManager = syncManager,
     *     outbox = outboxRepository,
     *     conflictStore = conflictStore,
     * )
     * ```
     */
    fun fromSyncManager(
        scope: CoroutineScope,
        syncManager: SyncManager,
        outbox: OutboxRepository,
        conflictStore: ConflictStore? = null,
        networkMonitor: dev.syncforge.network.NetworkMonitor? = null,
        maxRetries: Int = 5,
    ): SyncFacade =
        SyncForgeEngineAdapter(
            scope = scope,
            syncManager = syncManager,
            outboxRepository = outbox,
            conflictStore = conflictStore,
            networkMonitor = networkMonitor,
            maxRetries = maxRetries,
        ).asFacade()

    /**
     * Real in-process SyncForge loop (no remote server): outbox + push/pull + conflicts.
     */
    fun localLoop(scope: CoroutineScope): LocalSyncForgeLoop = LocalSyncForgeLoop(scope)

    /**
     * Functional bridge when you already have mapped flows (legacy / custom engines).
     */
    fun withSyncForge(
        scope: CoroutineScope,
        status: Flow<*>,
        pending: Flow<List<PendingOperation>>,
        conflicts: Flow<List<ConflictInfo>> = flowOf(emptyList()),
        onSyncNow: suspend () -> Unit,
        onRetryFailed: suspend () -> Unit = onSyncNow,
        checkOnline: () -> Boolean = { true },
        onEnqueue: suspend (PendingOperation) -> Unit = {},
        onAcknowledge: suspend (List<String>) -> Unit = {},
        onApplyConflict: suspend (ConflictInfo, ConflictDecision) -> Unit = { _, _ -> },
        autoResolve: suspend (ConflictInfo) -> ConflictDecision? = { null },
    ): SyncFacade = SyncForgeBridge.bind(
        scope = scope,
        observeStatus = { @Suppress("UNCHECKED_CAST") (status as Flow<Any>) },
        observePending = { pending },
        observeConflicts = { conflicts },
        onSyncNow = onSyncNow,
        onRetryFailed = onRetryFailed,
        checkOnline = checkOnline,
        onEnqueue = onEnqueue,
        onAcknowledge = onAcknowledge,
        onApplyConflict = onApplyConflict,
        autoResolve = autoResolve,
    )

    fun withMappedStatus(
        scope: CoroutineScope,
        status: StateFlow<SyncStatus>,
        pending: Flow<List<PendingOperation>>,
        conflicts: Flow<List<ConflictInfo>> = flowOf(emptyList()),
        onSyncNow: suspend () -> Unit,
        checkOnline: () -> Boolean = { true },
        preferLocalConflicts: Boolean = false,
    ): SyncFacade {
        val outbox = InMemoryOutbox()
        val resolver = if (preferLocalConflicts) {
            PreferLocalConflictResolver()
        } else {
            UiDrivenConflictResolver()
        }
        return SyncForgeBridge.bindTyped(
            scope = scope,
            status = status,
            pending = pending,
            conflicts = conflicts,
            onSyncNow = onSyncNow,
            checkOnline = checkOnline,
            outbox = outbox,
            conflictResolver = resolver,
        )
    }

    /** Lightweight demo facade without SyncForge (previews only). */
    fun demo(initial: SyncStatus = SyncStatus.Synced): SyncFacade = demoSyncFacade(initial)

    fun controllable(initial: SyncStatus = SyncStatus.Synced): ControllableSyncEngine =
        ControllableSyncEngine(initial)
}
