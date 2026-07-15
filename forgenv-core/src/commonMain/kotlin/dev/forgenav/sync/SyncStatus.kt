package dev.forgenav.sync

import kotlinx.serialization.Serializable

/**
 * Cross-cutting sync lifecycle state for UI and [SyncAwareState].
 *
 * Intentionally aligned with offline-first engines (SyncForge, custom outboxes) without
 * depending on any particular SDK. Adapters in `forgenv-syncforge` map engine-specific
 * statuses into this hierarchy.
 */
@Serializable
sealed interface SyncStatus {
    /** Online, idle, no outstanding work. */
    @Serializable
    data object Synced : SyncStatus

    /** A push/pull/full cycle is in flight. */
    @Serializable
    data class Syncing(
        val phase: Phase = Phase.Full,
        val progress: Float? = null,
    ) : SyncStatus {
        @Serializable
        enum class Phase { Push, Pull, Full }
    }

    /** Local mutations waiting in the outbox. */
    @Serializable
    data class Pending(
        val operationCount: Int,
        val failedCount: Int = 0,
        val conflictCount: Int = 0,
    ) : SyncStatus

    /** No network — mutations are queued. */
    @Serializable
    data class Offline(
        val pendingCount: Int = 0,
    ) : SyncStatus

    /** One or more entities require conflict resolution. */
    @Serializable
    data class Conflict(
        val conflictCount: Int,
        val entityIds: List<String> = emptyList(),
    ) : SyncStatus

    /** User-visible sync failure. */
    @Serializable
    data class Error(
        val message: String,
        val retryable: Boolean = true,
        val causeCode: String? = null,
    ) : SyncStatus
}

/**
 * A single outbox / pending mutation for badges and debug panels.
 */
@Serializable
data class PendingOperation(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: OperationType,
    val enqueuedAtMillis: Long,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val permanentlyFailed: Boolean = false,
) {
    @Serializable
    enum class OperationType { Create, Update, Delete, Custom }
}

/**
 * Conflict payload presented to the UI / [ConflictResolver].
 */
@Serializable
data class ConflictInfo(
    val id: String,
    val entityType: String,
    val entityId: String,
    val localSnapshotJson: String?,
    val remoteSnapshotJson: String?,
    val detectedAtMillis: Long,
    val fields: List<String> = emptyList(),
)

/**
 * Result of resolving a [ConflictInfo].
 */
@Serializable
sealed interface ConflictDecision {
    @Serializable
    data object KeepLocal : ConflictDecision

    @Serializable
    data object AcceptRemote : ConflictDecision

    @Serializable
    data class Merge(val mergedJson: String) : ConflictDecision

    @Serializable
    data object DeleteLocal : ConflictDecision
}
