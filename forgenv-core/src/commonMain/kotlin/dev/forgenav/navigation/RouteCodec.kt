package dev.forgenav.navigation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Encodes and decodes [Route] instances for [SavedNavigatorState].
 *
 * Register each route family (typically a `@Serializable` sealed hierarchy) once:
 * ```
 * val codec = RouteCodec()
 *     .register("AppRoute", AppRoute.serializer()) { it is AppRoute }
 * ```
 */
class RouteCodec(
    private val json: Json = defaultJson,
) {
    private val families = mutableListOf<Family>()

    /**
     * Registers a serializable route family.
     *
     * @param family stable discriminator stored in [SavedRoutePayload.family]
     * @param serializer kotlinx serializer for the family (sealed root or concrete type)
     * @param belongs returns true when [encode] should use this family
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Route> register(
        family: String,
        serializer: KSerializer<T>,
        belongs: (Route) -> Boolean,
    ): RouteCodec {
        require(families.none { it.name == family }) {
            "Route family already registered: $family"
        }
        families += Family(
            name = family,
            belongs = belongs,
            encode = { route ->
                json.encodeToString(serializer, route as T)
            },
            decode = { payload ->
                json.decodeFromString(serializer, payload)
            },
        )
        return this
    }

    fun encode(route: Route): SavedRoutePayload {
        val family = families.firstOrNull { it.belongs(route) }
            ?: error(
                "No RouteCodec family for route ${route::class.simpleName}. " +
                    "Register it with RouteCodec.register(...).",
            )
        return SavedRoutePayload(
            family = family.name,
            payloadJson = family.encode(route),
        )
    }

    fun encodeOrNull(route: Route): SavedRoutePayload? =
        runCatching { encode(route) }.getOrNull()

    fun decode(payload: SavedRoutePayload): Route {
        val family = families.firstOrNull { it.name == payload.family }
            ?: error("Unknown route family '${payload.family}' — not registered on RouteCodec")
        return family.decode(payload.payloadJson)
    }

    fun decodeOrNull(payload: SavedRoutePayload): Route? =
        runCatching { decode(payload) }.getOrNull()

    fun canEncode(route: Route): Boolean = families.any { it.belongs(route) }

    private data class Family(
        val name: String,
        val belongs: (Route) -> Boolean,
        val encode: (Route) -> String,
        val decode: (String) -> Route,
    )

    companion object {
        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            classDiscriminator = "type"
        }
    }
}
