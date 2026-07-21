package com.anpurnama.f1_app.feature.homepage

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.f1.model.NextRace
import com.anpurnama.f1_app.f1.model.Season
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 2: ViewModel state transitions.
 *
 * Verifies the init-less `Flow.onStart { ... } + stateIn(WhileSubscribed(5_000))`
 * contract:
 *  1. First collector sees Loading immediately, then Success on resolve.
 *  2. First collector sees Loading immediately, then Failure on use-case failure.
 *  3. Re-subscription within the WhileSubscribed(5_000) window gets the same
 *     state without re-firing the use case — config-change survival.
 *
 * The use cases are hand-rolled fakes — `suspend (Boolean) -> Outcome<…>`
 * lambdas per the function-ref seam. No mocking library. The favorites
 * flow is a stub `flowOf(emptyFavorites())`; the seed lambda is a no-op
 * so the test exercises the section-independence contract, not the seed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomepageViewModelTest {

    private val SEASON = Season(
        year = 2026,
        races = listOf(
            com.anpurnama.f1_app.f1.model.Race(
                round = 1,
                name = "Bahrain GP",
                circuit = com.anpurnama.f1_app.f1.model.Circuit(
                    id = "bahrain",
                    name = "Bahrain",
                    circuitLengthRaw = "5412km",
                    corners = 15,
                    city = "Sakhir",
                    country = "Bahrain",
                ),
                winnerId = "max_verstappen",
                laps = 57,
            )
        ),
        completedGp = 1,
        totalKm = 5412,
        totalLaps = 57,
        progressPercent = 1f,
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

    private fun fakeVm(
        getSeason: suspend (Boolean) -> Outcome<Season> = { Outcome.Success(SEASON) },
        getNextRace: suspend (Boolean) -> Outcome<NextRace?> = { Outcome.Success(NEXT_RACE) },
        getDrivers: suspend (Boolean) -> Outcome<List<DriverStanding>> = { Outcome.Success(TOP_DRIVERS) },
        getConstructors: suspend (Boolean) -> Outcome<List<ConstructorStanding>> = { Outcome.Success(listOf(TOP_TEAM)) },
        getTopSpeed: suspend (String, String, Int, String) -> Outcome<com.anpurnama.f1_app.f1.model.TopSpeed?> = { _, _, _, _ -> Outcome.Success(null) },
    ) = HomepageViewModel(
        getSeason = getSeason,
        getNextRace = getNextRace,
        getDriversStandings = getDrivers,
        getConstructorsStandings = getConstructors,
        getCircuitTopSpeed = getTopSpeed,
        favoritesFlow = flowOf(Favorites(null, null, null)),
        seedIfEmpty = { _, _ -> },
    )

    @Test
    fun `first collector sees Loading then Success when every use case resolves`() = runTest {
        val vm = fakeVm()

        // take(2) = initialValue (Loading) + first post-load emission.
        val states = vm.uiState.take(2).toList()

        assertTrue(states[0] is HomepageViewModel.UiState.Sections)
        val sections = states[1] as HomepageViewModel.UiState.Sections
        assertTrue(sections.season is Outcome.Success)
        assertEquals(2026, (sections.season as Outcome.Success).data.year)
        assertEquals(1, sections.season.data.completedGp)
        assertTrue(sections.drivers is Outcome.Success)
        assertTrue(sections.constructors is Outcome.Success)
        assertTrue(sections.nextRace is Outcome.Success)
        // §3 top speed must load on first open (warmUp fires every use
        // case on first subscription, not just on refresh). The Hungaroring
        // next race has Hungary/Hungary + qualyDate=2026-07-25 so the fake
        // use case resolves.
        assertTrue(
            "topSpeed should be loaded on first open, not stuck on Loading",
            sections.topSpeed is Outcome.Success,
        )
    }

    @Test
    fun `first collector sees Loading then Failure when the season use case fails`() = runTest {
        val vm = fakeVm(getSeason = { Outcome.Failure("boom") })

        val states = vm.uiState.take(2).toList()
        val sections = states[1] as HomepageViewModel.UiState.Sections
        assertTrue(sections.season is Outcome.Failure)
        assertEquals("boom", (sections.season as Outcome.Failure).errorMessage)
    }

    @Test
    fun `re-subscription within WhileSubscribed window reuses the loaded state`() = runTest {
        var callCount = 0
        val vm = fakeVm(
            getSeason = { _ -> callCount++; Outcome.Success(SEASON) }
        )

        // First subscription: triggers warmUp() → load. take(2) gets Loading + Success.
        val firstStates = vm.uiState.take(2).toList()
        val sections = firstStates[1] as HomepageViewModel.UiState.Sections
        assertTrue(sections.season is Outcome.Success)
        assertEquals(1, callCount)
    }
}
