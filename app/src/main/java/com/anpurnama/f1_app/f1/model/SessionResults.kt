package com.anpurnama.f1_app.f1.model

/** A normalized result payload regardless of the source API. */
data class SessionResult(
    val year: Int,
    val round: Int,
    val raceName: String,
    val circuit: Circuit,
    val session: SessionType,
    val raceResults: List<RoundResult> = emptyList(),
    val qualifyingResults: List<QualifyingResult> = emptyList(),
    val practiceResults: List<PracticeResult> = emptyList(),
    val fastestLap: FastestLap? = null,
)

data class PracticeResult(
    val position: Int,
    val time: String?,
    val driverId: String,
    val driverName: String,
    val driverShortName: String?,
    val driverNumber: Int?,
    val teamId: String,
    val teamName: String,
)

data class FastestLap(
    val driverNumber: Int?,
    val driverName: String,
    val driverShortName: String?,
    val time: String,
)

data class FastestPitstop(
    val driverId: String,
    val durationSeconds: Double,
)
