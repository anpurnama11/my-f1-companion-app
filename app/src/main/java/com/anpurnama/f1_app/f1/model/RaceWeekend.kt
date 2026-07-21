package com.anpurnama.f1_app.f1.model

import kotlinx.datetime.Instant

/**
 * A single session of a race weekend (FP1, FP2, FP3, Sprint Quali, Sprint,
 * Quali, Race). Drives the Homepage §1 countdown card.
 *
 * `start` is the absolute start of the session in UTC (OpenF1 `date_start`
 * is always `+00:00`). `label` is the long form for body text
 * ("Practice 1"), `shortLabel` is the chip form ("FP1").
 */
data class SessionTime(
    val label: String,
    val shortLabel: String,
    val start: Instant,
)

/**
 * Full race-weekend schedule, sessions sorted ascending by [start]. Drives
 * the Homepage §1 countdown card. A successful `null` payload (from
 * [com.anpurnama.f1_app.f1.GetRaceWeekendScheduleUseCase]) means the
 * weekend is not on the OpenF1 calendar (pre-2023, no sessions returned,
 * off-season) — the card renders an empty state, never a fake "—".
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
