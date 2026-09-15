package com.manuel.mvp.session

import java.time.Clock
import java.time.Duration
import java.time.Instant

/** A single question+answer turn held by [SessionMemory]. */
data class Exchange(val question: String, val answer: String)

/**
 * In-memory rolling window of the last [maxExchanges] conversational exchanges (FR-010). Holds no
 * state outside process memory; the window is cleared explicitly via [clear] or lazily once
 * [inactivityTimeout] or more has elapsed since the last recorded exchange -- checked on every
 * [record]/[currentExchanges] call, with no background timer or thread.
 */
class SessionMemory(
    private val clock: Clock,
    private val maxExchanges: Int = 5,
    private val inactivityTimeout: Duration = Duration.ofMinutes(5),
) {
    private val exchanges = mutableListOf<Exchange>()
    private var lastActivityAt: Instant? = null

    fun record(question: String, answer: String) {
        clearIfTimedOut()
        exchanges.add(Exchange(question, answer))
        if (exchanges.size > maxExchanges) {
            exchanges.removeAt(0)
        }
        lastActivityAt = clock.instant()
    }

    fun currentExchanges(): List<Exchange> {
        clearIfTimedOut()
        return exchanges.toList()
    }

    fun clear() {
        exchanges.clear()
        lastActivityAt = null
    }

    private fun clearIfTimedOut() {
        val lastActivity = lastActivityAt ?: return
        val elapsed = Duration.between(lastActivity, clock.instant())
        if (elapsed >= inactivityTimeout) {
            clear()
        }
    }
}
