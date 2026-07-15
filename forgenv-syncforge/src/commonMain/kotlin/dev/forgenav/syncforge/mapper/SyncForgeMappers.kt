package dev.forgenav.syncforge.mapper

import dev.forgenav.sync.ConflictDecision
import dev.forgenav.sync.ConflictInfo
import dev.forgenav.sync.PendingOperation
import dev.forgenav.sync.SyncStatus
import dev.syncforge.conflict.ConflictChoice
import dev.syncforge.conflict.ConflictRecord
import dev.syncforge.conflict.ConflictSummary
import dev.syncforge.model.ChangeType
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncStatus as SfSyncStatus

/**
 * Typed mappers between SyncForge models and ForgeNav presentation models.
 */
object SyncForgeMappers {

    fun toForgeStatus(
        status: SfSyncStatus,
        openConflictCount: Int = 0,
        networkOnline: Boolean = true,
    ): SyncStatus {
        // Open conflicts are a first-class ForgeNav state when present.
        if (openConflictCount > 0 && status !is SfSyncStatus.Syncing) {
            return SyncStatus.Conflict(conflictCount = openConflictCount)
        }
        if (!networkOnline && status !is SfSyncStatus.Syncing) {
            val pending = when (status) {
                is SfSyncStatus.Offline -> status.outboxCount
                is SfSyncStatus.Pending -> status.outboxCount
                else -> 0
            }
            return SyncStatus.Offline(pendingCount = pending)
        }
        return when (status) {
            is SfSyncStatus.Idle,
            is SfSyncStatus.LastSynced,
            -> SyncStatus.Synced

            is SfSyncStatus.Syncing -> SyncStatus.Syncing(
                phase = when (status.phase) {
                    SfSyncStatus.Syncing.Phase.PUSH -> SyncStatus.Syncing.Phase.Push
                    SfSyncStatus.Syncing.Phase.PULL -> SyncStatus.Syncing.Phase.Pull
                    SfSyncStatus.Syncing.Phase.FULL -> SyncStatus.Syncing.Phase.Full
                },
            )

            is SfSyncStatus.Pending -> SyncStatus.Pending(
                operationCount = status.outboxCount,
                failedCount = status.permanentlyFailedCount,
                conflictCount = status.conflictCount,
            )

            is SfSyncStatus.Offline -> SyncStatus.Offline(pendingCount = status.outboxCount)

            is SfSyncStatus.Error -> SyncStatus.Error(
                message = status.message,
                retryable = status.retryable,
            )
        }
    }

    fun toPendingOperation(entry: OutboxEntry, maxRetries: Int = 5): PendingOperation =
        PendingOperation(
            id = entry.id.toString(),
            entityType = entry.entityType,
            entityId = entry.entityId,
            operation = when (entry.changeType) {
                ChangeType.CREATE -> PendingOperation.OperationType.Create
                ChangeType.UPDATE -> PendingOperation.OperationType.Update
                ChangeType.DELETE -> PendingOperation.OperationType.Delete
            },
            enqueuedAtMillis = entry.createdAtMillis,
            retryCount = entry.retryCount,
            lastError = entry.lastError,
            permanentlyFailed = entry.isPermanentlyFailed(maxRetries),
        )

    fun toConflictInfo(summary: ConflictSummary): ConflictInfo =
        ConflictInfo(
            id = summary.id.toString(),
            entityType = summary.entityType,
            entityId = summary.entityId,
            localSnapshotJson = null,
            remoteSnapshotJson = null,
            detectedAtMillis = summary.detectedAtMillis,
            fields = emptyList(),
        )

    fun toConflictInfo(record: ConflictRecord): ConflictInfo =
        ConflictInfo(
            id = record.id.toString(),
            entityType = record.entityType,
            entityId = record.entityId,
            localSnapshotJson = record.localJson,
            remoteSnapshotJson = record.remoteJson,
            detectedAtMillis = record.detectedAtMillis,
            fields = emptyList(),
        )

    fun toSyncForgeChoice(decision: ConflictDecision): ConflictChoice = when (decision) {
        ConflictDecision.KeepLocal -> ConflictChoice.KeepLocal
        ConflictDecision.AcceptRemote,
        ConflictDecision.DeleteLocal,
        -> ConflictChoice.AcceptRemote
        is ConflictDecision.Merge -> ConflictChoice.KeepLocal // merge payloads need entity-typed Custom
    }
}
