package com.anpurnama.f1_app.widget.countdown

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.model.NextRace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.TimeUnit

/**
 * Periodic `CoroutineWorker` that refreshes the [NextRaceCache] the
 * Countdown widget reads. Enqueued by [F1App.onCreate] (periodic) and
 * by the widget's "tap to retry" affordance (one-time, see
 * [enqueueOneTime]).
 *
 * **Schedule.** 15-minute WorkManager floor with the
 * `NETWORK_TYPE_CONNECTED` constraint and exponential backoff. One
 * `PeriodicWorkRequest` for the whole app lifetime; the adaptive
 * gate in [doWork] decides whether the current tick actually fetches.
 *
 * **Adaptive gate** (per ticket 07 + wayfinder 07):
 *
 *  - `snapshot == null` → fetch (first cold launch, no cache yet).
 *  - `now - lastSyncedMillis >= 60min` → fetch (cache is stale).
 *  - `startMillis > 0L && nowMillis in [startMillis - 3d, startMillis + 3h)` →
 *    fetch (inside the race window — keep the countdown / LIVE
 *    transition prompt).
 *  - Otherwise → skip; the next periodic tick decides again.
 *
 * **V1 simplification.** The design's "race window" is
 * `[FP1_start, race_start + 3h]`. The widget cache only stores
 * `raceStartMillis` (not the full session schedule) to keep the
 * worker narrow and the cache small. So the v1 approximation
 * uses a fixed 3-day pre-race buffer in place of `FP1_start`:
 * 3 days before through 3 hours after the race. Slightly wider
 * than the literal spec by ~1 day; cheaper than caching the
 * full session schedule. Documented compromise; revisit if
 * pre-FP1 freshness becomes a real user complaint.
 *
 * **Fetch failure policy.** Per the data-layer invariant: the cache
 * is **never cleared** on a network failure. The next successful
 * tick writes the new data; a failed tick returns
 * [Result.success] (the 15-min periodic tick is the retry path —
 * double-scheduling via `Result.retry` would be redundant and
 * could stall the next periodic run).
 *
 * **On success.** Writes the new snapshot, then calls
 * [CountdownWidget.updateAll] to repaint every active instance.
 */
class CountdownWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? F1App
            ?: return Result.success()  // defensive: never crash the worker
        val wiring = app.wiring
        val cache = wiring.nextRaceCache
        val now = Clock.System.now().toEpochMilliseconds()

        val current = cache.snapshot()
        if (!shouldFetch(current = current, nowMillis = now)) {
            return Result.success()
        }

        return when (val outcome = wiring.getNextRace(forceRefresh = true)) {
            is Outcome.Success -> {
                val nextRace = outcome.data
                if (nextRace == null) {
                    cache.writeOffSeason(lastSyncedMillis = now)
                } else {
                    val startMillis = raceStartMillis(nextRace)
                    cache.write(
                        NextRaceSnapshot(
                            year = nextRace.year,
                            round = nextRace.round,
                            raceName = nextRace.raceName,
                            circuitName = nextRace.circuit.name,
                            circuitCountry = nextRace.circuit.country,
                            circuitId = nextRace.circuit.id,
                            startMillis = startMillis,
                            lastSyncedMillis = now,
                        )
                    )
                }
                // Repaint every instance of the widget with the new
                // cache state. The next render will hit the new data
                // via `cache.observe()`.
                CountdownWidget().updateAll(applicationContext)
                Result.success()
            }
            is Outcome.Failure, Outcome.Loading -> {
                // Leave the cache alone — never clear on failure
                // (per data-layer invariant). The next periodic tick
                // is the retry path.
                Result.success()
            }
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "f1app_countdown_widget_periodic"
        const val UNIQUE_ONETIME_NAME = "f1app_countdown_widget_onetime"

        private const val CACHE_STALE_MS = 60L * 60 * 1000  // 60 min
        private const val PRE_RACE_WINDOW_MS = 3L * 24 * 60 * 60 * 1000  // 3 days

        /**
         * "In the race window" = `[start - 3d, start + 3h]`. The 3-day
         * pre-race buffer mirrors the spec's intent: the design's
         * literal window is `[FP1_start, race_start + 3h]`, where
         * FP1 lands Friday afternoon. v1 doesn't cache FP1 (see the
         * worker's KDoc), so the v1 approximation polls every tick
         * starting 3 days before the race. Wider than the literal
         * spec by ~1 day; cheaper than caching the full session
         * schedule.
         */

        /**
         * Public enqueue API. Called by `F1App.onCreate()` at process
         * start. `ExistingPeriodicWorkPolicy.UPDATE` so changing the
         * schedule (e.g. tuning the cache-stale threshold) takes
         * effect on the next app launch without first needing to
         * cancel the existing spec.
         */
        fun enqueuePeriodic(
            context: Context,
            networkType: NetworkType = NetworkType.CONNECTED,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ) {
            val request = PeriodicWorkRequestBuilder<CountdownWorker>(
                repeatInterval = 15, repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    backoffDelay = 10, timeUnit = TimeUnit.MINUTES,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            // Touch the dispatcher arg so a future override compiles
            // cleanly; today we delegate to the WorkManager pool.
            @Suppress("UNUSED_VARIABLE")
            val unused = ioDispatcher
        }

        /**
         * "Tap to retry" path for the [CountdownState.NoRaceData]
         * state. One-time, network-constrained. `REPLACE` so a
         * second tap cancels any in-flight retry rather than
         * queueing a second one.
         *
         * Not expedited: a regular one-time work is plenty for "the
         * user opened their phone to glance at the widget and
         * nothing was there". Expedited jobs are quota-bound; no
         * reason to spend the budget on this.
         */
        fun enqueueOneTime(
            context: Context,
            networkType: NetworkType = NetworkType.CONNECTED,
        ) {
            val request = OneTimeWorkRequestBuilder<CountdownWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONETIME_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /**
         * Visible-for-test hook. Decides whether the current tick
         * should fetch. Pure: `nowMillis` and the cached snapshot
         * are the only inputs. The same function shape would be
         * trivial to unit-test in isolation if a test wants to pin
         * the 60-min gate independently of the worker harness.
         */
        internal fun shouldFetch(current: NextRaceSnapshot?, nowMillis: Long): Boolean {
            if (current == null) return true
            if (nowMillis - current.lastSyncedMillis >= CACHE_STALE_MS) return true
            val start = current.startMillis
            if (start > 0L) {
                val windowStart = start - PRE_RACE_WINDOW_MS
                val windowEnd = start + RACE_WINDOW_MS
                if (nowMillis in windowStart..<windowEnd) return true
            }
            return false
        }

        /**
         * Compose a f1api.dev `raceDate` ("YYYY-MM-DD") +
         * `raceTime` ("HH:MM:SSZ") pair into epoch millis via
         * `kotlinx.datetime.Instant.parse`. The combined wire
         * shape is `"YYYY-MM-DDTHH:MM:SSZ"`, which is exactly
         * what `Instant.parse` accepts.
         *
         * Returns `0L` on a malformed or missing pair, which the
         * reducer maps to [CountdownState.SeasonOver]. The next
         * successful worker tick overwrites it with a real value.
         */
        internal fun raceStartMillis(nextRace: NextRace): Long {
            val date = nextRace.raceDate ?: return 0L
            val time = nextRace.raceTime ?: return 0L
            val composed = "${date}T${time}"
            return runCatching { Instant.parse(composed).toEpochMilliseconds() }
                .getOrElse { 0L }
        }
    }
}
