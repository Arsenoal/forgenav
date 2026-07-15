package dev.forgenav.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.forgenav.sync.SyncStatus

/**
 * Compact chip showing the current [SyncStatus].
 */
@Composable
fun SyncStatusIndicator(
    status: SyncStatus,
    modifier: Modifier = Modifier,
    showWhenSynced: Boolean = false,
) {
    val visible = showWhenSynced || status !is SyncStatus.Synced
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = status.containerColor(),
            contentColor = status.contentColor(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (status is SyncStatus.Syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = status.contentColor(),
                    )
                }
                Text(
                    text = status.label(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun SyncStatus.containerColor() = when (this) {
    is SyncStatus.Synced -> MaterialTheme.colorScheme.secondaryContainer
    is SyncStatus.Syncing -> MaterialTheme.colorScheme.primaryContainer
    is SyncStatus.Pending -> MaterialTheme.colorScheme.tertiaryContainer
    is SyncStatus.Offline -> MaterialTheme.colorScheme.surfaceVariant
    is SyncStatus.Conflict -> MaterialTheme.colorScheme.errorContainer
    is SyncStatus.Error -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun SyncStatus.contentColor() = when (this) {
    is SyncStatus.Synced -> MaterialTheme.colorScheme.onSecondaryContainer
    is SyncStatus.Syncing -> MaterialTheme.colorScheme.onPrimaryContainer
    is SyncStatus.Pending -> MaterialTheme.colorScheme.onTertiaryContainer
    is SyncStatus.Offline -> MaterialTheme.colorScheme.onSurfaceVariant
    is SyncStatus.Conflict -> MaterialTheme.colorScheme.onErrorContainer
    is SyncStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
}

fun SyncStatus.label(): String = when (this) {
    is SyncStatus.Synced -> "Synced"
    is SyncStatus.Syncing -> when (phase) {
        SyncStatus.Syncing.Phase.Push -> "Uploading…"
        SyncStatus.Syncing.Phase.Pull -> "Downloading…"
        SyncStatus.Syncing.Phase.Full -> "Syncing…"
    }
    is SyncStatus.Pending ->
        if (operationCount == 1) "1 pending" else "$operationCount pending"
    is SyncStatus.Offline ->
        if (pendingCount > 0) "Offline · $pendingCount queued" else "Offline"
    is SyncStatus.Conflict ->
        if (conflictCount == 1) "1 conflict" else "$conflictCount conflicts"
    is SyncStatus.Error -> if (retryable) "Sync error · retry" else "Sync error"
}
