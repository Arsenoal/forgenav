package dev.forgenav.syncforge.loop

import dev.forgenav.sync.SyncFacade
import dev.forgenav.syncforge.adapter.SyncForgeEngineAdapter
import dev.syncforge.SyncForge
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.conflict.InMemoryConflictStore
import dev.syncforge.conflict.conflictPolicy
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.model.Change
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncState
import dev.syncforge.network.NetworkMonitor
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.RemoteDelta
import dev.syncforge.network.SyncTransport
import dev.syncforge.outbox.InMemoryOutboxRepository
import dev.syncforge.sync.SyncConfig
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * In-process SyncForge stack for samples and integration tests:
 * real [SyncManager] + in-memory outbox/store + loopback transport + controllable network.
 *
 * This is the **real offline-first loop** (enqueue → outbox → push/pull → conflict),
 * without requiring a remote server.
 */
@OptIn(ExperimentalSyncForgeApi::class)
class LocalSyncForgeLoop(
    private val scope: CoroutineScope,
) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val taskStore = InMemoryTaskStore()
    val network = ControllableNetworkMonitor(online = true)
    val transport = LoopbackSyncTransport(json)
    val outbox = InMemoryOutboxRepository().apply { setMaxRetriesForObservation(5) }
    val conflictStore = InMemoryConflictStore()

    private val taskHandler = TaskSyncHandler(taskStore, json)

    val syncManager: SyncManager = SyncForge.create(
        config = SyncConfig(
            entityTypes = setOf(TaskEntity.ENTITY_TYPE),
            enableOptimisticUpdates = true,
            maxRetries = 5,
        ),
        outbox = outbox,
        transport = transport,
        registry = EntityRegistry.of(taskHandler),
        scope = scope,
        networkMonitor = network,
        conflictPolicy = conflictPolicy {
            entity(TaskEntity.ENTITY_TYPE) { deferToUser() }
        },
        conflictStore = conflictStore,
    )

    private val adapter = SyncForgeEngineAdapter(
        scope = scope,
        syncManager = syncManager,
        outboxRepository = outbox,
        conflictStore = conflictStore,
        networkMonitor = network,
        maxRetries = 5,
    )

    val facade: SyncFacade = adapter.asFacade()

    val tasks: Flow<List<TaskEntity>> = taskStore.observeAll()

    suspend fun seedDefaults() {
        if (taskStore.snapshotAll().isNotEmpty()) return
        val now = currentMillis()
        listOf(
            "Ship ForgeNav 1.0",
            "Wire SyncForge outbox",
            "Write deep-link tests",
        ).forEachIndexed { index, title ->
            val entity = TaskEntity(
                id = "seed-$index",
                title = title,
                done = false,
                localVersion = 1,
                updatedAtMillis = now,
                syncState = SyncState.SYNCED,
            )
            taskStore.insert(entity)
            transport.rememberServer(entity)
        }
    }

    suspend fun addTask(title: String): TaskEntity {
        val now = currentMillis()
        val entity = TaskEntity(
            id = randomLoopId(),
            title = title.trim(),
            done = false,
            localVersion = 1,
            updatedAtMillis = now,
            syncState = SyncState.PENDING,
        )
        // Optimistic write + outbox via SyncManager
        syncManager.enqueueChange(Change.create(TaskEntity.ENTITY_TYPE, entity))
        return entity
    }

    suspend fun toggleDone(id: String) {
        val current = taskStore.findById(id) ?: return
        val now = currentMillis()
        val updated = current.copy(
            done = !current.done,
            localVersion = current.localVersion + 1,
            updatedAtMillis = now,
            syncState = SyncState.PENDING,
        )
        syncManager.enqueueChange(Change.update(TaskEntity.ENTITY_TYPE, updated))
    }

    suspend fun deleteTask(id: String) {
        val current = taskStore.findById(id) ?: return
        syncManager.enqueueChange(
            Change.delete<TaskEntity>(
                entityType = TaskEntity.ENTITY_TYPE,
                entityId = id,
                localVersion = current.localVersion,
                updatedAtMillis = currentMillis(),
            ),
        )
    }

    suspend fun syncNow() {
        syncManager.sync()
    }

    fun setOnline(online: Boolean) {
        network.setOnline(online)
        // Nudge observers: SyncManager only refreshes Offline on a sync cycle.
        // Samples call syncNow() after go-offline; network flow drives ForgeNav status mapping.
    }

    /**
     * Forces a user-visible conflict:
     * 1) Ensures a local pending edit exists
     * 2) Injects a divergent remote delta
     * 3) Runs pull so SyncForge [deferToUser] records an open conflict
     */
    suspend fun simulateConflict(entityId: String? = null) {
        network.setOnline(true)
        val target = entityId?.let { taskStore.findById(it) }
            ?: taskStore.snapshotAll().firstOrNull()
            ?: return

        // Local divergent edit (pending) if not already pending
        if (target.syncState != SyncState.PENDING) {
            val localEdit = target.copy(
                title = "${target.title} (local)",
                localVersion = target.localVersion + 1,
                updatedAtMillis = currentMillis(),
                syncState = SyncState.PENDING,
            )
            syncManager.enqueueChange(Change.update(TaskEntity.ENTITY_TYPE, localEdit))
        }

        val remote = target.copy(
            title = "${target.title} (server)",
            localVersion = target.localVersion + 10,
            updatedAtMillis = currentMillis() + 1,
            syncState = SyncState.SYNCED,
        )
        transport.queuePullDelta(
            RemoteDelta(
                entityType = TaskEntity.ENTITY_TYPE,
                entityId = remote.id,
                payloadJson = json.encodeToString(remote),
                serverVersion = remote.localVersion,
                updatedAtMillis = remote.updatedAtMillis,
                isDeleted = false,
            ),
        )
        // Pull only so local pending stays and conflict detector fires
        syncManager.pull()
    }

    companion object {
        private fun currentMillis(): Long = loopNowMillis()

        private fun randomLoopId(): String =
            "task-${kotlin.random.Random.nextLong().toULong().toString(16)}"
    }
}

