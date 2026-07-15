package dev.forgenav.navigation

import kotlinx.serialization.Serializable

/**
 * Full navigation options (Nav3 / Navigation-Compose style).
 * Preferred over stuffing every flag into [RouteMetadata] alone.
 */
@Serializable
data class NavOptions(
    val presentation: PresentationStyle = PresentationStyle.Screen,
    val singleTop: Boolean = false,
    val launchSingleTop: Boolean = false,
    val clearBackStack: Boolean = false,
    /** Pop until this [Route.routeKey] before pushing (if non-null). */
    val popUpToRouteKey: String? = null,
    val popUpToInclusive: Boolean = false,
    /** When true with multi-stack tabs, preserve the popped stack for later restore. */
    val saveState: Boolean = false,
    val restoreState: Boolean = false,
    val extras: Map<String, String> = emptyMap(),
    /** Internal: correlates with [NavResultHub] for navigate-for-result. */
    val resultRequestId: String? = null,
) {
    fun toMetadata(): RouteMetadata = RouteMetadata(
        presentation = presentation,
        clearBackStack = clearBackStack,
        singleTop = singleTop,
        launchSingleTop = launchSingleTop,
        extras = extras + buildMap {
            popUpToRouteKey?.let { put(EXTRA_POP_UP_TO, it) }
            if (popUpToInclusive) put(EXTRA_POP_UP_TO_INCLUSIVE, "true")
            if (saveState) put(EXTRA_SAVE_STATE, "true")
            if (restoreState) put(EXTRA_RESTORE_STATE, "true")
            resultRequestId?.let { put(EXTRA_RESULT_REQUEST_ID, it) }
        },
    )

    companion object {
        const val EXTRA_POP_UP_TO = "forgenav.popUpTo"
        const val EXTRA_POP_UP_TO_INCLUSIVE = "forgenav.popUpToInclusive"
        const val EXTRA_SAVE_STATE = "forgenav.saveState"
        const val EXTRA_RESTORE_STATE = "forgenav.restoreState"
        const val EXTRA_RESULT_REQUEST_ID = "forgenav.resultRequestId"

        fun fromMetadata(metadata: RouteMetadata): NavOptions = NavOptions(
            presentation = metadata.presentation,
            singleTop = metadata.singleTop,
            launchSingleTop = metadata.launchSingleTop,
            clearBackStack = metadata.clearBackStack,
            popUpToRouteKey = metadata.extras[EXTRA_POP_UP_TO],
            popUpToInclusive = metadata.extras[EXTRA_POP_UP_TO_INCLUSIVE] == "true",
            saveState = metadata.extras[EXTRA_SAVE_STATE] == "true",
            restoreState = metadata.extras[EXTRA_RESTORE_STATE] == "true",
            extras = metadata.extras.filterKeys {
                it !in setOf(
                    EXTRA_POP_UP_TO,
                    EXTRA_POP_UP_TO_INCLUSIVE,
                    EXTRA_SAVE_STATE,
                    EXTRA_RESTORE_STATE,
                    EXTRA_RESULT_REQUEST_ID,
                )
            },
            resultRequestId = metadata.extras[EXTRA_RESULT_REQUEST_ID],
        )
    }
}

fun NavOptions.toRouteMetadata(): RouteMetadata = toMetadata()
