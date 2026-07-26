package com.anpurnama.f1_app.widget.countdown

/**
 * The render-time display state for the Countdown widget. Computed
 * by [reduceCountdownState] from the cached [NextRaceSnapshot] and
 * `now` — no Glance, no Compose, no work scheduling involved at this
 * seam. The widget composable just switches on [phase] (via
 * `when`/sealed `is` checks) and renders the matching layout.
 *
 * Each non-error state carries the data the widget needs to render
 * the body + paint the accent strip + build the deep-link URI.
 *
 * The state matrix is the one from the ticket:
 *
 * | Condition                                         | Display
 * |---------------------------------------------------|----------------
 * | snapshot == null                                  | NoRaceData
 * | snapshot.startMillis == 0L                        | SeasonOver
 * | now < startMillis                                 | Countdown
 * | startMillis <= now < startMillis + RACE_WINDOW_MS | LiveNow
 * | now >= startMillis + RACE_WINDOW_MS               | RaceComplete
 */
sealed interface CountdownState {

    /**
     * No cached race at all (first cold launch, never written). The
     * widget renders the "tap to retry" affordance; the tap enqueues
     * a one-shot [CountdownWorker].
     */
    data object NoRaceData : CountdownState

    /**
     * Off-season sentinel: the worker detected an empty
     * `/current/next` and wrote `startMillis = 0L`. Deep link is
     * suppressed (no valid round to open).
     */
    data object SeasonOver : CountdownState

    /**
     * Race is still in the future. The widget renders the body
     * countdown (`2d 4h 30m`) + the GP date/time device-local.
     */
    data class Countdown(
        val raceName: String,
        val circuitName: String,
        val circuitCountry: String?,
        val circuitId: String,
        val sessionName: String,
        val sessionStartMillis: Long,
        val year: Int,
        val round: Int,
        val days: Int,
        val hours: Int,
        val minutes: Int,
    ) : CountdownState

    /**
     * Race window is open: `start <= now < start + RACE_WINDOW_MS`.
     * The widget renders "LIVE NOW" in the circuit accent color +
     * the GP date/time. Deep link is on.
     */
    data class LiveNow(
        val raceName: String,
        val circuitName: String,
        val circuitCountry: String?,
        val circuitId: String,
        val sessionName: String,
        val sessionStartMillis: Long,
        val year: Int,
        val round: Int,
    ) : CountdownState

    /**
     * The race window has closed but the cache hasn't flipped to the
     * following race yet (next worker tick will swap it). Transient
     * display: the widget renders "RACE COMPLETE" + the GP
     * date/time. Deep link is on.
     */
    data class RaceComplete(
        val raceName: String,
        val circuitName: String,
        val circuitCountry: String?,
        val circuitId: String,
        val sessionName: String,
        val sessionStartMillis: Long,
        val year: Int,
        val round: Int,
    ) : CountdownState
}

/**
 * Assumed race duration for the "live now" window. A real F1 race
 * runs ~2h, red-flags rarely push 3h, so 3h is the design's chosen
 * ceiling. Revisit if a wet race overruns and the widget flips to
 * "RACE COMPLETE" early.
 */
internal const val RACE_WINDOW_MS: Long = 3L * 60 * 60 * 1000

/**
 * Pure state reducer. The widget calls this on every `provideGlance`
 * with the current cached [NextRaceSnapshot] and the current wall
 * clock; the result is the state to render.
 *
 * @param nowMillis  wall-clock epoch millis at render time.
 * @param snapshot   the cached [NextRaceSnapshot], or `null` when no
 *                   cache has been written yet (first cold launch).
 *
 * The 60-min "stale" threshold from the worker's adaptive gate is
 * deliberately not a parameter here: the render-time reducer treats
 * any populated snapshot as the source of truth. A "cache set + sync
 * failure" never blanks the widget (per the ticket) — it just
 * continues to render the cached data until the next successful
 * fetch. The widget never needs to know whether the cache is fresh.
 */
fun reduceCountdownState(
    nowMillis: Long,
    snapshot: NextRaceSnapshot?,
): CountdownState {
    if (snapshot == null) return CountdownState.NoRaceData
    if (snapshot.startMillis == 0L) return CountdownState.SeasonOver

    val start = snapshot.startMillis
    return when {
        nowMillis < start -> {
            val remaining = start - nowMillis
            val days = (remaining / (24L * 60 * 60 * 1000)).toInt()
            val hours = ((remaining % (24L * 60 * 60 * 1000)) / (60L * 60 * 1000)).toInt()
            val minutes = ((remaining % (60L * 60 * 1000)) / (60L * 1000)).toInt()
            CountdownState.Countdown(
                raceName = snapshot.raceName,
                circuitName = snapshot.circuitName,
                circuitCountry = snapshot.circuitCountry,
                circuitId = snapshot.circuitId,
                sessionName = snapshot.sessionName,
                sessionStartMillis = start,
                year = snapshot.year,
                round = snapshot.round,
                days = days,
                hours = hours,
                minutes = minutes,
            )
        }
        nowMillis < start + RACE_WINDOW_MS -> CountdownState.LiveNow(
            raceName = snapshot.raceName,
            circuitName = snapshot.circuitName,
            circuitCountry = snapshot.circuitCountry,
            circuitId = snapshot.circuitId,
            sessionName = snapshot.sessionName,
            sessionStartMillis = start,
            year = snapshot.year,
            round = snapshot.round,
        )
        else -> CountdownState.RaceComplete(
            raceName = snapshot.raceName,
            circuitName = snapshot.circuitName,
            circuitCountry = snapshot.circuitCountry,
            circuitId = snapshot.circuitId,
            sessionName = snapshot.sessionName,
            sessionStartMillis = start,
            year = snapshot.year,
            round = snapshot.round,
        )
    }
}