@Serializable
data class TaskEntity(
    override val id: String,
    val title: String,
    val done: Boolean = false,
    override val localVersion: Long = 0,
    override val updatedAtMillis: Long = 0,
    override val syncState: SyncState = SyncState.SYNCED,
) : SyncedEntity {
    companion object {
        const val ENTITY_TYPE: String = "tasks"
    }
}

class InMemoryTaskStore {
    private val mutex = Mutex()
    private val tasks = MutableStateFlow<List<TaskEntity>>(emptyList())

    fun observeAll(): Flow<List<TaskEntity>> = tasks.asStateFlow()
    fun snapshotAll(): List<TaskEntity> = tasks.value

    suspend fun findById(id: String): TaskEntity? = mutex.withLock {
        tasks.value.firstOrNull { it.id == id }
    }

    suspend fun insert(entity: TaskEntity) = mutex.withLock {
        tasks.value = listOf(entity) + tasks.value.filter { it.id != entity.id }
    }

    suspend fun update(entity: TaskEntity) = mutex.withLock {
        tasks.value = tasks.value.map { if (it.id == entity.id) entity else it }
    }

    suspend fun deleteById(id: String) = mutex.withLock {
        tasks.value = tasks.value.filter { it.id != id }
    }
}

internal class TaskSyncHandler(
    private val store: InMemoryTaskStore,
    private val json: Json,
) : TypedEntitySyncHandler<TaskEntity>() {
    override val entityType: String = TaskEntity.ENTITY_TYPE
    override fun toJson(entity: TaskEntity): String = json.encodeToString(entity)
    override fun fromJson(jsonString: String): TaskEntity = json.decodeFromString(jsonString)
    override suspend fun findById(id: String): TaskEntity? = store.findById(id)
    override suspend fun insert(entity: TaskEntity) = store.insert(entity)
    override suspend fun update(entity: TaskEntity) = store.update(entity)
    override suspend fun deleteById(id: String) = store.deleteById(id)
    override fun withSyncState(entity: TaskEntity, state: SyncState): TaskEntity =
        entity.copy(syncState = state)
}

class ControllableNetworkMonitor(
    online: Boolean = true,
) : NetworkMonitor {
    private val _online = MutableStateFlow(online)
    override val isOnline: Boolean get() = _online.value
    override fun observeOnline(): Flow<Boolean> = _online
    fun setOnline(online: Boolean) {
        _online.value = online
    }
}

/**
 * Loopback transport: acknowledges pushes into an in-memory server map and
 * delivers injected pull deltas (used for conflict simulation).
 */
class LoopbackSyncTransport(
    private val json: Json,
) : SyncTransport {
    private val mutex = Mutex()
    private val server = linkedMapOf<String, Pair<String, Long>>() // entityId -> payload, version
    private val queuedPulls = ArrayDeque<RemoteDelta>()

    suspend fun rememberServer(entity: TaskEntity) = mutex.withLock {
        server[entity.id] = json.encodeToString(entity) to entity.localVersion
    }

    suspend fun queuePullDelta(delta: RemoteDelta) = mutex.withLock {
        queuedPulls.addLast(delta)
    }

    override suspend fun push(entries: List<OutboxEntry>): PushResult = mutex.withLock {
        entries.forEach { entry ->
            if (entry.payloadJson != null) {
                val version = (server[entry.entityId]?.second ?: 0L) + 1
                server[entry.entityId] = entry.payloadJson!! to version
            } else {
                server.remove(entry.entityId)
            }
        }
        PushResult(acknowledgedIds = entries.map { it.id })
    }

    override suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult = mutex.withLock {
        val injected = buildList {
            while (queuedPulls.isNotEmpty() && size < pageSize) {
                add(queuedPulls.removeFirst())
            }
        }
        PullResult(
            deltas = injected,
            serverTimestampMillis = loopNowMillis(),
            hasMore = queuedPulls.isNotEmpty(),
        )
    }
}

/** Monotonic-enough wall clock for demos without expect/actual. */
private var loopClockSeed: Long = 1_720_000_000_000L
private fun loopNowMillis(): Long {
    loopClockSeed += 1
    return loopClockSeed
}
