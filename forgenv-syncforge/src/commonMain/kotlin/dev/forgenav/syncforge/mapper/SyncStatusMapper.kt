package dev.forgenav.syncforge.mapper

import dev.forgenav.sync.PendingOperation
import dev.forgenav.sync.SyncStatus

/**
 * Maps SyncForge (and similar) status models into ForgeNav [SyncStatus].
 *
 * SyncForge sealed hierarchy (studio.syncforge 2.x) roughly looks like:
 * - Idle
 * - Syncing(phase)
 * - Pending(outboxCount, permanentlyFailedCount, conflictCount)
 * - Offline(outboxCount)
 * - LastSynced(timestampMillis)
 * - Error(message, retryable)
 *
 * Because this module does not hard-depend on SyncForge artifacts, mapping uses
 * structural / name-based conversion plus explicit helpers for known shapes.
 */
object SyncStatusMapper {

    /**
     * Best-effort mapping from an opaque engine status (e.g. SyncForge SyncStatus).
     * Prefer [fromComponents] when you control the call site.
     */
    fun mapUnknown(value: Any): SyncStatus {
        val name = value::class.simpleName ?: return SyncStatus.Synced
        return when {
            name.equals("Idle", ignoreCase = true) ||
                name.equals("LastSynced", ignoreCase = true) ||
                name.equals("Synced", ignoreCase = true) -> SyncStatus.Synced

            name.equals("Syncing", ignoreCase = true) -> SyncStatus.Syncing()

            name.equals("Pending", ignoreCase = true) -> {
                val count = readInt(value, "outboxCount", "operationCount", "pendingCount") ?: 0
                val failed = readInt(value, "permanentlyFailedCount", "failedCount") ?: 0
                val conflicts = readInt(value, "conflictCount") ?: 0
                SyncStatus.Pending(count, failed, conflicts)
            }

            name.equals("Offline", ignoreCase = true) -> {
                val count = readInt(value, "outboxCount", "pendingCount") ?: 0
                SyncStatus.Offline(count)
            }

            name.equals("Error", ignoreCase = true) -> {
                val message = readString(value, "message") ?: "Sync error"
                val retryable = readBoolean(value, "retryable") ?: true
                SyncStatus.Error(message, retryable)
            }

            name.equals("Conflict", ignoreCase = true) -> {
                val count = readInt(value, "conflictCount", "count") ?: 1
                SyncStatus.Conflict(count)
            }

            else -> SyncStatus.Synced
        }
    }

    /**
     * Explicit mapping API mirroring SyncForge's status fields.
     */
    fun fromComponents(
        kind: Kind,
        outboxCount: Int = 0,
        permanentlyFailedCount: Int = 0,
        conflictCount: Int = 0,
        message: String? = null,
        retryable: Boolean = true,
        phase: SyncStatus.Syncing.Phase = SyncStatus.Syncing.Phase.Full,
    ): SyncStatus = when (kind) {
        Kind.Idle, Kind.LastSynced -> SyncStatus.Synced
        Kind.Syncing -> SyncStatus.Syncing(phase)
        Kind.Pending -> SyncStatus.Pending(outboxCount, permanentlyFailedCount, conflictCount)
        Kind.Offline -> SyncStatus.Offline(outboxCount)
        Kind.Error -> SyncStatus.Error(message ?: "Sync error", retryable)
        Kind.Conflict -> SyncStatus.Conflict(conflictCount.coerceAtLeast(1))
    }

    enum class Kind {
        Idle,
        Syncing,
        Pending,
        Offline,
        LastSynced,
        Error,
        Conflict,
    }

    /**
     * Map a SyncForge-like outbox entry shape into [PendingOperation].
     */
    fun pendingOperation(
        id: String,
        entityType: String,
        entityId: String,
        operation: String,
        enqueuedAtMillis: Long,
        retryCount: Int = 0,
        lastError: String? = null,
        permanentlyFailed: Boolean = false,
    ): PendingOperation = PendingOperation(
        id = id,
        entityType = entityType,
        entityId = entityId,
        operation = when (operation.uppercase()) {
            "CREATE", "INSERT" -> PendingOperation.OperationType.Create
            "UPDATE", "UPSERT" -> PendingOperation.OperationType.Update
            "DELETE", "REMOVE" -> PendingOperation.OperationType.Delete
            else -> PendingOperation.OperationType.Custom
        },
        enqueuedAtMillis = enqueuedAtMillis,
        retryCount = retryCount,
        lastError = lastError,
        permanentlyFailed = permanentlyFailed,
    )

    private fun readInt(value: Any, vararg names: String): Int? {
        val text = value.toString()
        for (name in names) {
            val regex = Regex("""$name[=:]?\s*(\d+)""")
            val m = regex.find(text) ?: continue
            return m.groupValues[1].toIntOrNull()
        }
        return null
    }

    private fun readString(value: Any, vararg names: String): String? {
        val text = value.toString()
        for (name in names) {
            val regex = Regex("""$name[=:]\s*([^,)\]]+)""")
            val m = regex.find(text) ?: continue
            return m.groupValues[1].trim().trim('"', '\'')
        }
        return null
    }

    private fun readBoolean(value: Any, vararg names: String): Boolean? {
        val text = value.toString()
        for (name in names) {
            val regex = Regex("""$name[=:]\s*(true|false)""", RegexOption.IGNORE_CASE)
            val m = regex.find(text) ?: continue
            return m.groupValues[1].equals("true", ignoreCase = true)
        }
        return null
    }
}
