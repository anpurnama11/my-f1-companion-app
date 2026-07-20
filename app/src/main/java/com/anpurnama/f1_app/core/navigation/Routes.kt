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

    @Serializable
    data object Homepage : Route

    @Serializable
    data object Schedule : Route

    @Serializable
    data object Leaderboard : Route

    @Serializable
    data object MyTeam : Route
}
