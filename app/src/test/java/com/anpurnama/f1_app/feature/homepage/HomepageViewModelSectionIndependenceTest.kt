package com.anpurnama.f1_app.feature.homepage

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.f1.model.NextRace
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.Season
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import com.anpurnama.f1_app.test.MainCoroutineRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomepageViewModelSectionIndependenceTest {
    @get:Rule
    val mainRule = MainCoroutineRule()

    private val season = Season(
        year = 2026,
        races = listOf(Race(11, "Hungarian GP", Circuit("hungaroring", "Hungaroring", "4381km", 14, "Mogyorod", "Hungary"), null, 70)),
        completedGp = 0,
        totalKm = 0.0,
        totalLaps = 0,
        progressPercent = 0f,
    )
    private val nextRace = NextRace(
        year = 2026,
        round = 11,
        raceName = "Hungarian GP",
        raceId = "hungarian2026",
        laps = 70,
        circuit = Circuit("hungaroring", "Hungaroring", "4381km", 14, "Mogyorod", "Hungary"),
        raceDate = "2026-07-26",
        raceTime = "13:00:00Z",
    )
    private val drivers = listOf(DriverStanding("driver", "team", 1, 1, 0, "Driver", "DRV", 1))
    private val constructors = listOf(ConstructorStanding("team", 1, 1, 0, "Team", "Country"))

    private fun vm(
        getSeason: suspend (Boolean) -> Outcome<Season> = { Outcome.Success(season) },
        getNextRace: suspend (Boolean) -> Outcome<NextRace?> = { Outcome.Success(nextRace) },
        getDrivers: suspend (Boolean) -> Outcome<List<DriverStanding>> = { Outcome.Success(drivers) },
        getConstructors: suspend (Boolean) -> Outcome<List<ConstructorStanding>> = { Outcome.Success(constructors) },
    ) = HomepageViewModel(
        getSeason = getSeason,
        getNextRace = getNextRace,
        getDriversStandings = getDrivers,
        getConstructorsStandings = getConstructors,
        favoritesFlow = flowOf(Favorites(null, null, null)),
        seedIfEmpty = { _, _ -> },
    )

    @Test
    fun `state has only supported homepage atoms and derives schedule from matching race`() = runTest {
        val schedule = season.races.first().copy(
            schedule = com.anpurnama.f1_app.f1.model.RaceSchedule(
                race = com.anpurnama.f1_app.f1.model.SessionSlot("2026-07-26", "13:00:00Z"),
            ),
        )
        val vm = vm(getSeason = { Outcome.Success(season.copy(races = listOf(schedule))) })
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value as HomepageViewModel.UiState.Sections
        assertTrue(state.weekendSchedule is SectionUiState.Content)
        assertEquals(1, (state.weekendSchedule as SectionUiState.Content).data?.sessions?.size)
    }

    @Test
    fun `season failure does not blank next race and derived schedule is empty`() = runTest {
        val vm = vm(getSeason = { Outcome.Failure("season down") })
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value as HomepageViewModel.UiState.Sections
        assertTrue(state.season is SectionUiState.Error)
        assertTrue(state.nextRace is SectionUiState.Content)
        assertEquals(null, (state.weekendSchedule as SectionUiState.Content).data)
    }

    @Test
    fun `refresh re-fires only network-backed homepage sections`() = runTest {
        val calls = mutableListOf<String>()
        val vm = vm(
            getSeason = { force -> calls += "season:$force"; Outcome.Success(season) },
            getNextRace = { force -> calls += "next:$force"; Outcome.Success(nextRace) },
            getDrivers = { force -> calls += "drivers:$force"; Outcome.Success(drivers) },
            getConstructors = { force -> calls += "constructors:$force"; Outcome.Success(constructors) },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertTrue(calls.all { it.endsWith("false") || it.endsWith("true") })
        assertEquals(4, calls.count { it.endsWith("true") })
        assertTrue(calls.none { it.startsWith("artwork") || it.startsWith("topSpeed") })
    }
}
