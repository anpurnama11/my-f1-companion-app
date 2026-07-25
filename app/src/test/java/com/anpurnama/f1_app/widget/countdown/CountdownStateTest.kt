package com.anpurnama.f1_app.widget.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reducer unit test for the Countdown widget's render-time state machine.
 *
 * The reducer is a pure function: `now` (epoch millis) + the cached
 * [NextRaceSnapshot] (or null) → a [CountdownState] sealed-subtype. The
 * Glance widget just calls it and switches on the result. No Glance
 * harness, no WorkManager, no Compose runtime — JVM unit only.
 *
 * The full state matrix is table-driven in [transitions match the spec
 * table]; each case is one assertion over the returned state's
 * subtype, so a regression in any branch shows up as a single
 * mismatched class.
 */
class CountdownStateTest {

    private val start = 1_700_000_000_000L  // arbitrary anchor
    private val oneMinute = 60_000L
    private val oneHour = 60 * oneMinute
    private val oneDay = 24 * oneHour
    private val threeHours = 3 * oneHour

    private fun snapshot(
        startMillis: Long = start,
        circuitId: String = "bahrain",
        year: Int = 2026,
        round: Int = 1,
        raceName: String = "Bahrain GP",
        circuitName: String = "Bahrain International Circuit",
        circuitCountry: String? = "Bahrain",
        lastSyncedMillis: Long = start - oneDay,
    ) = NextRaceSnapshot(
        year = year,
        round = round,
        raceName = raceName,
        circuitName = circuitName,
        circuitCountry = circuitCountry,
        circuitId = circuitId,
        startMillis = startMillis,
        lastSyncedMillis = lastSyncedMillis,
    )

    // -------- individual transitions --------

    @Test
    fun `null snapshot is NoRaceData`() {
        val state = reduceCountdownState(nowMillis = start, snapshot = null)
        assertTrue(
            "expected NoRaceData, got $state",
            state is CountdownState.NoRaceData,
        )
    }

    @Test
    fun `startMillis 0L sentinel is SeasonOver even with no other fields`() {
        val state = reduceCountdownState(
            nowMillis = start,
            snapshot = snapshot(startMillis = 0L),
        )
        assertTrue(
            "expected SeasonOver, got $state",
            state is CountdownState.SeasonOver,
        )
    }

    @Test
    fun `now 25h before start is Countdown with 1 day`() {
        val state = reduceCountdownState(
            nowMillis = start - 25 * oneHour,
            snapshot = snapshot(),
        )
        assertTrue(
            "expected Countdown, got $state",
            state is CountdownState.Countdown,
        )
        val cd = state as CountdownState.Countdown
        assertEquals(1, cd.days)
        assertEquals(1, cd.hours)
        assertEquals(0, cd.minutes)
        // Sanity: the data the widget needs to render the body lines.
        assertEquals("Bahrain GP", cd.raceName)
        assertEquals("Bahrain", cd.circuitCountry)
        assertEquals("bahrain", cd.circuitId)
        assertEquals(start, cd.raceStartMillis)
    }

    @Test
    fun `now 1 minute before start is Countdown with 0d 0h 1m`() {
        val state = reduceCountdownState(
            nowMillis = start - oneMinute,
            snapshot = snapshot(),
        )
        val cd = state as CountdownState.Countdown
        assertEquals(0, cd.days)
        assertEquals(0, cd.hours)
        assertEquals(1, cd.minutes)
    }

    @Test
    fun `now exactly at start is LiveNow not Countdown`() {
        val state = reduceCountdownState(nowMillis = start, snapshot = snapshot())
        assertTrue(
            "expected LiveNow at the boundary, got $state",
            state is CountdownState.LiveNow,
        )
    }

    @Test
    fun `now 30 minutes into the race is LiveNow`() {
        val state = reduceCountdownState(
            nowMillis = start + 30 * oneMinute,
            snapshot = snapshot(),
        )
        assertTrue(state is CountdownState.LiveNow)
    }

    @Test
    fun `now 2h59m into the race is still LiveNow`() {
        val state = reduceCountdownState(
            nowMillis = start + threeHours - oneMinute,
            snapshot = snapshot(),
        )
        assertTrue(state is CountdownState.LiveNow)
    }

