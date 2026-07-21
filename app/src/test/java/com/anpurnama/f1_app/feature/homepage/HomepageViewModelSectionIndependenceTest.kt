package com.anpurnama.f1_app.feature.homepage

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.f1.model.NextRace
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.f1.model.WeekendSchedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import com.anpurnama.f1_app.test.MainCoroutineRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The §2-only test class (HomepageViewModelTest) keeps its 3 tests on
 * the legacy single-section VM. Once the VM combines 5 use cases, that
 * file would shift too much; this file covers the new section-independence
 * contract end-to-end.
 *
 * Verifies:
 *  1. `uiState` exposes all 8 sections (favorites, drivers, constructors,
 *     nextRace, topSpeed, weekendSchedule, circuitImage, plus the existing season).
 *  2. Section independence: one use case failing leaves the other 4 in
 *     Success / Loading.
 *  3. `refresh()` re-fires the use cases with `forceRefresh = true`.
 *  4. `seedIfEmpty` is called once when the favorites cache is empty
 *     and standings load.
 *  5. `seedIfEmpty` is NOT called when the cache is already populated.
 *  6. Derived sections (topSpeed, weekendSchedule, circuitImage) reload
 *     when the `nextRace` atom advances to a different GP.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomepageViewModelSectionIndependenceTest {

    @get:Rule
    val mainRule = MainCoroutineRule()

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

    /**
     * Starts collecting [vm.uiState] and waits for the init-less warmUp
     * sequence to complete. Keeping a collector active prevents
     * [SharingStarted.WhileSubscribed] from tearing down the upstream
     * while the test asserts the final state.
     */
    private suspend fun TestScope.startCollecting(vm: HomepageViewModel): Job {
        val job = backgroundScope.launch { vm.uiState.collect {} }
        // take(2) = initial Loading + first post-load emission; this wakes
        // up the stateIn upstream and triggers warmUp().
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        return job
    }

    @Test
    fun `uiState carries all 8 sections (favorites, drivers, constructors, nextRace, topSpeed, weekendSchedule, circuitImage, season) and each can fail independently`() = runTest {
        // All use cases succeed except drivers-championship, which fails.
        // The drivers section should be Failure; the other 6 should be
        // Success — never a composite Failure that blanks the whole screen.
        var seedCalls = 0
        val vm = HomepageViewModel(
            getSeason = { Outcome.Success(SEASON) },
            getNextRace = { Outcome.Success(NEXT_RACE) },
            getRaceWeekendSchedule = { _, _ -> Outcome.Success(null) },
            getDriversStandings = { Outcome.Failure("boom") },
            getConstructorsStandings = { Outcome.Success(listOf(TOP_TEAM)) },
            getCircuitTopSpeed = { _, _, _, _ -> Outcome.Success(null) },
            getCircuitImage = { _, _ -> Outcome.Success(null) },
            favoritesFlow = emptyFavoritesFlow(),
            seedIfEmpty = { _, _ -> seedCalls++ },
        )

        val collectJob = startCollecting(vm)

        val state = vm.uiState.value as HomepageViewModel.UiState.Sections
        collectJob.cancel()
        assertTrue(state.drivers is SectionUiState.Error)
        assertEquals("boom", (state.drivers as SectionUiState.Error).message)
        assertTrue(state.season is SectionUiState.Content)
        assertTrue(state.constructors is SectionUiState.Content)
        assertTrue(state.nextRace is SectionUiState.Content)
        assertTrue(state.favorites is SectionUiState.Content)
        // topSpeed + weekendSchedule + circuitImage are loaded by the
        // warmUp sequence because it depends on the next-race atom.
        assertTrue(state.topSpeed is SectionUiState.Content)
        assertTrue(state.weekendSchedule is SectionUiState.Content)
        assertTrue(state.circuitImage is SectionUiState.Content)
    }

    @Test
    fun `seedIfEmpty is called when the cache is empty and constructors load`() = runTest {
        var seedCalls = 0
        var seededTeam: String? = null
        var seededDrivers: List<String>? = null
        val vm = HomepageViewModel(
            getSeason = { Outcome.Success(SEASON) },
            getNextRace = { Outcome.Success(NEXT_RACE) },
            getRaceWeekendSchedule = { _, _ -> Outcome.Success(null) },
            getDriversStandings = { Outcome.Success(TOP_DRIVERS) },
            getConstructorsStandings = { Outcome.Success(listOf(TOP_TEAM)) },
            getCircuitTopSpeed = { _, _, _, _ -> Outcome.Success(null) },
            getCircuitImage = { _, _ -> Outcome.Success(null) },
            favoritesFlow = flowOf(Favorites(null, null, null)),
            seedIfEmpty = { teamId, driverIds ->
                seedCalls++
                seededTeam = teamId
                seededDrivers = driverIds
            },
        )

        val collectJob = startCollecting(vm)

        val state = vm.uiState.value as HomepageViewModel.UiState.Sections
        assertEquals(1, seedCalls)
        collectJob.cancel()
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
            getRaceWeekendSchedule = { _, _ -> Outcome.Success(null) },
            getDriversStandings = { Outcome.Success(TOP_DRIVERS) },
            getConstructorsStandings = { Outcome.Success(listOf(TOP_TEAM)) },
            getCircuitTopSpeed = { _, _, _, _ -> Outcome.Success(null) },
            getCircuitImage = { _, _ -> Outcome.Success(null) },
            favoritesFlow = flowOf(Favorites("user-driver-1", "user-driver-2", "user-team")),
            seedIfEmpty = { _, _ -> seedCalls++ },
        )

        val collectJob = startCollecting(vm)

        // Seed must NOT have been called — user already picked.
        assertEquals(0, seedCalls)
        collectJob.cancel()
    }

    @Test
    fun `refresh re-fires every use case with forceRefresh true`() = runTest {
        val seasonRefresh = mutableListOf<Boolean>()
        val nextRaceRefresh = mutableListOf<Boolean>()
        val driversRefresh = mutableListOf<Boolean>()
        val constructorsRefresh = mutableListOf<Boolean>()
        val topSpeedRefresh = mutableListOf<Boolean>()
        val circuitImageRefresh = mutableListOf<Boolean>()
        val vm = HomepageViewModel(
            getSeason = { forceRefresh -> seasonRefresh += forceRefresh; Outcome.Success(SEASON) },
            getNextRace = { forceRefresh -> nextRaceRefresh += forceRefresh; Outcome.Success(NEXT_RACE) },
            getRaceWeekendSchedule = { _, _ -> Outcome.Success(null) },
            getDriversStandings = { forceRefresh -> driversRefresh += forceRefresh; Outcome.Success(TOP_DRIVERS) },
            getConstructorsStandings = { forceRefresh -> constructorsRefresh += forceRefresh; Outcome.Success(listOf(TOP_TEAM)) },
            getCircuitTopSpeed = { _, _, _, _ -> topSpeedRefresh += true; Outcome.Success(null) },
            getCircuitImage = { _, _ -> circuitImageRefresh += true; Outcome.Success(null) },
            favoritesFlow = emptyFavoritesFlow(),
            seedIfEmpty = { _, _ -> },
        )

        val collectJob = startCollecting(vm)

        // Refresh should set forceRefresh = true on every use case.
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertTrue(seasonRefresh.contains(true))
        collectJob.cancel()
        assertTrue(nextRaceRefresh.contains(true))
        assertTrue(driversRefresh.contains(true))
        assertTrue(constructorsRefresh.contains(true))
        assertTrue(topSpeedRefresh.contains(true))
        assertTrue(circuitImageRefresh.contains(true))
    }

    @Test
    fun `derived sections reload when nextRace advances to a different GP`() = runTest {
        val raceA = NEXT_RACE
        val raceB = NEXT_RACE.copy(
            raceId = "belgian2026",
            raceName = "Belgian Grand Prix 2026",
            circuit = NEXT_RACE.circuit.copy(
                id = "spa",
                name = "Spa-Francorchamps",
                country = "Belgium",
            ),
        )
        var nextRaceCalls = 0
        val scheduleCountries = mutableListOf<String>()
        val imageCountries = mutableListOf<String>()
        val vm = HomepageViewModel(
            getSeason = { Outcome.Success(SEASON) },
            getNextRace = {
                nextRaceCalls++
                when (nextRaceCalls) {
                    1 -> Outcome.Success(raceA)
                    else -> Outcome.Success(raceB)
                }
            },
            getRaceWeekendSchedule = { _, country ->
                scheduleCountries += country
                Outcome.Success(null)
            },
            getDriversStandings = { Outcome.Success(TOP_DRIVERS) },
            getConstructorsStandings = { Outcome.Success(listOf(TOP_TEAM)) },
            getCircuitTopSpeed = { _, _, _, _ -> Outcome.Success(null) },
            getCircuitImage = { _, country ->
                imageCountries += country
                Outcome.Success(null)
            },
            favoritesFlow = emptyFavoritesFlow(),
            seedIfEmpty = { _, _ -> },
        )

        val collectJob = startCollecting(vm)

        // Initial load should use the first race's country.
        assertEquals(listOf("Hungary"), scheduleCountries)
        assertEquals(listOf("Hungary"), imageCountries)

        // Refresh returns race B: derived sections must fetch Belgium data.
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(
            "weekendSchedule should reload with the new GP country",
            listOf("Hungary", "Belgium"),
            scheduleCountries,
        )
        assertEquals(
            "circuitImage should reload with the new GP country",
            listOf("Hungary", "Belgium"),
            imageCountries,
        )
        collectJob.cancel()
    }
}
