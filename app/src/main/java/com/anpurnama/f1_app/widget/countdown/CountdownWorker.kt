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
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.f1.model.SessionTime
import com.anpurnama.f1_app.f1.model.SessionType
import com.anpurnama.f1_app.f1.model.toWeekendSchedule
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.TimeUnit

/**
 * Refreshes widget cache on WorkManager's 15-minute cadence. Failures preserve cached data and
 * return [Result.success], so the next periodic tick—not a second retry schedule—tries again.
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
                    val season = when (val seasonOutcome = wiring.getSeason(forceRefresh = true)) {
                        is Outcome.Success -> seasonOutcome.data
                        is Outcome.Failure, Outcome.Loading -> null
                    }
                    val session = widgetSession(nextRace = nextRace, season = season, nowMillis = now)
                    cache.write(
                        NextRaceSnapshot(
                            year = nextRace.year,
                            round = nextRace.round,
                            raceName = nextRace.raceName,
                            circuitName = nextRace.circuit.name,
                            circuitCountry = nextRace.circuit.country,
                            circuitId = nextRace.circuit.id,
                            sessionName = session.name,
                            startMillis = session.startMillis,
                            lastSyncedMillis = now,
                        )
                    )
                }
                CountdownWidget().updateAll(applicationContext)
                Result.success()
            }
            is Outcome.Failure, Outcome.Loading -> {
                // Preserve the last known widget state until a later successful tick.
                Result.success()
            }
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "f1app_countdown_widget_periodic"
        const val UNIQUE_ONETIME_NAME = "f1app_countdown_widget_onetime"

        private const val CACHE_STALE_MS = 60L * 60 * 1000
        private const val PRE_RACE_WINDOW_MS = 3L * 24 * 60 * 60 * 1000

        /** UPDATE applies changed scheduling constraints to the existing periodic work. */
        fun enqueuePeriodic(
            context: Context,
            networkType: NetworkType = NetworkType.CONNECTED,
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
        }

        /** REPLACE prevents repeated taps from queuing duplicate retries. */
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

        internal fun shouldFetch(current: NextRaceSnapshot?, nowMillis: Long): Boolean {
            if (current == null) return true
            if (nowMillis - current.lastSyncedMillis >= CACHE_STALE_MS) return true
            val start = current.startMillis
            if (start > 0L) {
                if (nowMillis >= start + RACE_WINDOW_MS) return true
                val windowStart = start - PRE_RACE_WINDOW_MS
                val windowEnd = start + RACE_WINDOW_MS
                if (nowMillis in windowStart..<windowEnd) return true
            }
            return false
        }

        /** Missing or malformed API date/time becomes the off-season `0L` sentinel. */
        internal fun raceStartMillis(nextRace: NextRace): Long {
            val date = nextRace.raceDate ?: return 0L
            val time = nextRace.raceTime ?: return 0L
            val composed = "${date}T${time}"
            return runCatching { Instant.parse(composed).toEpochMilliseconds() }
                .getOrElse { 0L }
        }

        internal fun widgetSession(
            nextRace: NextRace,
            season: Season?,
            nowMillis: Long,
        ): WidgetSession {
            val fallback = WidgetSession(
                name = SessionType.Race.widgetLabel(),
                startMillis = raceStartMillis(nextRace),
            )
            val sessions = season
                ?.races
                ?.firstOrNull { it.round == nextRace.round }
                ?.schedule
                ?.toWeekendSchedule()
                ?.sessions
                .orEmpty()
            val session = sessions.firstOrNull { nowMillis < it.start.toEpochMilliseconds() + RACE_WINDOW_MS }
                ?: return fallback
            return WidgetSession(
                name = session.widgetLabel(),
                startMillis = session.start.toEpochMilliseconds(),
            )
        }
    }
}

internal data class WidgetSession(
    val name: String,
    val startMillis: Long,
)

private fun SessionTime.widgetLabel(): String = type.widgetLabel()

private fun SessionType.widgetLabel(): String = when (this) {
    SessionType.FP1 -> "Free Practice 1"
    SessionType.FP2 -> "Free Practice 2"
    SessionType.FP3 -> "Free Practice 3"
    SessionType.SprintQuali -> "Sprint Qualifying"
    SessionType.Sprint -> "Sprint"
    SessionType.Quali -> "Qualifying"
    SessionType.Race -> "Race"
}
