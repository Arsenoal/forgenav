package dev.forgenav.syncforge

import dev.forgenav.sync.ConflictDecision
import dev.forgenav.sync.SyncStatus
import dev.syncforge.model.SyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalSyncForgeLoopTest {

    @Test
    fun enqueue_then_sync_clearsOutboxAndMarksSynced() = runTest {
        val loop = ForgeNavSync.localLoop(backgroundScope)
        loop.seedDefaults()
        advanceUntilIdle()

        loop.addTask("Integration task")
        advanceUntilIdle()

        assertTrue(loop.outbox.countAwaitingPush() >= 1)

        loop.syncNow()
        advanceUntilIdle()

        assertEquals(0, loop.outbox.countAwaitingPush())
        val task = loop.taskStore.snapshotAll().first { it.title == "Integration task" }
        assertEquals(SyncState.SYNCED, task.syncState)
    }

    @Test
    fun offline_blocksSync_andKeepsOutbox() = runTest {
        val loop = ForgeNavSync.localLoop(backgroundScope)
        loop.seedDefaults()
        loop.addTask("Queued offline")
        advanceUntilIdle()

        val pendingBefore = loop.outbox.countAwaitingPush()
        assertTrue(pendingBefore >= 1)

        loop.setOnline(false)
        loop.syncNow() // must not flush outbox while offline
        advanceUntilIdle()

        assertEquals(false, loop.network.isOnline)
        assertEquals(pendingBefore, loop.outbox.countAwaitingPush())

        // Reconnect + sync drains outbox (real loop)
        loop.setOnline(true)
        loop.syncNow()
        advanceUntilIdle()
        assertEquals(0, loop.outbox.countAwaitingPush())
    }

    @Test
    fun simulateConflict_recordsOpenConflict() = runTest {
        val loop = ForgeNavSync.localLoop(backgroundScope)
        loop.seedDefaults()
        advanceUntilIdle()

        loop.simulateConflict("seed-0")
        advanceUntilIdle()

        val open = loop.conflictStore.countOpen()
        assertTrue(open >= 1, "expected open conflicts in store, got $open")

        val conflicts = loop.facade.conflicts.first { it.isNotEmpty() }
        assertTrue(conflicts.isNotEmpty())

        loop.facade.resolveConflict(conflicts.first(), ConflictDecision.KeepLocal)
        advanceUntilIdle()

        assertEquals(0, loop.conflictStore.countOpen())
    }
}
