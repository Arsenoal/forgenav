package dev.forgenav.navigation

import app.cash.turbine.test
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
private sealed interface Nav3Route : Route {
    @Serializable data object Home : Nav3Route
    @Serializable data object Feed : Nav3Route
    @Serializable data object Profile : Nav3Route
    @Serializable data class Detail(val id: String) : Nav3Route
    @Serializable data object Login : Nav3Route
    @Serializable data object Settings : Nav3Route
    @Serializable data object Picker : Nav3Route
}

class ForgeNavigatorNav3Test {

    private fun codec() = RouteCodec()
        .register("Nav3Route", Nav3Route.serializer()) { it is Nav3Route }

    @Test
    fun tabsHaveIndependentStacks() {
        val tabs = listOf(
            TabSpec("home", Nav3Route.Home, "Home"),
            TabSpec("profile", Nav3Route.Profile, "Profile"),
        )
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph("root", Nav3Route.Home),
            tabs = tabs,
            routeCodec = codec(),
        )
        nav.onStart()
        assertEquals("home", nav.selectedTabId.value)
        nav.navigate(Nav3Route.Detail("1"))
        assertEquals(Nav3Route.Detail("1"), nav.currentEntry?.route)

        nav.selectTab("profile")
        assertEquals(Nav3Route.Profile, nav.currentEntry?.route)
        nav.navigate(Nav3Route.Settings)
        assertEquals(Nav3Route.Settings, nav.currentEntry?.route)

        nav.selectTab("home")
        assertEquals(Nav3Route.Detail("1"), nav.currentEntry?.route)

        val saved = nav.saveState()
        assertNotNull(saved)
        assertEquals("home", saved.selectedTabId)

