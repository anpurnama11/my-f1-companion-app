package com.anpurnama.f1_app.f1.model

// Domain model for a season. The aggregates are pre-computed at mapping time
// (in `SeasonResponseDto.toSeason()`) so the ViewModel never re-walks the
// list — matches the contract from ticket 03.
data class Season(
    val year: Int,
    val races: List<Race>,
    val completedGp: Int,
    val totalKm: Int,
    val totalLaps: Int,
    val progressPercent: Float,
)

data class Race(
    val round: Int,
    val name: String,
    val circuit: Circuit,
    val winnerId: String?,
    val laps: Int?,
)

data class Circuit(
    val id: String,
    val name: String,
    val circuitLengthRaw: String,  // e.g. "5412km" — kept raw, not parsed
    val corners: Int?,
    val city: String?,
    val country: String?,
)
