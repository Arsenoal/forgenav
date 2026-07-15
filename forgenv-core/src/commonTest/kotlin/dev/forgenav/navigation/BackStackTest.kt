package dev.forgenav.navigation

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
private sealed interface TestRoute : Route {
    @Serializable
    data object Home : TestRoute

    @Serializable
    data class Detail(val id: String) : TestRoute

    @Serializable
    data object Settings : TestRoute

    @Serializable
    data object Feed : TestRoute
}

class BackStackTest {

    @Test
    fun pushAndPop() {
        val stack = BackStack("root", TestRoute.Home)
        assertEquals(1, stack.snapshot.value.size)
        assertFalse(stack.canPop)

        stack.push(TestRoute.Detail("1"))
        assertEquals(2, stack.snapshot.value.size)
        assertTrue(stack.canPop)
        assertEquals("Detail", stack.current?.route?.routeKey)

        assertTrue(stack.pop())
        assertEquals(TestRoute.Home, stack.current?.route)
        assertFalse(stack.pop())
    }

    @Test
    fun replaceTop() {
        val stack = BackStack("root", TestRoute.Home)
        stack.push(TestRoute.Detail("1"))
        stack.replace(TestRoute.Detail("2"))
        assertEquals(2, stack.snapshot.value.size)
        assertEquals(TestRoute.Detail("2"), stack.current?.route)
    }

    @Test
    fun clearBackStackMetadata() {
        val stack = BackStack("root", TestRoute.Home)
        stack.push(TestRoute.Detail("1"))
        stack.push(TestRoute.Settings, RouteMetadata(clearBackStack = true))
        assertEquals(1, stack.snapshot.value.size)
        assertEquals(TestRoute.Settings, stack.current?.route)
    }

    @Test
    fun popToRouteKey() {
        val stack = BackStack("root", TestRoute.Home)
        stack.push(TestRoute.Detail("1"))
        stack.push(TestRoute.Settings)
        val removed = stack.popTo("Detail", inclusive = false)
        assertTrue(removed >= 1)
        assertEquals("Detail", stack.current?.route?.routeKey)
    }

    @Test
    fun singleTopDoesNotDuplicateEqualRoute() {
        val stack = BackStack("root", TestRoute.Home)
        stack.push(TestRoute.Detail("1"), RouteMetadata(singleTop = true))
        stack.push(TestRoute.Detail("1"), RouteMetadata(singleTop = true))
        assertEquals(2, stack.snapshot.value.size)
    }

    @Test
    fun applyResetIsSingleEmission() = runTest {
        val stack = BackStack("root", TestRoute.Home)
        stack.push(TestRoute.Detail("1"))

        stack.snapshot.test {
            assertEquals(2, awaitItem().size) // current value

            val result = stack.apply(
                BackStackOp.Reset(
                    listOf(TestRoute.Home, TestRoute.Detail("x"), TestRoute.Settings),
                ),
            )
            assertTrue(result.didChange)
            assertEquals(3, result.newEntries.size)

            val next = awaitItem()
            assertEquals(
                listOf("Home", "Detail", "Settings"),
                next.entries.map { it.route.routeKey },
            )
            expectNoEvents()
        }
    }

    @Test
    fun applyPopCountIsSingleEmission() = runTest {
        val stack = BackStack("root", TestRoute.Home)
        stack.push(TestRoute.Detail("1"))
        stack.push(TestRoute.Settings)
        stack.push(TestRoute.Feed)

        stack.snapshot.test {
            assertEquals(4, awaitItem().size)

            val result = stack.apply(BackStackOp.Pop(2))
            assertTrue(result.didChange)
            assertEquals(2, result.removed.size)

            val next = awaitItem()
            assertEquals(TestRoute.Detail("1"), next.current?.route)
            assertEquals(2, next.size)
            expectNoEvents()
        }
    }

    @Test
    fun applyPopUpToPushAndTrimSingleEmission() = runTest {
        val stack = BackStack("root", TestRoute.Home)
        stack.push(TestRoute.Detail("1"))
        stack.push(TestRoute.Settings)

        stack.snapshot.test {
            assertEquals(3, awaitItem().size)

            val result = stack.apply(
                BackStackOp.PopTo("Home", inclusive = false),
                BackStackOp.Push(TestRoute.Feed),
                BackStackOp.TrimToMaxSize(64),
            )
            assertTrue(result.didChange)
            assertEquals(
                listOf("Home", "Feed"),
                result.newEntries.map { it.route.routeKey },
            )

            val next = awaitItem()
            assertEquals(listOf("Home", "Feed"), next.entries.map { it.route.routeKey })
            expectNoEvents()
        }
    }

    @Test
    fun applyNoOpDoesNotEmit() = runTest {
        val stack = BackStack("root", TestRoute.Home)
        stack.snapshot.test {
            assertEquals(1, awaitItem().size)
            val result = stack.apply(BackStackOp.Pop(1))
            assertFalse(result.didChange)
            expectNoEvents()
        }
    }

    @Test
    fun trimKeepsRootAndNewest() {
        val stack = BackStack("root", TestRoute.Home)
        stack.push(TestRoute.Detail("1"))
        stack.push(TestRoute.Detail("2"))
        stack.push(TestRoute.Settings)
        stack.apply(BackStackOp.TrimToMaxSize(3))
        assertEquals(3, stack.snapshot.value.size)
        assertEquals(TestRoute.Home, stack.snapshot.value.entries.first().route)
        assertEquals(TestRoute.Settings, stack.current?.route)
        // Middle Detail("1") dropped; root + last 2
        assertEquals(
            listOf("Home", "Detail", "Settings"),
            stack.snapshot.value.entries.map { it.route.routeKey },
        )
        assertEquals(TestRoute.Detail("2"), stack.snapshot.value.entries[1].route)
    }
}
