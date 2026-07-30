package com.anpurnama.f1_app.core.cache

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anpurnama.f1_app.F1App
import kotlinx.datetime.Clock
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager job that warms the current-season offline cache
 * once every 12 hours. Runs the same per-resource refresh methods the
 * foreground screens use, so the worker inherits:
 *
 * - per-resource single-flight (foreground + worker coalesce),
 * - per-resource TTL gates (the worker does not bypass stale decisions),
 * - per-resource failure isolation (one bad endpoint never aborts the
 *   bundle; successful writes stand, failed resources record attempt
 *   metadata only).
 *
 * The schedule refresh runs first because `/current` is the only atomic
 * active-season promotion authority (ADR 0017). If it fails, the
 * existing active season (if any) is reused for the resource bundles —
 * a transient schedule failure does not strand the worker.
 *
 * Worker result policy:
 * - `Result.retry()` when any migrated current-season resource reports
 *   a retryable failure, even if other writes succeeded or snapshots
 *   were fresh. Successful writes remain committed and TTL-skip later.
 * - Fresh skips, deferred work, and permanent failures are neutral.
 * - Session resources temporarily retain their legacy all-failed retry
 *   policy until issue #68 migrates them.
 *
 * Resource selection is the repositories' `refreshCurrentSeasonBundle`
 * methods; this worker is orchestration only.
 */
class CacheSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? F1App
            ?: return Result.success()  // defensive: never crash the worker
        val wiring = app.wiring

        // 1. Atomic active-season promotion (or refresh of the existing one).
        //    The schedule entry is included in the aggregate so a failed
        //    schedule refresh on a first-run / pre-promotion device is
        //    surfaced as a total failure (worker retries) rather than
        //    silently treated as off-season — the bundles return empty
        //    when no active season is set, which the worker would
        //    otherwise mis-classify as "nothing to attempt this tick".
        //    A failed schedule on a device with an existing active
        //    season does not strand the worker: the bundles still
        //    operate against the existing active season and the
        //    schedule failure is recorded as attempt metadata only.
        val schedule = BundleRefreshResult(
            listOf(
                BundleRefreshResult.Entry(
                    key = "season-schedule",
                    result = wiring.refreshCurrentSeasonScheduleForPeriodic(),
                ),
            ),
        )

        val now = Clock.System.now()

        // 2. Best-effort bundle of next race + standings + catalogs.
        val resources = wiring.refreshCurrentSeasonResourcesBundleForPeriodicSync()

        // 3. Best-effort bundle of plausibly-complete session results + pitstops.
        val sessions = wiring.refreshCurrentSeasonSessionsBundleForPeriodicSync(now)

        return decideWorkerResult(concat(schedule, resources, sessions))
    }

    /** Concatenate [BundleRefreshResult]s without allocating a new list when empty. */
    private fun concat(
        a: BundleRefreshResult,
        b: BundleRefreshResult,
        c: BundleRefreshResult,
    ): BundleRefreshResult {
        val all = buildList {
            if (a.entries.isNotEmpty()) addAll(a.entries)
            if (b.entries.isNotEmpty()) addAll(b.entries)
            if (c.entries.isNotEmpty()) addAll(c.entries)
        }
        return if (all.isEmpty()) BundleRefreshResult.Empty else BundleRefreshResult(all)
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME: String = "current-season-cache-sync"

        // 12h tick per the wayfinder 05 decision and ADR 0017.
        const val INTERVAL_HOURS: Long = 12L

        // Exponential backoff starting at 30s per the wayfinder 05 spec.
        const val BACKOFF_DELAY_SECONDS: Long = 30L

        /**
         * Build the periodic request. Exposed for tests so the
         * interval, network constraint, and backoff can be asserted
         * without spinning up a `WorkManager` test harness. The actual
         * [enqueuePeriodic] is the production seam called from
         * [com.anpurnama.f1_app.F1App.onCreate].
         */
        fun buildPeriodicRequest(
            networkType: NetworkType = NetworkType.CONNECTED,
        ): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<CacheSyncWorker>(
                repeatInterval = INTERVAL_HOURS,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    backoffDelay = BACKOFF_DELAY_SECONDS,
                    timeUnit = TimeUnit.SECONDS,
                )
                .build()

        /**
         * Enqueue the unique periodic job. [ExistingPeriodicWorkPolicy.KEEP]
         * is intentional for a stable v1 policy: it guarantees that a
         * later interval/constraint change does not silently mutate
         * already-enqueued work. Future changes must use [ExistingPeriodicWorkPolicy.UPDATE]
         * or a versioned unique work name.
         */
        fun enqueuePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                buildPeriodicRequest(),
            )
        }
    }
}

/**
 * Pure decision: given a [BundleRefreshResult] aggregate from one
 * worker tick, return the [ListenableWorker.Result] WorkManager
 * should record.
 *
 * Empty and neutral-only aggregates advance to the next fixed tick.
 * Any migrated retryable failure requests WorkManager backoff. The
 * aggregate also preserves the pre-#68 legacy session rule: retry only
 * when legacy failures have no successful write beside them.
 *
 * Exposed at the file level (not on the worker companion) so the
 * JVM unit tests can exercise the decision matrix without a
 * `WorkManager` test harness.
 */
internal fun decideWorkerResult(aggregate: BundleRefreshResult): ListenableWorker.Result = when {
    aggregate.isEmpty -> ListenableWorker.Result.success()
    aggregate.requiresRetry -> ListenableWorker.Result.retry()
    else -> ListenableWorker.Result.success()
}
