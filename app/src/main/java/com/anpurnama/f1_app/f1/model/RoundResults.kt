package com.anpurnama.f1_app.f1.model

/**
 * Per-round race result, returned by f1api.dev `/{year}/{round}/race`.
 * Drives the Round detail full grid AND the Schedule > Past list
 * podium (sliced `[0..2]`).
 *
 * `position` is kept as a String per the ticket 03 spec: handles
 * `"1"`/`"2"`/`"3"`/`"NC"` without any parsing. Consumers slice the
 * ordered `results` array rather than sorting on `position` —
 * position order is position order. The "NC" string is for
 * DNF/DNS rows; they have a non-zero `grid` and a messy `time`
 * like `"DNF (1)"`.
 *
 * `time` is kept un-parsed: `"1:31:44"` for the winner, `"+22.457"`
 * for the gap, `"+1 lap"` for lapped, `"DNF (1)"` for retirees.
 *
 * `grid` is also a String (`"1"` for pole, `"20"` for back of grid).
 * Stored as String to keep the schema noise consistent with
 * `position` and avoid a parse that would crash on `"Pit"` rows.
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
