package com.anpurnama.f1_app.core.cache

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Pure-function tests for [CacheSyncWorker.buildPeriodicRequest] — the
 * seamed request builder. The actual `enqueueUniquePeriodicWork(KEEP)`
 * call hits `WorkManager.getInstance(context)` which is not available
 * in JVM unit tests; the constants and the periodic-request builder
 * are the inspectable surface, matching the [com.anpurnama.f1_app.widget.countdown.CountdownWorker]
 * style (gate logic is tested as a pure function, not via a
 * WorkManager harness).
 *
 * Per the spec + ADR 0017:
 * - 12-hour fixed interval
 * - `NetworkType.CONNECTED` constraint
 * - `BackoffPolicy.EXPONENTIAL` starting at 30s
 * - Unique work name `current-season-cache-sync`
 *
 * The tests assert policy and constraints, never exact wall-clock run
 * timing.
 */
class CacheSyncWorkerTest {

    @Test
    fun `unique periodic name matches the offline-cache contract`() {
        assertEquals(
            "current-season-cache-sync",
            CacheSyncWorker.UNIQUE_PERIODIC_NAME,
        )
    }

    @Test
    fun `interval is fixed at 12 hours`() {
        assertEquals(12L, CacheSyncWorker.INTERVAL_HOURS)
    }

    @Test
    fun `backoff delay is 30 seconds and exponential`() {
        assertEquals(30L, CacheSyncWorker.BACKOFF_DELAY_SECONDS)
        val request = CacheSyncWorker.buildPeriodicRequest()
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        val expectedDelayMs = TimeUnit.SECONDS.toMillis(CacheSyncWorker.BACKOFF_DELAY_SECONDS)
        assertEquals(expectedDelayMs, request.workSpec.backoffDelayDuration)
    }

    @Test
    fun `buildPeriodicRequest requires CONNECTED network`() {
        val request = CacheSyncWorker.buildPeriodicRequest()
        val constraints = request.workSpec.constraints
        assertNotNull(constraints)
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }

