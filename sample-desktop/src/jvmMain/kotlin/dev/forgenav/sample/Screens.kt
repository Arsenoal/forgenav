package dev.forgenav.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.forgenav.compose.nav.LocalForgeNavigator

@Composable
fun TaskDetailScreen(id: String) {
    val navigator = LocalForgeNavigator.current
    Column(Modifier.padding(24.dp)) {
        Text("Task detail", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("id = $id", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "Deep link: forgenav://tasks/$id",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { navigator.popBackStack() }) {
            Text("Back")
        }
    }
}

@Composable
fun SettingsScreen() {
    val navigator = LocalForgeNavigator.current
    Column(Modifier.padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("ForgeNav sample · offline-first navigation + MVI")
        Spacer(Modifier.height(24.dp))
        Button(onClick = { navigator.popBackStack() }) {
            Text("Back")
        }
    }
}

@Composable
fun CreateTaskSheet() {
    val navigator = LocalForgeNavigator.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text("Quick create (bottom sheet)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("PresentationStyle.BottomSheet is rendered by ForgeNavHost.")
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { navigator.popBackStack() }) {
            Text("Close")
        }
    }
}
