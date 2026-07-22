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
    /**
     * FP1/FP2/FP3/qualy/race session times carried over from the
     * `/current` DTO. Null when the f1api.dev response has no
     * `schedule` block for this round (older seasons, partial data).
     * Drives the Schedule tab's upcoming-row session-time list.
     */
    val schedule: RaceSchedule? = null,
)

/**
 * Per-session date+time for a race weekend. Date and time kept as raw
 * strings from f1api.dev (e.g. `"2024-03-02"`, `"15:00:00Z"`) — the
 * screen formats them. Each slot is independently nullable; the DTO
 * has all-null defaults so the whole object is null when absent.
 */
data class RaceSchedule(
    val fp1: SessionSlot? = null,
    val fp2: SessionSlot? = null,
    val fp3: SessionSlot? = null,
    val qualy: SessionSlot? = null,
    val race: SessionSlot? = null,
)

data class SessionSlot(val date: String?, val time: String?)

data class Circuit(
    val id: String,
    val name: String,
    val circuitLengthRaw: String,  // e.g. "5412km" — kept raw, not parsed
    val corners: Int?,
    val city: String?,
    val country: String?,
)
