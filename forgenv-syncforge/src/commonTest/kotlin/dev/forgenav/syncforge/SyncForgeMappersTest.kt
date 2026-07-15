package dev.forgenav.syncforge

import dev.forgenav.sync.SyncStatus
import dev.forgenav.syncforge.mapper.SyncForgeMappers
import dev.syncforge.model.SyncStatus as SfSyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SyncForgeMappersTest {

    @Test
    fun mapsIdleAndLastSyncedToSynced() {
        assertEquals(SyncStatus.Synced, SyncForgeMappers.toForgeStatus(SfSyncStatus.Idle))
        assertEquals(
            SyncStatus.Synced,
            SyncForgeMappers.toForgeStatus(SfSyncStatus.LastSynced(1L)),
        )
    }

    @Test
    fun openConflictsElevateStatus() {
        val status = SyncForgeMappers.toForgeStatus(
            SfSyncStatus.Pending(outboxCount = 2),
            openConflictCount = 1,
        )
        assertIs<SyncStatus.Conflict>(status)
        assertEquals(1, status.conflictCount)
    }

    @Test
    fun mapsOffline() {
        val status = SyncForgeMappers.toForgeStatus(SfSyncStatus.Offline(outboxCount = 3))
        assertIs<SyncStatus.Offline>(status)
        assertEquals(3, status.pendingCount)
    }

    @Test
    fun networkOfflineElevatesEvenWhenEngineIdle() {
        val status = SyncForgeMappers.toForgeStatus(
            SfSyncStatus.Idle,
            networkOnline = false,
        )
        assertIs<SyncStatus.Offline>(status)
    }
}
