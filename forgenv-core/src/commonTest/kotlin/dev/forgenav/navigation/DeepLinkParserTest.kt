package dev.forgenav.navigation

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Serializable
private sealed interface LinkRoute : Route {
    @Serializable
    data object Home : LinkRoute

    @Serializable
    data class Task(val id: String) : LinkRoute

    @Serializable
    data class Search(val q: String = "", val page: Int = 1) : LinkRoute
}

class DeepLinkParserTest {

    private val graph = NavGraph(id = "root", startRoute = LinkRoute.Home)

    @Test
    fun parsePathArgument() {
        val parser = DeepLinkParser(graph)
            .register("app://tasks/{id}", LinkRoute.Task.serializer())

        val deepLink = parser.parse("app://tasks/abc-123")
        assertNotNull(deepLink)
        assertEquals(LinkRoute.Task("abc-123"), deepLink.route)
    }

    @Test
    fun parseQueryArguments() {
        val parser = DeepLinkParser(graph)
            .register("app://search", LinkRoute.Search.serializer())

        val deepLink = parser.parse("app://search?q=hello&page=2")
        assertNotNull(deepLink)
        assertEquals(LinkRoute.Search(q = "hello", page = 2), deepLink.route)
    }

    @Test
    fun unknownPatternReturnsNull() {
        val parser = DeepLinkParser(graph)
        assertNull(parser.parse("app://unknown"))
    }

    @Test
    fun buildUriRoundTrip() {
        val parser = DeepLinkParser(graph)
            .register("app://tasks/{id}", LinkRoute.Task.serializer())
        val uri = parser.buildUri(LinkRoute.Task("42"), "app://tasks/{id}")
        assertEquals("app://tasks/42", uri)
    }
}
