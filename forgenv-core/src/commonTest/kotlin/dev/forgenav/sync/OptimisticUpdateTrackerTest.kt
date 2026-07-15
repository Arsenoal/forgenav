package dev.forgenav.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OptimisticUpdateTrackerTest {

    @Test
    fun trackCommitRollback() {
        val tracker = OptimisticUpdateTracker<Int>()
        tracker.track(
            OptimisticUpdate(
                id = "1",
                previousState = 0,
                optimisticState = 1,
                createdAtMillis = 0L,
            ),
        )
        assertEquals(1, tracker.size)
        assertEquals(1, tracker.commit("1")?.optimisticState)
        assertEquals(0, tracker.size)
        assertNull(tracker.rollback("missing"))
    }

    @Test
    fun rollbackAllRestoresOrder() {
        val tracker = OptimisticUpdateTracker<String>()
        tracker.track(OptimisticUpdate("a", "root", "a", createdAtMillis = 1))
        tracker.track(OptimisticUpdate("b", "a", "b", createdAtMillis = 2))
        val all = tracker.rollbackAll()
        assertEquals(2, all.size)
        assertEquals("root", all.first().previousState)
        assertEquals(0, tracker.size)
    }
}
