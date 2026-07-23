package com.anpurnama.f1_app.feature.round

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.QualifyingResult
import com.anpurnama.f1_app.f1.model.RoundQualifying
import com.anpurnama.f1_app.f1.model.RoundResult
import com.anpurnama.f1_app.f1.model.RoundResults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import com.anpurnama.f1_app.test.MainCoroutineRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression test for the "back from Round detail re-loads Schedule"
 * bug.
 *
 * The RoundViewModel's `uiState` is built as
 *
 *   combine(...).onStart { warmUp() }.stateIn(Lazily)
 *
 * Under `Lazily`, the cold upstream runs from the first subscriber
 * until the VM is cleared (`viewModelScope` cancel). It does NOT stop
 * when the last subscriber leaves, so `onStart { warmUp() }` fires
 * exactly once in the VM's lifetime. The previous `WhileSubscribed(5_000)`
 * setting caused warmUp to re-fire on any re-subscribe past 5s — which
 * is what the user reported when reading a Round detail for >5s and
 * pressing back.
 *
 * This test pins the new contract: warmUp fires once, no matter how
 * many times the screen re-enters composition within the VM's
 * lifetime. The test name deliberately avoids "WhileSubscribed" —
 * the timeout is gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoundViewModelResubscribeAfterTimeoutTest {

    @get:Rule
    val mainRule = MainCoroutineRule()

    private val RACE_RESULTS = RoundResults(
        year = 2024, round = 1, raceName = "Bahrain GP",
        date = "2024-03-02", time = "15:00:00Z",
        circuit = Circuit(id = "bahrain", name = "Bahrain International Circuit",
            circuitLengthRaw = "5412km", corners = 15,
            city = "Sakhir", country = "Bahrain"),
        results = listOf(
            RoundResult(position = "1", points = 26, grid = "1", time = "1:31:44",
                driverId = "maxverstappen", driverName = "Max Verstappen",
                driverShortName = "VER", driverNumber = 33,
                teamId = "redbull", teamName = "Red Bull Racing"),
        ),
    )
    private val QUALY = RoundQualifying(
        year = 2024, round = 1, raceName = "Bahrain GP",
        qualyDate = "2024-03-01", qualyTime = "16:00:00Z",
        circuit = Circuit(id = "bahrain", name = "Bahrain International Circuit",
            circuitLengthRaw = "5412km", corners = 15,
            city = "Sakhir", country = "Bahrain"),
        results = listOf(
            QualifyingResult(gridPosition = 1, q1 = "1:30.031", q2 = "1:29.374", q3 = "1:29.179",
                driverId = "maxverstappen", driverName = "Max Verstappen",
                driverShortName = "VER", driverNumber = 33,
                teamId = "redbull", teamName = "Red Bull Racing"),
        ),
    )

    private fun fakeVm(
        resultCalls: MutableList<Pair<Int, Boolean>>,
        qualyCalls: MutableList<Pair<Int, Boolean>>,
    ): RoundViewModel = RoundViewModel(
        year = 2024,
        round = 1,
        getRoundResults = { _, _, force -> resultCalls += 1 to force; Outcome.Success(RACE_RESULTS) },
        getRoundQualifying = { _, _, force -> qualyCalls += 1 to force; Outcome.Success(QUALY) },
    )

    @Test
    fun `re-subscription within the VM lifetime reuses the loaded state`() = runTest {
        val resultCalls = mutableListOf<Pair<Int, Boolean>>()
        val qualyCalls = mutableListOf<Pair<Int, Boolean>>()
        val vm = fakeVm(resultCalls, qualyCalls)

        // First subscription: warmUp fires both use cases with
        // forceRefresh = false. Pin the Loading → Content transition
        // (practices.md).
        val collectJob1 = backgroundScope.launch { vm.uiState.collect() }
        val firstTwo = vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        assertEquals("first subscription: 1 results call with forceRefresh=false",
            listOf(1 to false), resultCalls)
        assertEquals("first subscription: 1 qualifying call with forceRefresh=false",
            listOf(1 to false), qualyCalls)
        assertTrue("first emission should be Loading on both sections",
            (firstTwo[0] as RoundViewModel.UiState.Sections).results
                is com.anpurnama.f1_app.core.ui.SectionUiState.Loading)
        assertTrue("second emission should be Content on both sections",
            (firstTwo[1] as RoundViewModel.UiState.Sections).results
                is com.anpurnama.f1_app.core.ui.SectionUiState.Content)

        // Unsubscribe (user backs out to Schedule).
        collectJob1.cancel()
        testScheduler.advanceUntilIdle()

        // Re-subscribe well past the previous `WhileSubscribed(5_000)`
        // window. Under `Lazily` the upstream is still hot, so
        // warmUp does NOT re-fire. This is the back-from-detail UX.
        advanceTimeBy(60_000)
        val collectJob2 = backgroundScope.launch { vm.uiState.collect() }
        testScheduler.advanceUntilIdle()
        assertEquals("after 60s: no extra results call (Lazily keeps upstream hot)",
            listOf(1 to false), resultCalls)
        assertEquals("after 60s: no extra qualifying call (Lazily keeps upstream hot)",
            listOf(1 to false), qualyCalls)
        collectJob2.cancel()
        testScheduler.advanceUntilIdle()
    }
}