    @Test
    fun `now exactly 3h after start is RaceComplete`() {
        val state = reduceCountdownState(
            nowMillis = start + threeHours,
            snapshot = snapshot(),
        )
        assertTrue(
            "expected RaceComplete at the 3h boundary, got $state",
            state is CountdownState.RaceComplete,
        )
    }

    @Test
    fun `now 4 hours after start is RaceComplete`() {
        val state = reduceCountdownState(
            nowMillis = start + 4 * oneHour,
            snapshot = snapshot(),
        )
        assertTrue(state is CountdownState.RaceComplete)
    }

    // -------- table-driven matrix over the spec's 6 states --------

    @Test
    fun `transitions match the spec table`() {
        val cases: List<Triple<String, Long, NextRaceSnapshot?>> = listOf(
            // no cache
            Triple("null snapshot is NoRaceData", start, null),
            // off-season sentinel
            Triple("startMillis 0L is SeasonOver", start, snapshot(startMillis = 0L)),
            // countdown
            Triple("30d before start is Countdown", start - 30 * oneDay, snapshot()),
            Triple("1d before start is Countdown", start - oneDay, snapshot()),
            Triple("5m before start is Countdown", start - 5 * oneMinute, snapshot()),
            // live now
            Triple("start is LiveNow", start, snapshot()),
            Triple("start + 90m is LiveNow", start + 90 * oneMinute, snapshot()),
            Triple("start + 179m is LiveNow", start + 179 * oneMinute, snapshot()),
            // race complete
            Triple("start + 3h is RaceComplete", start + threeHours, snapshot()),
            Triple("start + 6h is RaceComplete", start + 6 * oneHour, snapshot()),
        )
        cases.forEach { (label, nowMillis, snap) ->
            val state = reduceCountdownState(nowMillis = nowMillis, snapshot = snap)
            val expected: Class<out CountdownState> = when {
                snap == null -> CountdownState.NoRaceData::class.java
                snap.startMillis == 0L -> CountdownState.SeasonOver::class.java
                nowMillis < start -> CountdownState.Countdown::class.java
                nowMillis < start + threeHours -> CountdownState.LiveNow::class.java
                else -> CountdownState.RaceComplete::class.java
            }
            assertEquals(
                "$label — expected ${expected.simpleName}, got ${state::class.simpleName}",
                expected,
                state::class.java,
            )
        }
    }

    // -------- deep link / accent strip / date-time data round-trips --------

    @Test
    fun `countdown state carries the data the widget needs to render and deep-link`() {
        val state = reduceCountdownState(
            nowMillis = start - 2 * oneDay - 3 * oneHour - 15 * oneMinute,
            snapshot = snapshot(),
        ) as CountdownState.Countdown

        assertEquals(2, state.days)
        assertEquals(3, state.hours)
        assertEquals(15, state.minutes)
        assertEquals("Bahrain GP", state.raceName)
        assertEquals("Bahrain International Circuit", state.circuitName)
        assertEquals("Bahrain", state.circuitCountry)
        assertEquals("bahrain", state.circuitId)
        assertEquals(start, state.raceStartMillis)
        // The widget builds the deep-link URI from these two values.
        assertEquals(2026, state.year)
        assertEquals(1, state.round)
    }

    @Test
    fun `live and complete states carry the same data minus the countdown tuple`() {
        val live = reduceCountdownState(nowMillis = start + 10 * oneMinute, snapshot = snapshot())
            as CountdownState.LiveNow
        val done = reduceCountdownState(nowMillis = start + 5 * oneHour, snapshot = snapshot())
            as CountdownState.RaceComplete

        // No countdown tuple in live/complete states — the widget renders
        // a state-specific label instead.
        assertNull((live as CountdownState).let { (it as? CountdownState.Countdown)?.days })
        assertNull((done as CountdownState).let { (it as? CountdownState.Countdown)?.days })

        // But the data the widget needs to deep-link + paint the accent
        // strip is present on both.
        assertEquals("bahrain", live.circuitId)
        assertEquals("bahrain", done.circuitId)
        assertEquals(2026, live.year)
        assertEquals(1, live.round)
    }
}
