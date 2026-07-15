package dev.forgenav.navigation

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private sealed interface SaveRoute : Route {
    @Serializable
    data object Home : SaveRoute

    @Serializable
    data class Detail(val id: String) : SaveRoute

    @Serializable
    data object Settings : SaveRoute
}

class SavedNavStateTest {

    private fun codec() = RouteCodec()
        .register("SaveRoute", SaveRoute.serializer()) { it is SaveRoute }

    @Test
    fun routeCodecRoundTrip() {
        val codec = codec()
        val route = SaveRoute.Detail("42")
        val payload = codec.encode(route)
        assertEquals("SaveRoute", payload.family)
        assertEquals(route, codec.decode(payload))
    }

    @Test
    fun saveAndRestoreRootStack() {
        val codec = codec()
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph(id = "root", startRoute = SaveRoute.Home),
            routeCodec = codec,
        )
        nav.navigate(SaveRoute.Detail("a"))
        nav.navigate(SaveRoute.Settings)

        val saved = nav.saveState()
        assertNotNull(saved)
        assertEquals(3, saved.root.entries.size)

        val restored = DefaultForgeNavigator(
            rootGraph = NavGraph(id = "root", startRoute = SaveRoute.Home),
            routeCodec = codec,
        )
        assertTrue(restored.restoreState(saved))
        assertEquals(3, restored.backStack.value.size)
        assertEquals(SaveRoute.Settings, restored.currentEntry?.route)
        assertEquals(saved.root.entries.last().id, restored.currentEntry?.id)
    }

    @Test
    fun saveWithoutCodecReturnsNull() {
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph(id = "root", startRoute = SaveRoute.Home),
        )
        assertNull(nav.saveState())
        assertFalse(
            nav.restoreState(
                SavedNavigatorState(
                    root = SavedBackStackState(
                        graphId = "root",
                        entries = listOf(
                            SavedNavEntry(
                                id = "x",
                                route = SavedRoutePayload("SaveRoute", "{}"),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun stringRoundTripViaSerializer() {
        val codec = codec()
        val serializer = NavStateSerializer(codec)
        val nav = ForgeNavigator(
            startRoute = SaveRoute.Home,
            routeCodec = codec,
        )
        nav.navigate(SaveRoute.Detail("z"))
        val state = nav.saveState()!!
        val json = serializer.encodeToString(state)
        val parsed = serializer.decodeFromString(json)

        val other = ForgeNavigator(startRoute = SaveRoute.Home, routeCodec = codec)
        assertTrue(other.restoreState(parsed))
        assertEquals(SaveRoute.Detail("z"), other.currentEntry?.route)
    }

    @Test
    fun nestedStacksRestored() {
        val codec = codec()
        val children = mapOf(
            "tabs" to NavGraph(id = "tabs", startRoute = SaveRoute.Home),
        )
        val nav = DefaultForgeNavigator(
            rootGraph = NavGraph(id = "root", startRoute = SaveRoute.Home, children = children),
            routeCodec = codec,
        )
        nav.navigateNested("tabs", SaveRoute.Detail("tab-1"))
        val saved = nav.saveState()!!
        assertTrue(saved.nested.containsKey("tabs"))

        val restored = DefaultForgeNavigator(
            rootGraph = NavGraph(id = "root", startRoute = SaveRoute.Home, children = children),
            routeCodec = codec,
        )
        assertTrue(restored.restoreState(saved))
        val nested = restored.nestedBackStack("tabs")?.value
        assertNotNull(nested)
        assertEquals(SaveRoute.Detail("tab-1"), nested.current?.route)
    }

    @Test
    fun pendingDeepLinksPreservedUntilStart() {
        val codec = codec()
        val graph = NavGraph(id = "root", startRoute = SaveRoute.Home)
        val parser = DeepLinkParser(graph)
            .register("app://d/{id}", SaveRoute.Detail.serializer())
        val nav = DefaultForgeNavigator(
            rootGraph = graph,
            deepLinkParser = parser,
            routeCodec = codec,
            config = NavigatorConfig(deferDeepLinksUntilStarted = true),
        )
        // not started yet
        assertTrue(nav.handleDeepLink("app://d/99"))
        val saved = nav.saveState()!!
        assertEquals(listOf("app://d/99"), saved.pendingDeepLinks)

        val restored = DefaultForgeNavigator(
            rootGraph = graph,
            deepLinkParser = parser,
            routeCodec = codec,
            config = NavigatorConfig(deferDeepLinksUntilStarted = true),
        )
        assertTrue(restored.restoreState(saved))
        restored.onStart()
        assertEquals(SaveRoute.Detail("99"), restored.currentEntry?.route)
    }
}
