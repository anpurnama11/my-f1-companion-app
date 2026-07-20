package com.anpurnama.f1_app.feature.homepage

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.Season
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 2: ViewModel state transitions.
 *
 * Verifies the init-less `Flow.onStart { load() } + stateIn(WhileSubscribed(5_000))`
 * contract:
 *  1. First collector sees Loading immediately, then Success on resolve.
 *  2. First collector sees Loading immediately, then Failure on use-case failure.
 *  3. Re-subscription within the WhileSubscribed(5_000) window gets the same
 *     state without re-firing the use case — config-change survival.
 *
 * The use case is a hand-rolled fake — a `suspend (Boolean) -> Outcome<Season>`
 * lambda per the function-ref seam. No mocking library.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomepageViewModelTest {

    private val SEASON = Season(
        year = 2026,
        races = listOf(
            Race(
                round = 1,
                name = "Bahrain GP",
                circuit = Circuit(
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

    @Test
    fun `first collector sees Loading then Success when use case resolves`() = runTest {
        val vm = HomepageViewModel { Outcome.Success(SEASON) }

        // take(2) = initialValue (Loading) + first post-load emission (Success).
        val states = vm.uiState.take(2).toList()

        assertEquals(HomepageViewModel.UiState.Loading, states[0])
        val success = states[1] as HomepageViewModel.UiState.Success
        assertEquals(2026, success.season.year)
        assertEquals(1, success.season.completedGp)
    }

    @Test
    fun `first collector sees Loading then Failure when use case fails`() = runTest {
        val vm = HomepageViewModel { Outcome.Failure("boom") }

        val states = vm.uiState.take(2).toList()

        assertEquals(HomepageViewModel.UiState.Loading, states[0])
        val failure = states[1] as HomepageViewModel.UiState.Failure
        assertEquals("boom", failure.errorMessage)
    }

    @Test
    fun `re-subscription within WhileSubscribed window reuses the loaded state`() = runTest {
        var callCount = 0
        val vm = HomepageViewModel { _ ->
            callCount++
            Outcome.Success(SEASON)
        }

        // First subscription: triggers onStart → load. take(2) gets Loading + Success.
        val firstStates = vm.uiState.take(2).toList()
        assertEquals(HomepageViewModel.UiState.Loading, firstStates[0])
        assertTrue(firstStates[1] is HomepageViewModel.UiState.Success)
        assertEquals(1, callCount)

        // Immediate re-subscription (well inside the 5_000ms window) must
        // see the cached Success and NOT re-fire the use case.
        val cached = vm.uiState.first()
        assertTrue(cached is HomepageViewModel.UiState.Success)
        assertEquals(1, callCount)
    }
}
