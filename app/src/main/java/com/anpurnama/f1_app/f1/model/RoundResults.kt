package com.anpurnama.f1_app.f1.model

/**
 * Per-round race result, returned by Jolpica standard
 * `/ergast/f1/{year}/{round}/results.json` (the f1api.dev hybrid merge is
 * retired — see lode/decisions/0006-race-results-hybrid-source.md, superseded).
 * Drives the Round detail full grid AND the Schedule > Past list
 * podium (sliced `[0..2]`).
 *
 * `position` is kept as a String: Ergast `positionText` is numeric for
 * finishers and lapped rows (`"1"`/`"2"`/`"11"`) and a code for
 * non-classified rows (`"R"` retired, `"D"`/`"E"`/`"W"`…); the latter
 * normalize to `"NC"` at the DTO→domain seam so the screen keeps its
 * f1api-era `"P NC"` + hidden-arrow behavior. Consumers slice the
 * ordered `results` array rather than sorting on `position` — position
 * order is position order.
 *
 * `time` is kept un-parsed: `"1:31:44.000"` for the winner, `"+22.457"`
 * for the gap, `"+1 lap"` for lapped; `null` for retirees/DNS — the
 * `RoundResult.displayStatusOrTime()` extension maps the `status` field
 * (`"Retired"`/`"Did not start"`/…) to `"DNF"`/`"DNS"`.
 *
 * `grid` is also a String (`"1"` for pole, `"20"` for back of grid,
 * `"0"` for pit-lane/DNS starts). Stored as String to keep the schema
 * noise consistent with `position` and avoid a parse that would crash
 * on `"0"` rows.
 */
data class RoundResult(
    val position: String,
    val points: Int,
    val grid: String,
    val time: String?,
    val driverId: String,
    val driverName: String,
    val driverShortName: String?,
    val driverNumber: Int?,
    val teamId: String,
    val teamName: String,
    /** Authoritative Jolpica status when available. */
    val status: String? = null,
    /** f1api.dev fastest-lap time, retained for the Race standout card. */
    val fastLap: String? = null,
)

/**
 * A single round's full result envelope, mapped to a domain
 * [RoundResults]. Carries the round metadata the Round detail
 * page renders as the page header (round, race name, date, circuit)
 * plus the ordered [results] for the grid + podium.
 */
data class RoundResults(
    val year: Int,
    val round: Int,
    val raceName: String,
    val date: String?,           // "YYYY-MM-DD" from `races.date`
    val time: String?,           // "HH:MM:SSZ" from `races.time`
    val circuit: Circuit,
    val results: List<RoundResult>,
)

/**
 * A single row of qualifying, returned by f1api.dev
 * `/{year}/{round}/qualy`. Drives the Round detail Qualifying tab.
 *
 * `gridPosition` is the starting-grid position this qualifying run
 * earned (Int, 1-based). `q1`/`q2`/`q3` are the per-segment lap
 * times: present when the driver reached that segment, `null`
 * otherwise (e.g. knocked out in Q1 has `q2 = null`, `q3 = null`).
 * The strings are dirty (`"1:30:031"`, `"1:29:374"`) and kept
 * un-parsed.
 */
data class QualifyingResult(
    val gridPosition: Int,
    val q1: String?,
    val q2: String?,
    val q3: String?,
    val driverId: String,
    val driverName: String,
    val driverShortName: String?,
    val driverNumber: Int?,
    val teamId: String,
    val teamName: String,
)

/**
 * Qualifying-envelope domain. Same shape as [RoundResults] but with
 * qualifying rows and a separate `qualyDate`/`qualyTime` instead of
 * `date`/`time`. One per round.
 */
data class RoundQualifying(
    val year: Int,
    val round: Int,
    val raceName: String,
    val qualyDate: String?,      // "YYYY-MM-DD" from `races.qualyDate`
    val qualyTime: String?,      // "HH:MM:SSZ" from `races.qualyTime`
    val circuit: Circuit,
    val results: List<QualifyingResult>,
)
