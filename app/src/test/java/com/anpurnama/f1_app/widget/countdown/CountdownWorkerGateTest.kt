package com.anpurnama.f1_app.widget.countdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for [CountdownWorker.shouldFetch] — the pure adaptive-gate
 * function that decides whether a periodic tick should hit the
 * network. No `android.*` dependencies (no Context, no WorkManager
 * harness), so this stays in `src/test/` next to the reducer.
 *
 * The gate is the one from the wayfinder 07 + ticket 07 spec, with
 * the v1 simplification: only `raceStartMillis` is in the cache, so
 * "in the race window" is approximated as `now < raceStart + 3h`.
 * That approximation is documented in the worker's KDoc.
 */
class CountdownWorkerGateTest {

    private val start = 1_700_000_000_000L
    private val oneMin = 60_000L
    private val oneHour = 60 * oneMin
    private val oneDay = 24 * oneHour
    private val threeHours = 3 * oneHour

    private fun snapshot(startMillis: Long = start, lastSynced: Long = start - 10 * oneMin) =
        NextRaceSnapshot(
            year = 2026,
            round = 1,
            raceName = "Bahrain GP",
            circuitName = "Bahrain International Circuit",
            circuitCountry = "Bahrain",
            circuitId = "bahrain",
            startMillis = startMillis,
            lastSyncedMillis = lastSynced,
        )

    // -------- first cold launch (no cache) --------

    @Test
    fun `null snapshot always fetches`() {
        assertTrue(CountdownWorker.shouldFetch(current = null, nowMillis = start))
        assertTrue(
            "even a 'now' in the past should still fetch on a cold launch",
            CountdownWorker.shouldFetch(current = null, nowMillis = start - 30 * oneDay),
        )
    }

    // -------- cache age gate (60 min) --------

    @Test
    fun `cache younger than 60 min and outside race window does not fetch`() {
        val now = start - 7 * oneDay  // a week before the race
        val snap = snapshot(lastSynced = now - 30 * oneMin)
        assertFalse(CountdownWorker.shouldFetch(current = snap, nowMillis = now))
    }

    @Test
    fun `cache exactly 60 min old triggers fetch`() {
        val now = start - 7 * oneDay
        val snap = snapshot(lastSynced = now - 60 * oneMin)
        assertTrue(
            "60 min is the boundary; should fire",
            CountdownWorker.shouldFetch(current = snap, nowMillis = now),
        )
    }

    @Test
    fun `cache older than 60 min triggers fetch`() {
        val now = start - 7 * oneDay
        val snap = snapshot(lastSynced = now - 90 * oneMin)
        assertTrue(CountdownWorker.shouldFetch(current = snap, nowMillis = now))
    }

    // -------- race window gate (now < start + 3h) --------

    @Test
    fun `cache fresh and now in the race window fetches every tick`() {
        val now = start + 30 * oneMin  // 30 min into the race
        val snap = snapshot(lastSynced = now - 1 * oneMin)  // brand-new cache
        assertTrue(CountdownWorker.shouldFetch(current = snap, nowMillis = now))
    }

    @Test
    fun `cache fresh and now before the race window does not fetch`() {
        val now = start - 6 * oneDay  // 6 days before
        val snap = snapshot(lastSynced = now - 1 * oneMin)
        assertFalse(CountdownWorker.shouldFetch(current = snap, nowMillis = now))
    }

    @Test
    fun `cache fresh and now after the race window does not fetch`() {
        val now = start + threeHours + 5 * oneMin  // 5 min past the window
        val snap = snapshot(lastSynced = now - 1 * oneMin)
        assertFalse(CountdownWorker.shouldFetch(current = snap, nowMillis = now))
    }

    @Test
    fun `cache fresh and now exactly at startMillis fetches (still in window)`() {
        val now = start
        val snap = snapshot(lastSynced = now - 1 * oneMin)
        assertTrue(CountdownWorker.shouldFetch(current = snap, nowMillis = now))
    }

    // -------- off-season sentinel --------

    @Test
    fun `off-season snapshot defers to cache age gate`() {
        // 0L sentinel, fresh cache: no fetch.
        val now = 1_700_000_000_000L
        val snap = snapshot(startMillis = 0L, lastSynced = now - 5 * oneMin)
        assertFalse(CountdownWorker.shouldFetch(current = snap, nowMillis = now))
    }

    @Test
    fun `off-season snapshot with stale cache still fetches via the age gate`() {
        // 0L sentinel, 90-min-old cache: age gate fires (this is how
        // the worker eventually picks up the new season's first race
        // without needing a "next season announced" sentinel).
        val now = 1_700_000_000_000L
        val snap = snapshot(startMillis = 0L, lastSynced = now - 90 * oneMin)
        assertTrue(CountdownWorker.shouldFetch(current = snap, nowMillis = now))
    }
}