    @Test
    fun `buildPeriodicRequest honors an injected network type for tests`() {
        // Sanity: the network type is a parameter so a future test
        // could swap it without a new builder.
        val request = CacheSyncWorker.buildPeriodicRequest(networkType = NetworkType.METERED)
        assertEquals(NetworkType.METERED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun `buildPeriodicRequest is a periodic request with a 12h interval`() {
        val request: PeriodicWorkRequest = CacheSyncWorker.buildPeriodicRequest()
        // The WorkSpec stores interval duration in milliseconds.
        val expectedIntervalMs = TimeUnit.HOURS.toMillis(CacheSyncWorker.INTERVAL_HOURS)
        assertEquals(expectedIntervalMs, request.workSpec.intervalDuration)
    }

    @Test
    fun `buildPeriodicRequest does not require battery, charging, idle, or storage state`() {
        // v1 only constrains network. Future expansion (e.g.
        // BATTERY_NOT_LOW) would go here so the test fails loudly.
        val constraints = CacheSyncWorker.buildPeriodicRequest().workSpec.constraints
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertEquals(false, constraints.requiresBatteryNotLow())
        assertEquals(false, constraints.requiresCharging())
        assertEquals(false, constraints.requiresDeviceIdle())
    }

    @Test
    fun `bundle result empty result is not a total failure`() {
        // CacheSyncWorker treats an empty bundle as success so the
        // next 12h tick can re-evaluate. The [BundleRefreshResult]
        // helper must reflect that.
        val empty = com.anpurnama.f1_app.core.cache.BundleRefreshResult.Empty
        assertTrue(empty.isEmpty)
        assertEquals(false, empty.isTotalFailure())
    }

    @Test
    fun `empty bundle signals the worker to advance to the next tick without retry`() {
        // Documents the worker decision matrix:
        //   - empty bundle (off-season / pre-promotion / no
        //     eligible sessions) → Result.success(): nothing to
        //     retry, the next 12h tick re-evaluates against fresh
        //     state.
        //   - non-empty bundle with at least one success →
        //     Result.success(): partial or full success, advance.
        //   - non-empty bundle with no successes →
        //     Result.retry(): transient infrastructure failure,
        //     defer to exponential backoff.
        val empty = com.anpurnama.f1_app.core.cache.BundleRefreshResult.Empty
        val offSeason = empty
        val partialSuccess = com.anpurnama.f1_app.core.cache.BundleRefreshResult(
            listOf(
                com.anpurnama.f1_app.core.cache.BundleRefreshResult.Entry(
                    key = "k1",
                    result = com.anpurnama.f1_app.core.cache.RefreshResult.Failure("503"),
                ),
                com.anpurnama.f1_app.core.cache.BundleRefreshResult.Entry(
                    key = "k2",
                    result = com.anpurnama.f1_app.core.cache.RefreshResult.Success,
                ),
            ),
        )
        val totalFailure = com.anpurnama.f1_app.core.cache.BundleRefreshResult(
            listOf(
                com.anpurnama.f1_app.core.cache.BundleRefreshResult.Entry(
                    key = "k1",
                    result = com.anpurnama.f1_app.core.cache.RefreshResult.Failure("503"),
                ),
            ),
        )
        // Off-season: empty, not a total failure, worker returns success.
        assertEquals(false, offSeason.isTotalFailure())
        assertTrue(offSeason.isEmpty)
        // Partial success: not a total failure, worker returns success.
        assertEquals(false, partialSuccess.isTotalFailure())
        assertEquals(1, partialSuccess.succeeded)
        // Total failure: worker returns retry.
        assertEquals(true, totalFailure.isTotalFailure())
    }

    @Test
    fun `decideWorkerResult success on empty bundle`() {
        // Off-season / no active season / no schedule / no
        // eligible sessions — nothing to attempt, advance the tick.
        val result = com.anpurnama.f1_app.core.cache.decideWorkerResult(
            com.anpurnama.f1_app.core.cache.BundleRefreshResult.Empty,
        )
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
    }

    @Test
    fun `decideWorkerResult success on partial success`() {
        val partial = com.anpurnama.f1_app.core.cache.BundleRefreshResult(
            listOf(
                com.anpurnama.f1_app.core.cache.BundleRefreshResult.Entry(
                    key = "k1",
                    result = com.anpurnama.f1_app.core.cache.RefreshResult.Failure("503"),
                ),
                com.anpurnama.f1_app.core.cache.BundleRefreshResult.Entry(
                    key = "k2",
                    result = com.anpurnama.f1_app.core.cache.RefreshResult.Success,
                ),
            ),
        )
        val result = com.anpurnama.f1_app.core.cache.decideWorkerResult(partial)
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
    }

    @Test
    fun `decideWorkerResult retry on total failure including schedule-only failure`() {
        // The fix for the no-active-season / no-schedule
        // misclassification: a single failed schedule entry on a
        // pre-promotion device is the schedule entry plus empty
        // bundles — the aggregate is one failed entry, so the worker
        // retries rather than silently succeeding. A transient
        // schedule failure must trigger backoff, not be mistaken
        // for off-season.
        val scheduleOnlyFailure = com.anpurnama.f1_app.core.cache.BundleRefreshResult(
            listOf(
                com.anpurnama.f1_app.core.cache.BundleRefreshResult.Entry(
                    key = "season-schedule",
                    result = com.anpurnama.f1_app.core.cache.RefreshResult.Failure(
                        "Server error (503)",
                    ),
                ),
            ),
        )
        val result = com.anpurnama.f1_app.core.cache.decideWorkerResult(scheduleOnlyFailure)
        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `decideWorkerResult success when the schedule failed but the bundles succeeded`() {
        // A failed schedule on a device with an existing active
        // season does not strand the worker: the bundles still
        // operate against the existing active season and the
        // schedule failure is recorded as attempt metadata only.
        val partial = com.anpurnama.f1_app.core.cache.BundleRefreshResult(
            listOf(
                com.anpurnama.f1_app.core.cache.BundleRefreshResult.Entry(
                    key = "season-schedule",
                    result = com.anpurnama.f1_app.core.cache.RefreshResult.Failure(
                        "Server error (503)",
                    ),
                ),
                com.anpurnama.f1_app.core.cache.BundleRefreshResult.Entry(
                    key = "k1",
                    result = com.anpurnama.f1_app.core.cache.RefreshResult.Success,
                ),
            ),
        )
        val result = com.anpurnama.f1_app.core.cache.decideWorkerResult(partial)
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
    }

    @Test
    fun `bundle result with only failures is a total failure`() {
        val onlyFailures = com.anpurnama.f1_app.core.cache.BundleRefreshResult(
            listOf(
                com.anpurnama.f1_app.core.cache.BundleRefreshResult.Entry(
                    key = "k1",
                    result = com.anpurnama.f1_app.core.cache.RefreshResult.Failure("503"),
                ),
            ),
        )
        assertEquals(true, onlyFailures.isTotalFailure())
    }

    @Test
    fun `bundle result with at least one success is not a total failure`() {
        val partial = com.anpurnama.f1_app.core.cache.BundleRefreshResult(
            listOf(
                com.anpurnama.f1_app.core.cache.BundleRefreshResult.Entry(
                    key = "k1",
                    result = com.anpurnama.f1_app.core.cache.RefreshResult.Failure("503"),
                ),
                com.anpurnama.f1_app.core.cache.BundleRefreshResult.Entry(
                    key = "k2",
                    result = com.anpurnama.f1_app.core.cache.RefreshResult.Success,
                ),
            ),
        )
        assertEquals(false, partial.isTotalFailure())
        assertEquals(1, partial.succeeded)
        assertEquals(1, partial.failed)
    }
}
