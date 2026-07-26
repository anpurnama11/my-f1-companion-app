package com.anpurnama.f1_app.f1.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// Domain model for a season. The aggregates are pre-computed at mapping time
// (in `SeasonResponseDto.toSeason()`) so the ViewModel never re-walks the
// list — matches the contract from ticket 03.
data class Season(
    val year: Int,
    val races: List<Race>,
    val completedGp: Int,
    /**
     * Total **kilometers** of track distance across completed rounds
     * (sum of `circuitLength / 1000` for races with a winner). Stored
     * as `Double` because the wire value is in meters (e.g. `"5412km"`
     * for Bahrain = 5.412 km) and a 24-race season can carry three
     * decimal places across the sum. Renders as `"%.1f"` to drop the
     * trailing noise.
     */
    val totalKm: Double,
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
    val sprintQualy: SessionSlot? = null,
    val sprintRace: SessionSlot? = null,
    val qualy: SessionSlot? = null,
    val race: SessionSlot? = null,
) {
    /** The five sessions advertised by this weekend, in track order. */
    fun activeSessions(): List<ScheduledSession> {
        val sprint = sprintQualy != null || sprintRace != null
        val ordered = if (sprint) {
            listOf(
                SessionType.FP1 to fp1,
                SessionType.SprintQuali to sprintQualy,
                SessionType.Sprint to sprintRace,
                SessionType.Quali to qualy,
                SessionType.Race to race,
            )
        } else {
            listOf(
                SessionType.FP1 to fp1,
                SessionType.FP2 to fp2,
                SessionType.FP3 to fp3,
                SessionType.Quali to qualy,
                SessionType.Race to race,
            )
        }
        return ordered.mapNotNull { (type, slot) -> slot?.let { ScheduledSession(type, it) } }
    }
}

data class SessionSlot(val date: String?, val time: String?)

fun SessionSlot.toInstantOrNull(): Instant? = runCatching {
    Instant.parse("${date.orEmpty()}T${time.orEmpty()}")
}.getOrNull()

data class ScheduledSession(val type: SessionType, val slot: SessionSlot)

data class Circuit(
    val id: String,
    val name: String,
    val circuitLengthRaw: String,  // e.g. "5412km" — kept raw, not parsed
    val corners: Int?,
    val city: String?,
    val country: String?,
)

fun roundMode(race: Race, now: Instant = Clock.System.now()): RoundMode {
    val raceStart = race.schedule?.race?.toInstantOrNull()
    return when {
        raceStart != null && raceStart > now -> RoundMode.Upcoming
        raceStart != null -> RoundMode.Past
        race.winnerId == null -> RoundMode.Upcoming
        else -> RoundMode.Past
    }
}

/**
 * Per-session-type delay (in hours) after a session's scheduled UTC start
 * before its **Results** button is offered in `UpcomingWeekend`. See ADR 0015.
 *
 * Race is intentionally absent: the race row is gated by [roundMode] (the
 * page flips to `Past` at race start), not this helper.
 */
private val sessionResultBufferHours: Map<SessionType, Int> = mapOf(
    SessionType.FP1 to 6,
    SessionType.FP2 to 6,
    SessionType.FP3 to 6,
    SessionType.SprintQuali to 6,
    SessionType.Sprint to 6,
    SessionType.Quali to 12,
)

/**
 * Heuristic controlling whether the **Results** button is shown for a
 * session row while the round is still in `Upcoming` mode (ADR 0015).
 *
 * Returns `true` only when the session's scheduled UTC start has passed by
 * at least the per-session-type buffer (6h for practices/sprint, 12h for
 * qualifying). The race row returns `false` — [roundMode] owns the race.
 *
 * Null-safe: a missing or unparseable slot ([SessionSlot.toInstantOrNull]
 * null) returns `false` (no button), since `roundMode` only proves the
 * race slot is present, not that FP/quali slots are populated.
 *
 * This is a heuristic, not an authoritative check. Tap reuses the existing
 * `Route.SessionResult` fetch, which owns empty/error states when the API
 * has not posted results yet.
 */
fun sessionResultMayBeAvailable(
    session: SessionType,
    slot: SessionSlot,
    now: Instant = Clock.System.now(),
): Boolean {
    val bufferHours = sessionResultBufferHours[session] ?: return false
    val start = slot.toInstantOrNull() ?: return false
    return now.epochSeconds >= start.epochSeconds + bufferHours * 3600L
}
