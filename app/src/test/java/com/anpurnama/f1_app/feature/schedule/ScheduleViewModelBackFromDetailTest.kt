package com.anpurnama.f1_app.feature.schedule

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.RoundPodium
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.test.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression test for the user-reported "back from Round detail reloads
 * Schedule" bug.
 *
 * The ScheduleViewModel's `uiState` is built as
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
 * Test sequence mirrors the back-from-Round detail UX:
 *   1. Subscribe to ScheduleViewModel (Schedule tab enters composition)
 *   2. Season + per-row podiums resolve
 *   3. User taps a row → ScheduleScreen leaves composition
 *   4. User reads Round detail for >5s
 *   5. User presses back → ScheduleScreen re-enters composition
 *   6. Assertion: `getSeason` was called exactly once (not again)
 *   7. Assertion: `getRoundPodium` was called exactly N times (one
 *      per past round, no re-fire on back-pop)
 *
 * The data is server-cached (10-min f1api.dev per
 * `lode/wayfinder/f1app/tickets/03-data-layer-and-refresh.md`), so a
 * hot `Lazily` upstream is cheap; a re-fire would only be wasteful
 * churn.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelBackFromDetailTest {

    @get:Rule
    val mainRule = MainCoroutineRule()

    private val BAHRAIN = Race(
        round = 1, name = "Bahrain GP",
        circuit = Circuit(id = "bahrain", name = "Bahrain", circuitLengthRaw = "5412km",
            corners = 15, city = "Sakhir", country = "Bahrain"),
        winnerId = "verstappen", laps = 57,
    )
    private val SAUDI = Race(
        round = 2, name = "Saudi Arabian GP",
        circuit = Circuit(id = "jeddah", name = "Jeddah", circuitLengthRaw = "6275km",
            corners = 27, city = "Jeddah", country = "Saudi Arabia"),
        winnerId = "perez", laps = 50,
    )
    private val FUTURE = Race(
        round = 11, name = "Hungarian GP",
        circuit = Circuit(id = "hungaroring", name = "Hungaroring", circuitLengthRaw = "4381km",
            corners = 14, city = "Mogyorod", country = "Hungary"),
        winnerId = null, laps = 70,
    )

    private val SEASON = Season(
        year = 2024,
        races = listOf(BAHRAIN, SAUDI, FUTURE),
        completedGp = 2,
        totalKm = 11.687,
        totalLaps = 107,
        progressPercent = 2f / 24f,
    )

    private fun fakeVm(
        seasonCalls: MutableList<Boolean>,
        podiumCalls: MutableList<Pair<Int, Boolean>>,
    ): ScheduleViewModel = ScheduleViewModel(
        getSeason = { force ->
            seasonCalls += force
            Outcome.Success(SEASON)
        },
        getRoundPodium = { _, round, force ->
            podiumCalls += round to force
            Outcome.Success(
                RoundPodium(
                    topThree = listOf(
                        com.anpurnama.f1_app.f1.model.RoundResult(
                            position = "1", points = 26, grid = "1", time = "1:31:44",
                            driverId = "verstappen", driverName = "Max Verstappen",
                            driverShortName = "VER", driverNumber = 33,
                            teamId = "redbull", teamName = "Red Bull Racing",
                        ),
                    ),
                ),
            )
        },
    )

    @Test
    fun `back from Round detail past 5s window does NOT re-fire warmUp`() = runTest {
        val seasonCalls = mutableListOf<Boolean>()
        val podiumCalls = mutableListOf<Pair<Int, Boolean>>()
        val vm = fakeVm(seasonCalls, podiumCalls)

        // Step 1: Schedule tab enters composition. warmUp fires
        // /current (forceRefresh=false) + one /race per past round.
        val collectJob1 = backgroundScope.launch { vm.uiState.collect() }
        val firstTwo = vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        assertEquals("first subscription: 1 season call with forceRefresh=false",
            listOf(false), seasonCalls)
        // 2 past rounds in the test season → 2 podium calls.
        assertEquals("first subscription: 1 podium call per past round (forceRefresh=false)",
            listOf(1 to false, 2 to false), podiumCalls)
        assertTrue("first emission should be Loading on season",
            (firstTwo[0] as ScheduleViewModel.UiState.Sections).season
                is com.anpurnama.f1_app.core.ui.SectionUiState.Loading)
        assertTrue("second emission should be Content on season",
            (firstTwo[1] as ScheduleViewModel.UiState.Sections).season
                is com.anpurnama.f1_app.core.ui.SectionUiState.Content)

        // Step 3: user taps a row → ScheduleScreen leaves composition.
        collectJob1.cancel()
        testScheduler.advanceUntilIdle()

        // Step 4: user reads Round detail for 60s (well past the
        // previous 5s WhileSubscribed window).
        advanceTimeBy(60_000)

        // Step 5: user presses back → ScheduleScreen re-enters
        // composition.
        val collectJob2 = backgroundScope.launch { vm.uiState.collect() }
        testScheduler.advanceUntilIdle()

        // Step 6+7: warmUp did NOT re-fire. The hot upstream keeps
        // serving the existing `StateFlow` value. This is the
        // user-reported bug regression.
        assertEquals("back-pop after 60s: no extra season call (Lazily keeps upstream hot)",
            listOf(false), seasonCalls)
        assertEquals("back-pop after 60s: no extra podium calls (Lazily keeps upstream hot)",
            listOf(1 to false, 2 to false), podiumCalls)
        collectJob2.cancel()
        testScheduler.advanceUntilIdle()
    }
}
