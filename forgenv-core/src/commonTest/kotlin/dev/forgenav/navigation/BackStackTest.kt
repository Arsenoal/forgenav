package dev.forgenav.navigation

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
}
