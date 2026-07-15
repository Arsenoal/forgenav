package dev.forgenav.compose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.forgenav.sync.ConflictDecision
import dev.forgenav.sync.ConflictInfo

/**
 * Standard conflict resolution dialog.
 *
 * Present when [conflicts] is non-empty; typically bound to the first conflict
 * while the rest remain queued.
 */
@Composable
fun ConflictResolutionDialog(
    conflict: ConflictInfo?,
    onDecision: (ConflictInfo, ConflictDecision) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Resolve sync conflict",
    allowMerge: Boolean = false,
    onRequestMerge: ((ConflictInfo) -> Unit)? = null,
) {
    if (conflict == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = "Entity ${conflict.entityType} (${conflict.entityId}) has diverged " +
                        "between this device and the server.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (conflict.fields.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Conflicting fields: ${conflict.fields.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDecision(conflict, ConflictDecision.KeepLocal) }) {
                Text("Keep mine")
            }
        },
        dismissButton = {
            Column(Modifier.padding(end = 8.dp)) {
                TextButton(onClick = { onDecision(conflict, ConflictDecision.AcceptRemote) }) {
                    Text("Use server")
                }
                if (allowMerge && onRequestMerge != null) {
                    OutlinedButton(
                        onClick = { onRequestMerge(conflict) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Merge…")
                    }
                }
                TextButton(onClick = { onDecision(conflict, ConflictDecision.DeleteLocal) }) {
                    Text("Delete local")
                }
            }
        },
    )
}

/**
 * Convenience: show dialog for the first conflict in a list.
 */
@Composable
fun ConflictResolutionDialog(
    conflicts: List<ConflictInfo>,
    onDecision: (ConflictInfo, ConflictDecision) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConflictResolutionDialog(
        conflict = conflicts.firstOrNull(),
        onDecision = onDecision,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
