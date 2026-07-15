package dev.forgenav.testing

import dev.forgenav.navigation.Route
import dev.forgenav.navigation.TabSpec
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private sealed interface TRoute : Route {
    @Serializable data object Home : TRoute
    @Serializable data object Settings : TRoute
    @Serializable data class Detail(val id: String) : TRoute
}

class TestNavigatorHelpersTest {

    @Test
    fun assertRouteKeysAndNavigateAll() {
        val nav = testForgeNavigator(TRoute.Home)
        nav.navigateAll(TRoute.Detail("1"), TRoute.Settings)
        nav.assertRouteKeys("Home", "Detail", "Settings")
        nav.assertCurrent(TRoute.Settings)
    }

    @Test
    fun tabsHelpers() {
        val nav = testForgeNavigator(
            startRoute = TRoute.Home,
            tabs = listOf(
                TabSpec("home", TRoute.Home),
                TabSpec("settings", TRoute.Settings),
            ),
        )
        nav.navigate(TRoute.Detail("a"))
        nav.selectTab("settings")
        nav.assertSelectedTab("settings")
        nav.assertTabRouteKeys("settings", "Settings")
        nav.selectTab("home")
        nav.assertTabRouteKeys("home", "Home", "Detail")
    }

    @Test
    fun resultHelpers() = runTest {
        val nav = testForgeNavigator(TRoute.Home)
        val deferred = backgroundScope.async {
            nav.navigateForResult(TRoute.Detail("pick"))
        }
        kotlinx.coroutines.yield()
        nav.deliverResultAndPop("ok")
        val result = deferred.await()
        assertEquals("ok", result.requireOk<String>())
    }
}
