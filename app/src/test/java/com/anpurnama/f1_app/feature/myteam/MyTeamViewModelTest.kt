package com.anpurnama.f1_app.feature.myteam

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.test.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyTeamViewModelTest {

    @get:Rule
    val mainRule = MainCoroutineRule()

    @Test
    fun `first subscription exposes saved favorites and both picker lists`() = runTest {
        val favorites = MutableStateFlow(Favorites("antonelli", "russell", "mercedes"))
        val viewModel = MyTeamViewModel(
            getDriversStandings = { Outcome.Success(drivers) },
            getConstructorsStandings = { Outcome.Success(constructors) },
            favoritesFlow = favorites,
            setDriver1 = {},
            setDriver2 = {},
            setTeam = {},
        )

        viewModel.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            favorites.value,
            (state.favorites as SectionUiState.Content).data,
        )
        assertEquals(
            drivers,
            (state.drivers as SectionUiState.Content).data,
        )
        assertEquals(
            constructors,
            (state.constructors as SectionUiState.Content).data,
        )
    }

    @Test
    fun `selecting a driver replaces only the explicitly chosen slot`() = runTest {
        val favorites = MutableStateFlow(Favorites("antonelli", "russell", "mercedes"))
        val driver1Writes = mutableListOf<String>()
        val driver2Writes = mutableListOf<String>()
        val viewModel = MyTeamViewModel(
            getDriversStandings = { Outcome.Success(drivers) },
            getConstructorsStandings = { Outcome.Success(constructors) },
            favoritesFlow = favorites,
            setDriver1 = { driver1Writes += it },
            setDriver2 = { driver2Writes += it },
            setTeam = {},
        )
        viewModel.uiState.take(2).toList()

        viewModel.selectDriver(DriverSlot.Driver1, "hamilton")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("hamilton"), driver1Writes)
        assertEquals(emptyList<String>(), driver2Writes)
    }

    @Test
    fun `selecting the other slots driver is rejected`() = runTest {
        val favorites = MutableStateFlow(Favorites("antonelli", "russell", "mercedes"))
        val driver2Writes = mutableListOf<String>()
        val viewModel = MyTeamViewModel(
            getDriversStandings = { Outcome.Success(drivers) },
            getConstructorsStandings = { Outcome.Success(constructors) },
            favoritesFlow = favorites,
            setDriver1 = {},
            setDriver2 = { driver2Writes += it },
            setTeam = {},
        )
        viewModel.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        viewModel.selectDriver(DriverSlot.Driver2, "antonelli")
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList<String>(), driver2Writes)
    }

    @Test
    fun `selecting a constructor replaces the constructor slot`() = runTest {
        val favorites = MutableStateFlow(Favorites("antonelli", "russell", "mercedes"))
        val teamWrites = mutableListOf<String>()
        val viewModel = MyTeamViewModel(
            getDriversStandings = { Outcome.Success(drivers) },
            getConstructorsStandings = { Outcome.Success(constructors) },
            favoritesFlow = favorites,
            setDriver1 = {},
            setDriver2 = {},
            setTeam = { teamWrites += it },
        )
        viewModel.uiState.take(2).toList()

        viewModel.selectTeam("ferrari")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("ferrari"), teamWrites)
    }

    private val drivers = listOf(
        DriverStanding(
            driverId = "antonelli",
            teamId = "mercedes",
            position = 1,
            points = 204,
            wins = 6,
            driverName = "Andrea Kimi Antonelli",
            driverShortName = "ANT",
            driverNumber = 12,
        ),
        DriverStanding(
            driverId = "russell",
            teamId = "mercedes",
            position = 2,
            points = 190,
            wins = 4,
            driverName = "George Russell",
            driverShortName = "RUS",
            driverNumber = 63,
        ),
    )

    private val constructors = listOf(
        ConstructorStanding(
            teamId = "mercedes",
            position = 1,
            points = 394,
            wins = 10,
            teamName = "Mercedes Formula 1 Team",
            country = "Germany",
        ),
    )
}
