package dev.forgenav.navigation

import dev.forgenav.util.randomId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Result delivered when a destination pops after [ForgeNavigator.navigateForResult].
 */
sealed interface NavResult {
    data class Ok(val value: Any?) : NavResult
    data object Cancelled : NavResult
}

/**
 * Coordinates navigate-for-result across the backstack.
 */
class NavResultHub {
    private val mutex = Mutex()
    private val deferred = mutableMapOf<String, CompletableDeferred<NavResult>>()
    private val _results = MutableSharedFlow<Pair<String, NavResult>>(extraBufferCapacity = 16)

    fun createRequestId(): String = randomId()

    suspend fun register(requestId: String): CompletableDeferred<NavResult> = mutex.withLock {
        val d = CompletableDeferred<NavResult>()
        deferred[requestId] = d
        d
    }

    fun registerBlocking(requestId: String): CompletableDeferred<NavResult> {
        val d = CompletableDeferred<NavResult>()
        deferred[requestId] = d
        return d
    }

    /**
     * @return true if a pending request was completed.
     */
    fun complete(requestId: String, result: NavResult): Boolean {
        val d = deferred.remove(requestId) ?: return false
        d.complete(result)
        _results.tryEmit(requestId to result)
        return true
    }

    /**
     * Completes with [NavResult.Cancelled] only if still pending.
     * @return true if a pending request was cancelled.
     */
    fun cancel(requestId: String): Boolean =
        complete(requestId, NavResult.Cancelled)

    fun cancelAll() {
        val ids = deferred.keys.toList()
        ids.forEach { cancel(it) }
    }

    fun observe(requestId: String): Flow<NavResult> =
        _results.filter { it.first == requestId }.map { it.second }.take(1)

    fun pendingRequestIdOnEntry(entry: NavEntry?): String? =
        entry?.metadata?.extras?.get(NavOptions.EXTRA_RESULT_REQUEST_ID)
}
