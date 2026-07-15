package dev.forgenav.sync

/**
 * Marker for ViewModel / screen state that participates in offline-first UX.
 *
 * Compose helpers ([dev.forgenav.compose.ui.SyncStatusIndicator], etc.) read these
 * properties generically so feature modules stay free of boilerplate.
 */
interface SyncAwareState {
    val syncStatus: SyncStatus
    val pendingOperations: List<PendingOperation>
        get() = emptyList()
    val conflicts: List<ConflictInfo>
        get() = emptyList()
    val isOptimistic: Boolean
        get() = false
}

/**
 * Default data holder implementing [SyncAwareState] for simple screens.
 */
data class DefaultSyncAwareState(
    override val syncStatus: SyncStatus = SyncStatus.Synced,
    override val pendingOperations: List<PendingOperation> = emptyList(),
    override val conflicts: List<ConflictInfo> = emptyList(),
    override val isOptimistic: Boolean = false,
) : SyncAwareState

/**
 * Mixin helpers for immutable state copy patterns.
 */
fun SyncAwareState.withSyncStatus(status: SyncStatus): DefaultSyncAwareState =
    DefaultSyncAwareState(
        syncStatus = status,
        pendingOperations = pendingOperations,
        conflicts = conflicts,
        isOptimistic = isOptimistic,
    )

/**
 * Snapshot of an optimistic mutation that can be rolled back.
 */
data class OptimisticUpdate<T>(
    val id: String,
    val previousState: T,
    val optimisticState: T,
    val operationId: String? = null,
    val createdAtMillis: Long,
)

/**
 * Tracks in-flight optimistic updates and applies rollbacks on conflict / failure.
 */
class OptimisticUpdateTracker<T> {
    private val pending = linkedMapOf<String, OptimisticUpdate<T>>()

    val size: Int get() = pending.size
    val updates: List<OptimisticUpdate<T>> get() = pending.values.toList()

    fun track(update: OptimisticUpdate<T>) {
        pending[update.id] = update
    }

    fun commit(id: String): OptimisticUpdate<T>? = pending.remove(id)

    fun rollback(id: String): OptimisticUpdate<T>? = pending.remove(id)

    fun rollbackAll(): List<OptimisticUpdate<T>> {
        val all = pending.values.toList()
        pending.clear()
        return all
    }

    fun rollbackForOperation(operationId: String): OptimisticUpdate<T>? {
        val match = pending.values.firstOrNull { it.operationId == operationId } ?: return null
        return pending.remove(match.id)
    }
}
