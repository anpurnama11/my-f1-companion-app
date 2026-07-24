package com.anpurnama.f1_app.f1.model

// Next race. Drives Homepage §1 (countdown card) and §3 (nearest-GP info).
data class NextRace(
    val year: Int,
    val round: Int,
    val raceName: String,
    val raceId: String,
    val laps: Int?,
    val circuit: Circuit,
    val raceDate: String?,  // "YYYY-MM-DD" from schedule.race.date (Sunday)
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
    val teamName: String? = null,
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

data class DriverDetail(
    val driverId: String,
    val name: String,
    val shortName: String?,
    val nationality: String?,
    val birthday: String?,
    val number: Int?,
    val teamId: String,
    val teamName: String,
    val standing: DriverStanding?,
)

data class TeamDetail(
    val teamId: String,
    val wordmark: String,
    val country: String?,
    val firstAppearance: Int?,
    val constructorsChampionships: Int?,
    val driversChampionships: Int?,
    val standing: ConstructorStanding?,
)
