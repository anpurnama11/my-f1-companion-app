package com.anpurnama.f1_app.f1.model

// Next race. Drives Homepage §1 (countdown card) and §3 (nearest-GP
// info). The `country` + `qualyDate` fields are the OpenF1 join keys for
// `GetCircuitTopSpeedUseCase`; §3 already has the data on hand.
//
// `qualyDate` is the OpenF1 join date, not `raceDate`. OpenF1's session
// `date_start` is Qualifying day, which is 1 day before the race (or 2
// days for sprint weekends). The original ticket 11 research claimed
// the date matched `raceDate` — verified wrong on live data; the fix is
// to match against f1api.dev's `schedule.qualy.date` (the Qualifying
// day), which matches OpenF1's `date_start` date portion exactly.
data class NextRace(
    val year: Int,
    val round: Int,
    val raceName: String,
    val raceId: String,
    val laps: Int?,
    val circuit: Circuit,
    val raceDate: String?,  // "YYYY-MM-DD" from schedule.race.date (Sunday)
    val qualyDate: String?, // "YYYY-MM-DD" from schedule.qualy.date (Saturday for normal weekends, Friday for sprint)
    val raceTime: String?,  // "HH:MM:SSZ" from schedule.race.time
)

// Drivers' championship row. Position-ordered — first launch seed picks the
// top-2 by the position field. `driverName` is the long form
// ("Lewis Hamilton"); `driverShortName` is the F1 broadcast short
// ("HAM"). `points` and `wins` ride along for any future use; not surfaced
// on §1.
data class DriverStanding(
    val driverId: String,
    val teamId: String,
    val position: Int,
    val points: Int,
    val wins: Int,
    val driverName: String,
    val driverShortName: String?,
    val driverNumber: Int?,
)

// Constructors' championship row. Used by the first-launch default seed to
// pick the top constructor; the two driver slots are then filled from that
// constructor's drivers in the standings.
data class ConstructorStanding(
    val teamId: String,
    val position: Int,
    val points: Int,
    val wins: Int,
    val teamName: String,
    val country: String?,
)

// Top speed (km/h) for a circuit. `null` is the "we don't know" state
// (pre-2023 round, no Qualifying session on the calendar, or both country
// lookups returning 0). §3 renders an empty cell, never a fake "—".
data class TopSpeed(
    val circuitId: String,
    val speedKph: Int,
)