        val restored = DefaultForgeNavigator(
            rootGraph = NavGraph("root", Nav3Route.Home),
            tabs = tabs,
            routeCodec = codec(),
        )
        assertTrue(restored.restoreState(saved))
        assertEquals("home", restored.selectedTabId.value)
        assertEquals(Nav3Route.Detail("1"), restored.currentEntry?.route)
        restored.selectTab("profile")
        assertEquals(Nav3Route.Settings, restored.currentEntry?.route)
    }

    @Test
    fun popUpToAndSetBackStack() {
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph("root", Nav3Route.Home),
        )
        nav.navigate(Nav3Route.Detail("a"))
        nav.navigate(Nav3Route.Settings)
        nav.navigate(
            Nav3Route.Feed,
            NavOptions(popUpToRouteKey = "Home", popUpToInclusive = false),
        )
        assertEquals(
            listOf("Home", "Feed"),
            nav.backStack.value.entries.map { it.route.routeKey },
        )

        nav.setBackStack(listOf(Nav3Route.Home, Nav3Route.Detail("x"), Nav3Route.Settings))
        assertEquals(3, nav.backStack.value.size)
        assertEquals(Nav3Route.Settings, nav.currentEntry?.route)
    }

    @Test
    fun interceptorsCancelAndRedirect() {
        val cancelInterceptor = NavigationInterceptor { req ->
            if (req.route is Nav3Route.Settings) InterceptResult.Cancel
            else InterceptResult.Proceed
        }
        val redirectInterceptor = NavigationInterceptor { req ->
            if (req.route is Nav3Route.Profile) {
                InterceptResult.Redirect(Nav3Route.Login)
            } else {
                InterceptResult.Proceed
            }
        }
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph("root", Nav3Route.Home),
            interceptors = listOf(cancelInterceptor, redirectInterceptor),
        )
        nav.navigate(Nav3Route.Settings)
        assertEquals(Nav3Route.Home, nav.currentEntry?.route)

        nav.navigate(Nav3Route.Profile)
        assertEquals(Nav3Route.Login, nav.currentEntry?.route)
    }

    @Test
    fun conditionalStartRoute() {
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph("root", Nav3Route.Home),
            startRouteProvider = StartRouteProvider { Nav3Route.Login },
        )
        assertEquals(Nav3Route.Login, nav.currentEntry?.route)
    }

    @Test
    fun navigateForResultOk() = runTest {
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph("root", Nav3Route.Home),
        )
        val deferred = backgroundScope.async {
            nav.navigateForResult(Nav3Route.Picker)
        }
        kotlinx.coroutines.yield()
        assertEquals(Nav3Route.Picker, nav.currentEntry?.route)
        nav.setResult("picked-value")
        nav.popBackStack()
        val result = deferred.await()
        assertIs<NavResult.Ok>(result)
        assertEquals("picked-value", result.value)
        assertEquals(Nav3Route.Home, nav.currentEntry?.route)
    }

    @Test
    fun navigateForResultCancelled() = runTest {
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph("root", Nav3Route.Home),
        )
        val deferred = backgroundScope.async {
            nav.navigateForResult(Nav3Route.Picker)
        }
        kotlinx.coroutines.yield()
        nav.popBackStack()
        val result = deferred.await()
        assertIs<NavResult.Cancelled>(result)
    }

    @Test
    fun deepLinkStackAndTab() {
        val tabs = listOf(
            TabSpec("home", Nav3Route.Home, "Home"),
            TabSpec("feed", Nav3Route.Feed, "Feed"),
        )
        val graph = NavGraph("root", Nav3Route.Home)
        val parser = DeepLinkParser(graph)
            .register(
                pattern = DeepLinkPattern(
                    pattern = "app://feed/{id}",
                    serializer = Nav3Route.Detail.serializer(),
                    nestedGraphId = "feed",
                    stackPrefix = listOf(Nav3Route.Feed),
                    priority = 10,
                ),
            )
        val nav = DefaultForgeNavigator(
            rootGraph = graph,
            deepLinkParser = parser,
            tabs = tabs,
        )
        nav.onStart()
        assertTrue(nav.handleDeepLink("app://feed/42"))
        assertEquals("feed", nav.selectedTabId.value)
        assertEquals(
            listOf("Feed", "Detail"),
            nav.backStack.value.entries.map { it.route.routeKey },
        )
        assertEquals(Nav3Route.Detail("42"), nav.currentEntry?.route)
    }

    @Test
    fun deepLinkPriorityPrefersHigher() {
        val graph = NavGraph("root", Nav3Route.Home)
        val parser = DeepLinkParser(graph)
            .register(
                DeepLinkPattern(
                    pattern = "app://x/{id}",
                    serializer = Nav3Route.Detail.serializer(),
                    priority = 1,
                ),
            )
            .register(
                // Same pattern later with higher priority still wins after sort
                DeepLinkPattern(
                    pattern = "app://x/{id}",
                    serializer = Nav3Route.Detail.serializer(),
                    priority = 100,
                    stackPrefix = listOf(Nav3Route.Home),
                ),
            )
        val link = parser.parse("app://x/1")
        assertNotNull(link)
        assertEquals(2, link.stackRoutes.size)
    }

    @Test
    fun popCountAndSingleTop() {
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph("root", Nav3Route.Home),
        )
        nav.navigate(Nav3Route.Detail("1"))
        nav.navigate(Nav3Route.Settings)
        nav.navigate(Nav3Route.Feed)
        assertTrue(nav.popBackStack(count = 2))
        assertEquals(Nav3Route.Detail("1"), nav.currentEntry?.route)

        nav.navigate(Nav3Route.Detail("1"), NavOptions(singleTop = true, launchSingleTop = true))
        // singleTop equal route replaces top rather than growing when already on Detail("1")
        assertEquals(2, nav.backStack.value.size)
    }

    @Test
    fun setBackStackSingleSnapshotEmission() = runTest {
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph("root", Nav3Route.Home),
        )
        nav.navigate(Nav3Route.Detail("a"))

        nav.backStack.test {
            assertEquals(2, awaitItem().size)
            nav.setBackStack(listOf(Nav3Route.Home, Nav3Route.Detail("x"), Nav3Route.Settings))
            val next = awaitItem()
            assertEquals(3, next.size)
            assertEquals(Nav3Route.Settings, next.current?.route)
            expectNoEvents()
        }
    }

    @Test
    fun popCountSingleSnapshotAndEvent() = runTest {
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph("root", Nav3Route.Home),
        )
        nav.navigate(Nav3Route.Detail("1"))
        nav.navigate(Nav3Route.Settings)
        nav.navigate(Nav3Route.Feed)

        nav.events.test {
            nav.backStack.test {
                assertEquals(4, awaitItem().size)
                assertTrue(nav.popBackStack(count = 2))
                assertEquals(Nav3Route.Detail("1"), awaitItem().current?.route)
                expectNoEvents()
            }
            val event = awaitItem()
            assertIs<NavEvent.Popped>(event)
            assertEquals(Nav3Route.Feed, event.from.route)
            assertEquals(Nav3Route.Detail("1"), event.to?.route)
            expectNoEvents()
        }
    }

    @Test
    fun popUpToNavigateSingleEmission() = runTest {
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph("root", Nav3Route.Home),
        )
        nav.navigate(Nav3Route.Detail("a"))
        nav.navigate(Nav3Route.Settings)

        nav.backStack.test {
            assertEquals(3, awaitItem().size)
            nav.navigate(
                Nav3Route.Feed,
                NavOptions(popUpToRouteKey = "Home", popUpToInclusive = false),
            )
            val next = awaitItem()
            assertEquals(listOf("Home", "Feed"), next.entries.map { it.route.routeKey })
            // Must not observe intermediate post-popUpTo stack (Home only).
            expectNoEvents()
        }
    }
}
