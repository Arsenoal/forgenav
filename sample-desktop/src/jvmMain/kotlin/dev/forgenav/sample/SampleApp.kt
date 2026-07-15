package dev.forgenav.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.forgenav.compose.lifecycle.CollectEffects
import dev.forgenav.compose.lifecycle.collectState
import dev.forgenav.compose.lifecycle.rememberForgeViewModel
import dev.forgenav.compose.nav.ForgeNavHost
import dev.forgenav.compose.nav.LocalForgeNavigator
import dev.forgenav.compose.nav.rememberSaveableForgeNavigator
import dev.forgenav.compose.ui.ConflictResolutionDialog
import dev.forgenav.compose.ui.OfflineBanner
import dev.forgenav.compose.ui.PendingOperationsBadge
import dev.forgenav.compose.ui.SyncStatusIndicator
import dev.forgenav.navigation.DeepLinkParser
import dev.forgenav.navigation.NavGraph
import dev.forgenav.navigation.PresentationStyle
import dev.forgenav.navigation.RouteCodec
import dev.forgenav.navigation.RouteMetadata
import dev.forgenav.sync.ConflictDecision

@Composable
fun SampleApp(deepLinkUri: String? = null) {
    val graph = remember {
        NavGraph(id = "root", startRoute = AppRoute.Home)
    }
    val parser = remember {
        DeepLinkParser(graph)
            .register(
                pattern = "forgenav://tasks/{id}",
                serializer = AppRoute.TaskDetail.serializer(),
                stackPrefix = listOf(AppRoute.Home),
            )
    }
    val routeCodec = remember {
        RouteCodec().register("AppRoute", AppRoute.serializer()) { it is AppRoute }
    }
    // Survives process death / config change (saved backstack state).
    val navigator = rememberSaveableForgeNavigator(
        startRoute = AppRoute.Home,
        routeCodec = routeCodec,
        deepLinkParser = parser,
    )
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(deepLinkUri) {
        if (deepLinkUri != null) {
            val link = parser.parse(deepLinkUri)
            if (link != null) navigator.handleDeepLink(link)
            else navigator.handleDeepLink(deepLinkUri)
        }
    }

    MaterialTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                ForgeNavHost(
                    navigator = navigator,
                    // Default: slide + system/predictive back. Try NavTransitions.Fade for softer UX.
                    transitionSpec = dev.forgenav.compose.nav.NavTransitions.SlideHorizontal,
                    enableSystemBack = true,
                ) { entry ->
                    when (val route = entry.route) {
                        is AppRoute.Home -> HomeScreen(snackbar)
                        is AppRoute.TaskDetail -> TaskDetailScreen(route.id)
                        is AppRoute.Settings -> SettingsScreen()
                        is AppRoute.CreateTask -> CreateTaskSheet()
                        else -> Text("Unknown route: $route")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(snackbar: SnackbarHostState) {
    val navigator = LocalForgeNavigator.current
    val vm = rememberForgeViewModel { TaskViewModel() }
    val state = vm.collectState()
    var draft by remember { mutableStateOf("") }

    CollectEffects(vm.effects) { effect ->
        when (effect) {
            is TaskEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
            is TaskEffect.NavigateToDetail ->
                navigator.navigate(AppRoute.TaskDetail(effect.id))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ForgeNav Tasks") },
                actions = {
                    PendingOperationsBadge(state.pendingOperations)
                    Spacer(Modifier.padding(4.dp))
                    SyncStatusIndicator(state.syncStatus, showWhenSynced = true)
                    TextButton(onClick = { navigator.navigate(AppRoute.Settings) }) {
                        Text("Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            OfflineBanner(
                status = state.syncStatus,
                onRetry = { vm.dispatch(TaskIntent.GoOnline) },
            )

            ConflictResolutionDialog(
                conflicts = state.conflicts,
                onDecision = { conflict, decision ->
                    vm.dispatch(
                        TaskIntent.ResolveConflict(conflict.entityId, decision),
                    )
                },
                onDismiss = {
                    state.conflicts.firstOrNull()?.let {
                        vm.dispatch(
                            TaskIntent.ResolveConflict(it.entityId, ConflictDecision.KeepLocal),
                        )
                    }
                },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("New task") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        if (draft.isNotBlank()) {
                            vm.dispatch(TaskIntent.AddTask(draft.trim()))
                            draft = ""
                        }
                    },
                ) {
                    Text("Add")
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.dispatch(TaskIntent.Refresh) }) { Text("Sync now") }
                TextButton(onClick = { vm.dispatch(TaskIntent.GoOffline) }) { Text("Go offline") }
                TextButton(onClick = { vm.dispatch(TaskIntent.SimulateConflict) }) {
                    Text("Conflict")
                }
                TextButton(
                    onClick = {
                        navigator.navigate(
                            AppRoute.CreateTask,
                            RouteMetadata(presentation = PresentationStyle.BottomSheet),
                        )
                    },
                ) {
                    Text("Sheet")
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.tasks, key = { it.id }) { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navigator.navigate(AppRoute.TaskDetail(task.id))
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = task.done,
                            onCheckedChange = { vm.dispatch(TaskIntent.ToggleDone(task.id)) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                textDecoration = if (task.done) {
                                    TextDecoration.LineThrough
                                } else {
                                    null
                                },
                            )
                            val badge = when {
                                task.inConflict -> "conflict"
                                task.pendingSync -> "pending sync (outbox)"
                                else -> null
                            }
                            if (badge != null) {
                                Text(
                                    badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (task.inConflict) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.tertiary
                                    },
                                )
                            }
                        }
                        TextButton(onClick = { vm.dispatch(TaskIntent.DeleteTask(task.id)) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
