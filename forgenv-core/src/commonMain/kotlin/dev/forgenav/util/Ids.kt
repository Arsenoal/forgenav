package dev.forgenav.util

import kotlin.random.Random

/**
 * Cross-platform unique id without experimental UUID APIs.
 */
fun randomId(): String {
    val a = Random.nextLong().toULong().toString(16)
    val b = Random.nextLong().toULong().toString(16)
    return "$a-$b"
}
