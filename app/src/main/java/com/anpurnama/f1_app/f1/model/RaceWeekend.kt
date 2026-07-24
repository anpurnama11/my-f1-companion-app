package com.anpurnama.f1_app.f1.model

import kotlinx.datetime.Instant

/**
 * A single session of a race weekend (FP1, FP2, FP3, Sprint Quali, Sprint,
 * Quali, Race). Drives the Homepage §1 countdown card.
 *
 * `start` is the absolute start of the session in UTC. The display labels
 * come from the typed [SessionType], so session identity never depends on
 * comparing strings.
 */
data class SessionTime(
    val type: SessionType,
    val start: Instant,
) {
    val label: String get() = type.label
    val shortLabel: String get() = type.shortLabel
}

/**
 * Full race-weekend schedule, sessions sorted ascending by [start]. Drives
 * the Homepage §1 countdown card. A successful `null` payload means no
 * valid session slots were available in the primary season schedule — the
 * card renders an empty state, never a fake "—".
 */
data class WeekendSchedule(
    val sessions: List<SessionTime>,
) {
    /**
     * The earliest session whose [SessionTime.start] is still in the
     * future. `null` when every session has started (race weekend over).
     */
    fun nextUpcoming(now: Instant): SessionTime? =
        sessions.firstOrNull { it.start > now }
}

/** Converts f1api.dev schedule slots into the countdown model. */
internal fun RaceSchedule.toWeekendSchedule(): WeekendSchedule? {
    val sessions = activeSessions()
        .mapNotNull { scheduled ->
            scheduled.slot.toInstantOrNull()?.let { start ->
                SessionTime(scheduled.type, start)
            }
        }
        .sortedBy { it.start }

    return WeekendSchedule(sessions).takeIf { it.sessions.isNotEmpty() }
}
