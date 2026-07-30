package com.anpurnama.f1_app.core.cache

import androidx.work.BackoffPolicy
import androidx.work.ListenableWorker
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
    fun `decideWorkerResult success on empty and neutral bundles`() {
        val neutral = BundleRefreshResult(
            listOf(
                BundleRefreshResult.Entry("fresh", RefreshResult.SkippedFresh),
                BundleRefreshResult.Entry("future", RefreshResult.Deferred),
            ),
        )

        assertEquals(ListenableWorker.Result.success(), decideWorkerResult(BundleRefreshResult.Empty))
        assertEquals(ListenableWorker.Result.success(), decideWorkerResult(neutral))
    }

    @Test
    fun `decideWorkerResult retries mixed refreshed skipped and retryable outcomes`() {
        val mixed = BundleRefreshResult(
            listOf(
                BundleRefreshResult.Entry("written", RefreshResult.Refreshed),
                BundleRefreshResult.Entry("fresh", RefreshResult.SkippedFresh),
                BundleRefreshResult.Entry("offline", RefreshResult.RetryableFailure("503")),
            ),
        )

        assertEquals(ListenableWorker.Result.retry(), decideWorkerResult(mixed))
    }

    @Test
    fun `decideWorkerResult does not retry permanent failures`() {
        val permanent = BundleRefreshResult(
            listOf(
                BundleRefreshResult.Entry("bad-request", RefreshResult.PermanentFailure("404")),
                BundleRefreshResult.Entry("fresh", RefreshResult.SkippedFresh),
            ),
        )

        assertEquals(ListenableWorker.Result.success(), decideWorkerResult(permanent))
    }

    @Test
    fun `legacy session failures retain total-failure retry behavior during expansion`() {
        val legacyOnlyFailure = BundleRefreshResult(
            listOf(BundleRefreshResult.Entry("session", RefreshResult.Failure("503"))),
        )
        val legacyPartialSuccess = BundleRefreshResult(
            listOf(
                BundleRefreshResult.Entry("session", RefreshResult.Failure("503")),
                BundleRefreshResult.Entry("pitstop", RefreshResult.Success),
            ),
        )

        assertEquals(ListenableWorker.Result.retry(), decideWorkerResult(legacyOnlyFailure))
        assertEquals(ListenableWorker.Result.success(), decideWorkerResult(legacyPartialSuccess))
    }

    @Test
    fun `legacy session failure beside a migrated write retains prior partial-success behavior`() {
        val transitionalMixed = BundleRefreshResult(
            listOf(
                BundleRefreshResult.Entry("schedule", RefreshResult.Refreshed),
                BundleRefreshResult.Entry("session", RefreshResult.Failure("503")),
            ),
        )

        assertEquals(ListenableWorker.Result.success(), decideWorkerResult(transitionalMixed))
    }

}
