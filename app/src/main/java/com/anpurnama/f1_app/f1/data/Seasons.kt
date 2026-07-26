package com.anpurnama.f1_app.f1.data

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Season helpers used by the imagery layer (ticket 08). The year
 * guard in [teamImageUrl] / [driverImageUrl] returns `null` for any
 * year < 2026 — v1 is 2026+ only — so callers don't have to check
 * the year themselves; they just pass [currentSeasonYear] in.
 */
object Seasons {
    /**
     * The local-timezone calendar year, computed once at call time.
     * v1 = 2026+ only; off-season (Jan-Mar, when the next year's
     * Cloudinary tree isn't yet populated) and historical lookups
     * (year < 2026) are not in scope.
     */
    fun currentSeasonYear(): Int =
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .year
}
