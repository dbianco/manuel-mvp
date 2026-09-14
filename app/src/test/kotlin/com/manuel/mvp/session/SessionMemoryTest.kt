package com.manuel.mvp.session

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests defining the exact behavioral contract that T008's real `SessionMemory` /
 * `Exchange` implementation must satisfy: an in-memory rolling window of the last 5
 * conversational exchanges (question+answer), which clears on an explicit [SessionMemory.clear]
 * call or after 5 minutes of inactivity ("tras 5 minutos sin ninguna instrucción válida").
 *
 * `SessionMemory` and `Exchange` do not exist in the main source set yet -- they are T008's job,
 * not this task's (T007). This file will not compile until T008 adds them; that is expected for
 * this test-first split (T007 defines the contract via tests, T008 implements it), same pattern
 * as T005 -> T006.
 *
 * ### Timeout boundary semantics: `>=`, not `>`
 *
 * The contract (see the T007 brief) is explicit that the window is cleared when
 * `inactivityTimeout` **or more** has elapsed since the last recorded exchange -- i.e. elapsed
 * time is compared against the timeout with `>=`, not `>`. An exchange recorded, then followed by
 * a clock advance of *exactly* 5 minutes, is timed out (see the two "...exactly clears window..."
 * tests below, one observing via `currentExchanges()` and one via `record()`); an advance of
 * anything less (down to 4:59, see "no premature clearing...") must NOT clear. This is the
 * natural reading of the Spanish spec phrase "tras 5 minutos sin ninguna instrucción válida"
 * (after 5 minutes with no valid instruction) -- once the fifth minute has fully elapsed, the
 * session is considered timed out.
 *
 * ### How time is controlled
 *
 * No `Thread.sleep` anywhere. [MutableClock] is a small `java.time.Clock` subclass holding a
 * mutable [Instant] that tests advance synchronously and instantly via [MutableClock.advanceBy].
 * `SessionMemory` is constructed with an instance of it so the lazy timeout check inside
 * `record`/`currentExchanges` (comparing `clock.instant()` against the last-recorded timestamp,
 * per the contract -- no background timer/thread) can be driven deterministically.
 */
class SessionMemoryTest {

    private lateinit var clock: MutableClock
    private lateinit var memory: SessionMemory

    @Before
    fun setUp() {
        clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        memory = SessionMemory(clock = clock)
    }

    /** Scenario 1: window fills up to 5, recorded one at a time, oldest first. */
    @Test
    fun `window fills up to five exchanges in recorded order`() {
        for (i in 1..5) {
            memory.record("question $i", "answer $i")
        }

        val result = memory.currentExchanges()

        assertEquals(5, result.size)
        assertEquals((1..5).map { i -> Exchange("question $i", "answer $i") }, result)
    }

    /** Scenario 2: a 6th exchange evicts the oldest; the newest is last. */
    @Test
    fun `sliding window evicts oldest when a sixth exchange is recorded`() {
        for (i in 1..6) {
            memory.record("question $i", "answer $i")
        }

        val result = memory.currentExchanges()

        assertEquals((2..6).map { i -> Exchange("question $i", "answer $i") }, result)
    }

    /** Scenario 3: clear() empties the window immediately, with zero elapsed time. */
    @Test
    fun `clear empties the window immediately without any elapsed time`() {
        memory.record("q1", "a1")
        memory.record("q2", "a2")

        memory.clear()

        assertEquals(emptyList<Exchange>(), memory.currentExchanges())
    }

    /** Scenario 4a: exactly the 5-minute timeout clears the window, observed via currentExchanges(). */
    @Test
    fun `timeout of five minutes exactly clears window via currentExchanges`() {
        memory.record("q1", "a1")

        clock.advanceBy(Duration.ofMinutes(5))

        assertEquals(emptyList<Exchange>(), memory.currentExchanges())
    }

    /** Scenario 4b: exactly the 5-minute timeout clears the window, observed via record(). */
    @Test
    fun `timeout of five minutes exactly clears window via record`() {
        memory.record("q1", "a1")

        clock.advanceBy(Duration.ofMinutes(5))
        memory.record("q2", "a2")

        assertEquals(listOf(Exchange("q2", "a2")), memory.currentExchanges())
    }

    /** Scenario 5: just under 5 minutes (4:59) must not clear the window. */
    @Test
    fun `no premature clearing just under five minutes`() {
        memory.record("q1", "a1")

        clock.advanceBy(Duration.ofMinutes(4).plusSeconds(59))

        assertEquals(listOf(Exchange("q1", "a1")), memory.currentExchanges())
    }

    /**
     * Mutable [Clock] test double: holds an [Instant] that tests advance synchronously via
     * [advanceBy], letting `SessionMemory`'s lazy timeout check be driven deterministically
     * without `Thread.sleep` or any real elapsed wall-clock time. Fixed at [ZoneOffset.UTC].
     */
    private class MutableClock(
        private var current: Instant,
        private val zone: ZoneId = ZoneOffset.UTC,
    ) : Clock() {
        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

        override fun instant(): Instant = current

        fun advanceBy(duration: Duration) {
            current = current.plus(duration)
        }
    }
}
