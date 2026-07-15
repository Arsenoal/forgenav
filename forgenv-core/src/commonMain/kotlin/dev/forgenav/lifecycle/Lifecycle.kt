package dev.forgenav.lifecycle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Minimal lifecycle model shared across Android, iOS, Desktop, and Web.
 * Maps cleanly onto AndroidX Lifecycle, UIKit view controller events, and Compose window focus.
 */
enum class LifecycleState {
    Initialized,
    Created,
    Started,
    Resumed,
    Paused,
    Stopped,
    Destroyed,
}

/**
 * Observer notified on lifecycle transitions.
 */
fun interface LifecycleObserver {
    fun onStateChanged(source: LifecycleOwner, event: LifecycleEvent)
}

enum class LifecycleEvent {
    ON_CREATE,
    ON_START,
    ON_RESUME,
    ON_PAUSE,
    ON_STOP,
    ON_DESTROY,
    ;

    companion object {
        fun upTo(state: LifecycleState): LifecycleEvent? = when (state) {
            LifecycleState.Created -> ON_CREATE
            LifecycleState.Started -> ON_START
            LifecycleState.Resumed -> ON_RESUME
            else -> null
        }

        fun downFrom(state: LifecycleState): LifecycleEvent? = when (state) {
            LifecycleState.Paused -> ON_PAUSE
            LifecycleState.Stopped -> ON_STOP
            LifecycleState.Destroyed -> ON_DESTROY
            else -> null
        }
    }
}

/**
 * Owner of a [Lifecycle] — typically a screen host, Activity, or window.
 */
interface LifecycleOwner {
    val lifecycle: Lifecycle
}

/**
 * Observable lifecycle.
 */
interface Lifecycle {
    val currentState: LifecycleState
    val state: StateFlow<LifecycleState>
    fun addObserver(observer: LifecycleObserver)
    fun removeObserver(observer: LifecycleObserver)
}

/**
 * Mutable lifecycle used by ForgeNav hosts and tests.
 */
class MutableLifecycle : Lifecycle {
    private val _state = MutableStateFlow(LifecycleState.Initialized)
    private val observers = mutableListOf<LifecycleObserver>()
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = this@MutableLifecycle
    }

    override val currentState: LifecycleState get() = _state.value
    override val state: StateFlow<LifecycleState> = _state.asStateFlow()

    override fun addObserver(observer: LifecycleObserver) {
        observers += observer
        // Replay current state if already past Initialized
        when (_state.value) {
            LifecycleState.Created -> observer.onStateChanged(owner, LifecycleEvent.ON_CREATE)
            LifecycleState.Started -> {
                observer.onStateChanged(owner, LifecycleEvent.ON_CREATE)
                observer.onStateChanged(owner, LifecycleEvent.ON_START)
            }
            LifecycleState.Resumed -> {
                observer.onStateChanged(owner, LifecycleEvent.ON_CREATE)
                observer.onStateChanged(owner, LifecycleEvent.ON_START)
                observer.onStateChanged(owner, LifecycleEvent.ON_RESUME)
            }
            else -> Unit
        }
    }

    override fun removeObserver(observer: LifecycleObserver) {
        observers -= observer
    }

    fun handleLifecycleEvent(event: LifecycleEvent) {
        val next = when (event) {
            LifecycleEvent.ON_CREATE -> LifecycleState.Created
            LifecycleEvent.ON_START -> LifecycleState.Started
            LifecycleEvent.ON_RESUME -> LifecycleState.Resumed
            LifecycleEvent.ON_PAUSE -> LifecycleState.Paused
            LifecycleEvent.ON_STOP -> LifecycleState.Stopped
            LifecycleEvent.ON_DESTROY -> LifecycleState.Destroyed
        }
        _state.value = next
        observers.toList().forEach { it.onStateChanged(owner, event) }
    }

    fun moveTo(state: LifecycleState) {
        val order = listOf(
            LifecycleState.Initialized,
            LifecycleState.Created,
            LifecycleState.Started,
            LifecycleState.Resumed,
        )
        val currentIndex = order.indexOf(_state.value).coerceAtLeast(0)
        val targetIndex = order.indexOf(state)
        if (targetIndex >= 0 && targetIndex > currentIndex) {
            for (i in (currentIndex + 1)..targetIndex) {
                LifecycleEvent.upTo(order[i])?.let { handleLifecycleEvent(it) }
            }
        }
    }
}

/**
 * Bridges a [Lifecycle] to [dev.forgenav.navigation.ForgeNavigator] start/stop.
 */
fun dev.forgenav.navigation.ForgeNavigator.bindTo(lifecycle: Lifecycle) {
    lifecycle.addObserver { _, event ->
        when (event) {
            LifecycleEvent.ON_START, LifecycleEvent.ON_RESUME -> onStart()
            LifecycleEvent.ON_STOP -> onStop()
            LifecycleEvent.ON_DESTROY -> dispose()
            else -> Unit
        }
    }
}
