package dev.forgenav.navigation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Parsed deep link ready for [ForgeNavigator.handleDeepLink].
 */
data class DeepLink(
    val uri: String,
    val route: Route,
    val presentation: PresentationStyle = PresentationStyle.Screen,
    val clearBackStack: Boolean = false,
    val singleTop: Boolean = true,
    val nestedGraphId: String? = null,
    val extras: Map<String, String> = emptyMap(),
)

/**
 * Pattern registered against a serializable [Route] type.
 *
 * Path templates use `{arg}` placeholders matching property names on the route data class.
 * Query parameters map to optional route properties.
 *
 * Example: `app://tasks/{id}` → `TaskDetail(id = "…")`
 */
data class DeepLinkPattern<T : Route>(
    val pattern: String,
    val serializer: KSerializer<T>,
    val presentation: PresentationStyle = PresentationStyle.Screen,
    val clearBackStack: Boolean = false,
    val nestedGraphId: String? = null,
)

/**
 * Parses URI strings into [DeepLink]s using registered [DeepLinkPattern]s and
 * kotlinx.serialization for argument materialization.
 *
 * Design notes (v0.3):
 * - We deliberately avoid classpath scanning; apps register patterns explicitly.
 * - Argument decoding prefers path segments, then query string, then defaults from JSON.
 * - Unknown patterns return null so hosts can fall through to platform handlers.
 */
class DeepLinkParser(
    private val graph: NavGraph,
    private val json: Json = defaultJson,
) {
    private val patterns = mutableListOf<CompiledPattern>()

    fun <T : Route> register(pattern: DeepLinkPattern<T>): DeepLinkParser {
        patterns += CompiledPattern.compile(pattern)
        return this
    }

    fun <T : Route> register(
        pattern: String,
        serializer: KSerializer<T>,
        presentation: PresentationStyle = PresentationStyle.Screen,
        clearBackStack: Boolean = false,
        nestedGraphId: String? = null,
    ): DeepLinkParser = register(
        DeepLinkPattern(
            pattern = pattern,
            serializer = serializer,
            presentation = presentation,
            clearBackStack = clearBackStack,
            nestedGraphId = nestedGraphId,
        ),
    )

    fun parse(uri: String): DeepLink? {
        val normalized = uri.trim()
        for (compiled in patterns) {
            val match = compiled.match(normalized) ?: continue
            val route = runCatching {
                json.decodeFromString(compiled.serializer, match.toJsonObject())
            }.getOrNull() ?: continue
            return DeepLink(
                uri = normalized,
                route = route,
                presentation = compiled.presentation,
                clearBackStack = compiled.clearBackStack,
                nestedGraphId = compiled.nestedGraphId,
                extras = match.query.filterKeys { it !in match.pathArgs },
            )
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    fun buildUri(route: Route, basePattern: String): String? {
        val compiled = patterns.firstOrNull { it.pattern == basePattern } ?: return null
        val ser = compiled.serializer as KSerializer<Route>
        val tree = json.encodeToJsonElement(ser, route)
        val obj = tree as? JsonObject ?: return null
        var result = basePattern
        val pathParamRegex = Regex("""\{([a-zA-Z_][a-zA-Z0-9_]*)\}""")
        pathParamRegex.findAll(basePattern).forEach { m ->
            val key = m.groupValues[1]
            val value = obj[key]?.toString()?.trim('"') ?: return null
            result = result.replace("{${key}}", value)
        }
        return result
    }

    companion object {
        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }
}

private data class MatchResult(
    val pathArgs: Map<String, String>,
    val query: Map<String, String>,
) {
    fun toJsonObject(): String {
        val all = pathArgs + query
        val body = all.entries.joinToString(",") { (k, v) ->
            val encoded = if (v.toBooleanStrictOrNull() != null || v.toLongOrNull() != null || v.toDoubleOrNull() != null) {
                v
            } else {
                "\"${v.replace("\"", "\\\"")}\""
            }
            "\"$k\":$encoded"
        }
        return "{$body}"
    }
}

private class CompiledPattern(
    val pattern: String,
    val serializer: KSerializer<out Route>,
    val presentation: PresentationStyle,
    val clearBackStack: Boolean,
    val nestedGraphId: String?,
    private val regex: Regex,
    private val groupNames: List<String>,
) {
    fun match(uri: String): MatchResult? {
        val (path, queryString) = splitUri(uri)
        val m = regex.matchEntire(path) ?: return null
        val pathArgs = groupNames.mapIndexed { index, name ->
            name to (m.groups[index + 1]?.value ?: "")
        }.toMap()
        val query = parseQuery(queryString)
        return MatchResult(pathArgs, query)
    }

    companion object {
        fun <T : Route> compile(pattern: DeepLinkPattern<T>): CompiledPattern {
            val names = mutableListOf<String>()
            val regexBody = buildString {
                append("^")
                var i = 0
                val raw = pattern.pattern
                while (i < raw.length) {
                    when {
                        raw[i] == '{' -> {
                            val end = raw.indexOf('}', i)
                            require(end > i) { "Unclosed { in pattern: ${pattern.pattern}" }
                            val name = raw.substring(i + 1, end)
                            names += name
                            append("([^/?#]+)")
                            i = end + 1
                        }
                        raw[i] in listOf('.', '+', '*', '?', '^', '$', '(', ')', '[', ']', '|', '\\') -> {
                            append('\\')
                            append(raw[i])
                            i++
                        }
                        else -> {
                            append(raw[i])
                            i++
                        }
                    }
                }
                append("$")
            }
            return CompiledPattern(
                pattern = pattern.pattern,
                serializer = pattern.serializer,
                presentation = pattern.presentation,
                clearBackStack = pattern.clearBackStack,
                nestedGraphId = pattern.nestedGraphId,
                regex = Regex(regexBody),
                groupNames = names,
            )
        }
    }
}

private fun splitUri(uri: String): Pair<String, String?> {
    val q = uri.indexOf('?')
    return if (q >= 0) uri.substring(0, q) to uri.substring(q + 1) else uri to null
}

private fun parseQuery(query: String?): Map<String, String> {
    if (query.isNullOrBlank()) return emptyMap()
    return query.split('&')
        .mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val eq = part.indexOf('=')
            if (eq < 0) part to ""
            else part.substring(0, eq) to decode(part.substring(eq + 1))
        }
        .toMap()
}

private fun decode(value: String): String =
    value.replace("+", " ")
        .replace(Regex("%([0-9A-Fa-f]{2})")) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }
