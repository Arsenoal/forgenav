package dev.forgenav.compose.nav

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.forgenav.navigation.ForgeNavigator

/**
 * Converts an Android [Intent] into a URI string for [ForgeNavigator.handleDeepLink].
 *
 * Prefers [Intent.getData]; falls back to `android.intent.extra.REFERRER` string extras
 * named `forgenav.uri` when apps pass links via notifications.
 */
fun Intent.toForgeDeepLinkUri(): String? {
    data?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
    getStringExtra(EXTRA_FORGENAV_URI)?.takeIf { it.isNotBlank() }?.let { return it }
    // App link / VIEW actions with empty data are ignored.
    return null
}

/**
 * Handles the activity's launch / new-intent deep link if present.
 *
 * @return true if a deep link was found and accepted by the navigator.
 */
fun ForgeNavigator.handleForgeDeepLink(intent: Intent?): Boolean {
    val uri = intent?.toForgeDeepLinkUri() ?: return false
    return handleDeepLink(uri)
}

/**
 * Handles [Activity.getIntent] on first composition / resume and optional new intents.
 *
 * Call from your root composable or Activity:
 * ```
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     // after navigator is created:
 *     navigator.handleForgeDeepLink(intent)
 * }
 *
 * override fun onNewIntent(intent: Intent) {
 *     super.onNewIntent(intent)
 *     setIntent(intent)
 *     navigator.handleForgeDeepLink(intent)
 * }
 * ```
 */
fun ForgeNavigator.consumeActivityDeepLink(activity: Activity): Boolean =
    handleForgeDeepLink(activity.intent)

/**
 * Creates a VIEW intent for a ForgeNav deep link URI (e.g. for notifications / shortcuts).
 */
fun forgeDeepLinkIntent(uri: String, packageName: String? = null): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
        packageName?.let { setPackage(it) }
        putExtra(EXTRA_FORGENAV_URI, uri)
    }

const val EXTRA_FORGENAV_URI: String = "forgenav.uri"

/**
 * Optional helper: wire deep links for a [ComponentActivity] once the navigator exists.
 */
fun ComponentActivity.installForgeDeepLinkHandler(
    navigator: ForgeNavigator,
    handleInitial: Boolean = true,
) {
    if (handleInitial) {
        navigator.handleForgeDeepLink(intent)
    }
}
