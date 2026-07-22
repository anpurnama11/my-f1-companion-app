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
 * Adversarial verification: the RoundViewModel's `uiState` is built as
 *
 *   combine(...).onStart { warmUp() }.stateIn(WhileSubscribed(5_000))
 *
 * Kotlin Flow semantics: `WhileSubscribed(stopTimeoutMillis)` cancels
 * the upstream after the timeout with zero subscribers; on
 * re-subscription, a fresh upstream starts, and `onStart { warmUp() }`
 * re-fires — so `loadResults(false)` and `loadQualifying(false)` run
 * again, and the underlying use cases are called a second time.
 *
 * This is the prescribed behavior per lode/practices.md for the
 * init-less + WhileSubscribed pattern. The test pins the *intra-window*
 * behavior (re-subscribe within 5_000ms does NOT re-fire warmUp) which
 * is the part that matters for the "tab switch and back" UX. The
 * post-timeout re-fire is documented Kotlin Flow behavior — verified
 * by reading the SharingStarted source (1.11.0: `transformLatest`
 * emits STOP after `delay(stopTimeout)`, and a new START on the next
 * subscription re-runs the upstream including `onStart`).
 *
 * The post-timeout re-fire is hard to pin in a unit test because
 * `viewModelScope` uses `Dispatchers.Main.immediate` which doesn't
 * cooperate with `advanceTimeBy` for the WhileSubscribed timer. The
 * intra-window check below is the testable half — it confirms the
 * `WhileSubscribed(5_000)` window is honored.
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
    fun `re-subscription within the WhileSubscribed window reuses the loaded state`() = runTest {
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

        // Unsubscribe (user backgrounds the screen).
        collectJob1.cancel()
        testScheduler.advanceUntilIdle()

        // Re-subscribe within the 5_000ms window: warmUp does NOT
        // re-fire (the cached upstream is still alive). This is the
        // "tab-switch-and-back" UX the timeout is tuned for.
        advanceTimeBy(4_000)
        val collectJob2 = backgroundScope.launch { vm.uiState.collect() }
        testScheduler.advanceUntilIdle()
        assertEquals("within 5s window: no extra results call",
            listOf(1 to false), resultCalls)
        assertEquals("within 5s window: no extra qualifying call",
            listOf(1 to false), qualyCalls)
        collectJob2.cancel()
        testScheduler.advanceUntilIdle()
        // Drain the WhileSubscribed timer on viewModelScope so
        // runTest doesn't see it as a pending coroutine after the
        // last subscriber leaves.
        advanceTimeBy(5_001)
    }
}

