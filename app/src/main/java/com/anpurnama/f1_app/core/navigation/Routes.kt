package com.anpurnama.f1_app.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Top-level navigation routes. The 4 tabs of the [NavShell] are the
 * 4 `data object`s here; detail routes (`DriverDetail`, `TeamDetail`,
 * `RoundDetail`, `CircuitDetail` — per ticket 05) land alongside these
 * in later slices.
 *
 * Each route is `@Serializable` and implements [NavKey] so the
 * Navigation 3 `entryProvider` can route by type.
 */
sealed interface Route : NavKey {

    /** The 4 top-level tab destinations. */
    companion object {
        val homepageTabs: Set<Route> = setOf(Homepage, Schedule, Leaderboard, MyTeam)
    }

    @Serializable
    data object Homepage : Route

    @Serializable
    data object Schedule : Route

    @Serializable
    data object Leaderboard : Route

    @Serializable
    data object MyTeam : Route

    /**
     * Detail page for a single circuit. Opened from RoundDetail's
     * circuit block and from Homepage §3's nearest-GP card. The page
     * itself lands in slice 06 — the route is wired in ticket 02 so
     * §3's tap-target navigates correctly, and the slice 06 page
     * just adds the `entry<CircuitDetail>` content.
     */
    @Serializable
    data class CircuitDetail(val circuitId: String) : Route

    /**
     * Detail page for a single round. Opened from a Schedule row tap
     * and (in the future, ticket 07) from the Countdown widget's
     * custom-scheme deep link. Lands in this slice (ticket 03): the
     * page shows the race results, the qualifying results, and a
     * circuit block that links to [CircuitDetail].
     */
    @Serializable
    data class RoundDetail(val year: Int, val round: Int) : Route
}
