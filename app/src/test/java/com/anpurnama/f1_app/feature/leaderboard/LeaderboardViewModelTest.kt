package com.anpurnama.f1_app.feature.leaderboard

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.test.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModelTest {

    @get:Rule
    val mainRule = MainCoroutineRule()

    private val drivers = listOf(
        DriverStanding(
            driverId = "antonelli", teamId = "mercedes", position = 1,
            points = 204, wins = 6, driverName = "Andrea Kimi Antonelli",
            driverShortName = "ANT", driverNumber = 12,
        ),
    )
    private val teams = listOf(
        ConstructorStanding(
            teamId = "mercedes", position = 1, points = 358, wins = 8,
            teamName = "Mercedes Formula 1 Team", country = "Germany",
        ),
    )

    @Test
    fun `first subscription loads drivers and constructors independently`() = runTest {
        val vm = LeaderboardViewModel(
            getDriversStandings = { Outcome.Success(drivers) },
            getConstructorsStandings = { Outcome.Success(teams) },
        )

        val states = vm.uiState.take(2).toList().map { it as LeaderboardViewModel.UiState.Sections }
        val final = states.last()

        assertTrue(states.first().drivers is SectionUiState.Loading)
        assertTrue(final.drivers is SectionUiState.Content)
        assertTrue(final.constructors is SectionUiState.Content)
        assertEquals("antonelli", (final.drivers as SectionUiState.Content).data.single().driverId)
        assertEquals("mercedes", (final.constructors as SectionUiState.Content).data.single().teamId)
    }

    @Test
    fun `driver failure does not blank constructors and refresh uses no cache`() = runTest {
        val refreshFlags = mutableListOf<Boolean>()
        val vm = LeaderboardViewModel(
            getDriversStandings = { force -> refreshFlags += force; Outcome.Failure("drivers unavailable") },
            getConstructorsStandings = { force -> refreshFlags += force; Outcome.Success(teams) },
        )
        vm.uiState.take(2).toList()

        val initial = vm.uiState.value as LeaderboardViewModel.UiState.Sections
        assertTrue(initial.drivers is SectionUiState.Error)
        assertTrue(initial.constructors is SectionUiState.Content)

        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(false, false, true, true), refreshFlags)
    }
}
