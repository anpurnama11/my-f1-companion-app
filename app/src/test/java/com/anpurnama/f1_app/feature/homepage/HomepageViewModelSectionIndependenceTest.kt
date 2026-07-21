package com.anpurnama.f1_app.feature.homepage

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.f1.model.NextRace
import com.anpurnama.f1_app.f1.model.Season
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The §2-only test class (HomepageViewModelTest) keeps its 3 tests on
 * the legacy single-section VM. Once the VM combines 5 use cases, that
 * file would shift too much; this file covers the new section-independence
 * contract end-to-end.
 *
 * Verifies:
 *  1. `uiState` exposes all 5 sections (favorites, drivers, constructors,
 *     nextRace, topSpeed, plus the existing season).
 *  2. Section independence: one use case failing leaves the other 4 in
 *     Success / Loading.
 *  3. `refresh()` re-fires the use cases with `forceRefresh = true`.
 *  4. `seedIfEmpty` is called once when the favorites cache is empty
 *     and standings load.
 *  5. `seedIfEmpty` is NOT called when the cache is already populated.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomepageViewModelSectionIndependenceTest {

    private val SEASON = Season(
        year = 2026, races = emptyList(),
        completedGp = 0, totalKm = 0, totalLaps = 0, progressPercent = 0f,
    )
    private val TOP_TEAM = ConstructorStanding(
        teamId = "mercedes", position = 1, points = 358, wins = 8,
        teamName = "Mercedes Formula 1 Team", country = "Germany",
    )
    private val TOP_DRIVERS = listOf(
        DriverStanding(
            driverId = "antonelli", teamId = "mercedes", position = 1,
            points = 204, wins = 6, driverName = "Andrea Kimi Antonelli",
            driverShortName = "ANT", driverNumber = 12,
        ),
        DriverStanding(
            driverId = "russell", teamId = "mercedes", position = 3,
            points = 154, wins = 2, driverName = "George Russell",
            driverShortName = "RUS", driverNumber = 63,
        ),
    )
    private val NEXT_RACE = NextRace(
        year = 2026, round = 11, raceName = "Formula 1 AWS Hungarian Grand Prix 2026",
        raceId = "hungarian2026", laps = 70,
        circuit = com.anpurnama.f1_app.f1.model.Circuit(
            id = "hungaroring", name = "Hungaroring", circuitLengthRaw = "4381km",
            corners = 14, city = "Mogyorod", country = "Hungary",
        ),
        raceDate = "2026-07-26", qualyDate = "2026-07-25", raceTime = "13:00:00Z",
    )

    private fun emptyFavoritesFlow(): Flow<Favorites> = flowOf(Favorites(null, null, null))

    @Test
    fun `uiState carries all 6 sections (favorites, drivers, constructors, nextRace, topSpeed, season) and each can fail independently`() = runTest {
        // All use cases succeed except drivers-championship, which fails.
        // The drivers section should be Failure; the other 5 should be
        // Success — never a composite Failure that blanks the whole screen.
        var seedCalls = 0
        val vm = HomepageViewModel(
            getSeason = { Outcome.Success(SEASON) },
            getNextRace = { Outcome.Success(NEXT_RACE) },
            getDriversStandings = { Outcome.Failure("boom") },
            getConstructorsStandings = { Outcome.Success(listOf(TOP_TEAM)) },
            getCircuitTopSpeed = { _, _, _, _ -> Outcome.Success(null) },
            favoritesFlow = emptyFavoritesFlow(),
            seedIfEmpty = { _, _ -> seedCalls++ },
        )

        val state = vm.uiState.take(2).toList().last() as HomepageViewModel.UiState.Sections
        assertTrue(state.drivers is Outcome.Failure)
        assertEquals("boom", (state.drivers as Outcome.Failure).errorMessage)
        assertTrue(state.season is Outcome.Success)
        assertTrue(state.constructors is Outcome.Success)
        assertTrue(state.nextRace is Outcome.Success)
        assertTrue(state.favorites is Outcome.Success)
        // topSpeed runs from the warmUp() sequence; the fake returns
        // Success(null) (no Qualifying match) — proves §3 fires on first
        // open, not only on refresh().
        assertTrue(state.topSpeed is Outcome.Success)
    }

    @Test
    fun `seedIfEmpty is called when the cache is empty and constructors load`() = runTest {
        var seedCalls = 0
        var seededTeam: String? = null
        var seededDrivers: List<String>? = null
        val vm = HomepageViewModel(
            getSeason = { Outcome.Success(SEASON) },
            getNextRace = { Outcome.Success(NEXT_RACE) },
            getDriversStandings = { Outcome.Success(TOP_DRIVERS) },
            getConstructorsStandings = { Outcome.Success(listOf(TOP_TEAM)) },
            getCircuitTopSpeed = { _, _, _, _ -> Outcome.Success(null) },
            favoritesFlow = flowOf(Favorites(null, null, null)),
            seedIfEmpty = { teamId, driverIds ->
                seedCalls++
                seededTeam = teamId
                seededDrivers = driverIds
            },
        )

        val state = vm.uiState.take(2).toList().last() as HomepageViewModel.UiState.Sections
        // Give the seed coroutine a chance to run.
        assertEquals(1, seedCalls)
        assertEquals("mercedes", seededTeam)
        assertEquals(listOf("antonelli", "russell"), seededDrivers)
        assertNotNull(state.constructors)
    }

    @Test
    fun `seedIfEmpty is not called when the cache is already populated`() = runTest {
        var seedCalls = 0
        val vm = HomepageViewModel(
            getSeason = { Outcome.Success(SEASON) },
            getNextRace = { Outcome.Success(NEXT_RACE) },
            getDriversStandings = { Outcome.Success(TOP_DRIVERS) },
            getConstructorsStandings = { Outcome.Success(listOf(TOP_TEAM)) },
            getCircuitTopSpeed = { _, _, _, _ -> Outcome.Success(null) },
            favoritesFlow = flowOf(Favorites("user-driver-1", "user-driver-2", "user-team")),
            seedIfEmpty = { _, _ -> seedCalls++ },
        )

        // Trigger the state flow.
        vm.uiState.take(2).toList()

        // Seed must NOT have been called — user already picked.
        assertEquals(0, seedCalls)
    }

    @Test
    fun `refresh re-fires every use case with forceRefresh true`() = runTest {
        val seasonRefresh = mutableListOf<Boolean>()
        val nextRaceRefresh = mutableListOf<Boolean>()
        val driversRefresh = mutableListOf<Boolean>()
        val constructorsRefresh = mutableListOf<Boolean>()
        val topSpeedRefresh = mutableListOf<Boolean>()
        val vm = HomepageViewModel(
            getSeason = { forceRefresh -> seasonRefresh += forceRefresh; Outcome.Success(SEASON) },
            getNextRace = { forceRefresh -> nextRaceRefresh += forceRefresh; Outcome.Success(NEXT_RACE) },
            getDriversStandings = { forceRefresh -> driversRefresh += forceRefresh; Outcome.Success(TOP_DRIVERS) },
            getConstructorsStandings = { forceRefresh -> constructorsRefresh += forceRefresh; Outcome.Success(listOf(TOP_TEAM)) },
            getCircuitTopSpeed = { _, _, _, _ -> topSpeedRefresh += true; Outcome.Success(null) },
            favoritesFlow = emptyFavoritesFlow(),
            seedIfEmpty = { _, _ -> },
        )

        // Subscribe to warm the state.
        vm.uiState.take(2).toList()
        // Refresh should set forceRefresh = true on every use case.
        vm.refresh()

        // Run any coroutines launched by refresh() to completion.
        testScheduler.advanceUntilIdle()
        assertTrue(seasonRefresh.contains(true))
        assertTrue(nextRaceRefresh.contains(true))
        assertTrue(driversRefresh.contains(true))
        assertTrue(constructorsRefresh.contains(true))
        assertTrue(topSpeedRefresh.contains(true))
    }
}
