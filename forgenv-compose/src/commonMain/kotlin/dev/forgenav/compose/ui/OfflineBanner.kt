package dev.forgenav.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.forgenav.sync.SyncStatus

/**
 * Full-width banner shown when the device / engine reports offline.
 */
@Composable
fun OfflineBanner(
    status: SyncStatus,
    modifier: Modifier = Modifier,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val offline = status as? SyncStatus.Offline
    AnimatedVisibility(
        visible = offline != null,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        if (offline == null) return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = message ?: defaultOfflineMessage(offline),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (onRetry != null) {
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

private fun defaultOfflineMessage(offline: SyncStatus.Offline): String =
    if (offline.pendingCount > 0) {
        "You're offline. ${offline.pendingCount} change(s) will sync when you're back online."
    } else {
        "You're offline. Changes will sync when connectivity returns."
    }
