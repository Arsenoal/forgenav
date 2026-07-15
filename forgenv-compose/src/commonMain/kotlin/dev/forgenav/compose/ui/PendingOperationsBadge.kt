package dev.forgenav.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.forgenav.sync.PendingOperation

/**
 * Badge showing the count of pending outbox operations.
 *
 * Pass any trailing content as [icon] (e.g. a cloud / sync icon).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingOperationsBadge(
    operations: List<PendingOperation>,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = {
        Text("↻", style = MaterialTheme.typography.titleMedium)
    },
) {
    val count = operations.count { !it.permanentlyFailed }
    val failed = operations.count { it.permanentlyFailed }
    BadgedBox(
        modifier = modifier,
        badge = {
            if (count > 0 || failed > 0) {
                Badge(
                    containerColor = if (failed > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ) {
                    Text(if (failed > 0) "$failed!" else count.coerceAtMost(99).toString())
                }
            }
        },
    ) {
        Box(Modifier.padding(4.dp)) {
            icon()
        }
    }
}

/**
 * Simple numeric badge without an icon slot.
 */
@Composable
fun PendingCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    Badge(modifier = modifier) {
        Text(count.coerceAtMost(99).toString())
    }
}
