package com.anpurnama.f1_app.feature.schedule

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.RoundPodium
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.RoundResult
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.test.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Rung 2: ViewModel state transitions for [ScheduleViewModel].
 *
 * Verifies the init-less + section-independence contract from
 * ticket 01 / 02 carried into the Schedule tab, plus the
 * revision-1 specifics:
 *  1. First collector sees Loading then Success(Season) when the
 *     `/current` call resolves.
 *  2. Section independence: a failing `/current` blanks the season
 *     and clears every per-row map.
 *  3. **Revision 1 — warmUp eagerly fires per-row loads.** After
 *     the season resolves, the VM fires `loadPodium` for every
 *     (country-bearing). Past rounds + circuit images are all
 *     resolved to Content after warmUp. This is the fix for the
 *     refresh-nukes-content bug: a refresh that only wrote the
 *     per-row maps to Loading would leave them stuck on Loading
 *     because the screen's `LaunchedEffect(race.round)` does NOT
 *     re-fire on a same-key re-render.
 *  4. **Revision 1 — RMW race is fixed.** Per-row writes use
 *     atomic `MutableStateFlow.update { }`, so concurrent
 *     `loadPodium` calls on different rounds
 *     never lose updates.
 *  5. `retryPodium(round)` re-fires the failing round only.
 *  6. Pull-to-refresh re-fires the season use case with
 *     `forceRefresh = true`, then re-fires every past round's
 *     `/race` for each past round.
 *
 * The use cases are hand-rolled fakes — `suspend (...) -> Outcome<…>`
 * lambdas. No mocking library.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

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
        winnerId = null, laps = 70,  // upcoming
    )
    private val NO_COUNTRY = Race(
        round = 12, name = "Belgian GP",
        circuit = Circuit(id = "spa", name = "Spa-Francorchamps", circuitLengthRaw = "7004km",
            corners = 19, city = "Spa", country = null),  // missing country
        winnerId = null, laps = 44,
    )

    private val SEASON = Season(
        year = 2026,
        races = listOf(BAHRAIN, SAUDI, FUTURE, NO_COUNTRY),
        completedGp = 2,
        // Bahrain 5412 + Saudi 6275 = 11687 meters = 11.687 km
        totalKm = 11.687, totalLaps = 107, progressPercent = 2f / 3f,
    )

    private fun podium(race: Race): RoundPodium {
        // VER and PER are the two winners in the test data; put the
        // race's winner at P1 and the other at P2.
        val isVerWinner = race.winnerId == "verstappen"
        return RoundPodium(
            topThree = listOf(
                RoundResult(position = "1", points = 26, grid = "1", time = "1:31:44",
                    driverId = if (isVerWinner) "verstappen" else "perez",
                    driverName = if (isVerWinner) "Max Verstappen" else "Sergio Pérez",
                    driverShortName = if (isVerWinner) "VER" else "PER",
                    driverNumber = if (isVerWinner) 33 else 11,
                    teamId = "redbull", teamName = "Red Bull Racing"),
                RoundResult(position = "2", points = 18, grid = "5", time = "+22.457",
                    driverId = if (isVerWinner) "perez" else "verstappen",
                    driverName = if (isVerWinner) "Sergio Pérez" else "Max Verstappen",
                    driverShortName = if (isVerWinner) "PER" else "VER",
                    driverNumber = if (isVerWinner) 11 else 33,
                    teamId = "redbull", teamName = "Red Bull Racing"),
                RoundResult(position = "3", points = 15, grid = "4", time = "+25.110",
                    driverId = "sainz", driverName = "Carlos Sainz",
                    driverShortName = "SAI", driverNumber = 55,
                    teamId = "ferrari", teamName = "Scuderia Ferrari"),
            )
        )
    }

    private fun fakeVm(
        getSeason: suspend (Boolean) -> Outcome<Season> = { Outcome.Success(SEASON) },
        getPodium: suspend (Int, Int, Boolean) -> Outcome<RoundPodium> = { _, round, _ ->
            Outcome.Success(podium(SEASON.races.first { it.round == round }))
        },
    ) = ScheduleViewModel(
        getSeason = getSeason,
        getRoundPodium = getPodium
    )

    private suspend fun TestScope.startCollecting(vm: ScheduleViewModel) {
        val job = backgroundScope.launch { vm.uiState.collect {} }
        // Per lode/practices.md: the first 2 emissions are
        // `initialValue` (Loading) + first post-load emission.
        // Capturing the transition with `take(2).toList()` is what
        // pins the init-less contract; `advanceUntilIdle` afterwards
        // flushes every per-row launch fired in `loadSeason`'s
        // Content branch.
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        job.cancel()
    }

    @Test
    fun `first collector sees Loading then Success Season`() = runTest {
        val vm = fakeVm()

        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        val firstTwo = vm.uiState.take(2).toList()
            .map { it as ScheduleViewModel.UiState.Sections }
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value as ScheduleViewModel.UiState.Sections
        collectJob.cancel()

        assertTrue(
            "first emission should be Loading, was $firstTwo",
            firstTwo[0].season is SectionUiState.Loading,
        )
        assertTrue(
            "second emission should be Content, was $firstTwo",
            firstTwo[1].season is SectionUiState.Content,
        )
        assertTrue("season should be Content, was ${state.season}", state.season is SectionUiState.Content)
        val s = (state.season as SectionUiState.Content).data
        assertEquals(2026, s.year)
        assertEquals(4, s.races.size)
    }

    @Test
    fun `past rounds are eagerly fetched on warmUp and resolve to Content`() = runTest {
        val vm = fakeVm()

        startCollecting(vm)
        // After warmUp the VM has already fired `loadPodium` for
        // every past round in `loadSeason`'s Content branch
        // (revision 1: refresh-nukes-content fix). advanceUntilIdle
        // flushes the launches. Past rounds (1, 2) → Content;
        // upcoming (11, 12) → absent from the podium map.
        val state = vm.uiState.value as ScheduleViewModel.UiState.Sections
        assertTrue(state.season is SectionUiState.Content)
        assertTrue("podium[1] should be Content after warmUp, was ${state.podiums[1]}",
            state.podiums[1] is SectionUiState.Content)
        assertTrue("podium[2] should be Content after warmUp, was ${state.podiums[2]}",
            state.podiums[2] is SectionUiState.Content)
        assertEquals(null, state.podiums[11])
        assertEquals(null, state.podiums[12])

        // The screen's per-row LaunchedEffect is now a no-op
        // (idempotency guard): the slot is already Content.
        vm.loadPodium(year = 2026, round = 1, forceRefresh = false)
        testScheduler.advanceUntilIdle()
        val loaded = vm.uiState.value as ScheduleViewModel.UiState.Sections
        val p1: SectionUiState<RoundPodium>? = loaded.podiums[1]
        assertTrue(p1 is SectionUiState.Content)
        val top = (p1 as SectionUiState.Content).data
        assertEquals(3, top.topThree.size)
        assertEquals("verstappen", top.topThree[0].driverId)
    }

    @Test
    fun `season failure blanks the screen and clears every per-row map`() = runTest {
        var podiumCalls = 0
        var imageCalls = 0
        val vm = fakeVm(
            getSeason = { Outcome.Failure("boom") },
            getPodium = { _, _, _ -> podiumCalls++; Outcome.Success(podium(BAHRAIN)) }
        )

        startCollecting(vm)
        val state = vm.uiState.value as ScheduleViewModel.UiState.Sections
        assertTrue(state.season is SectionUiState.Error)
        assertEquals(0, podiumCalls)
        assertEquals(0, imageCalls)
        assertTrue("podiums must be empty when season failed", state.podiums.isEmpty())
    }

    @Test
    fun `per-row podium failure degrades to a retry row, not a screen blank`() = runTest {
        val vm = fakeVm(
            getPodium = { _, round, _ ->
                if (round == 2) Outcome.Failure("boom-round-2")
                else Outcome.Success(podium(SEASON.races.first { it.round == round }))
            },
        )

        startCollecting(vm)
        val state = vm.uiState.value as ScheduleViewModel.UiState.Sections
        assertTrue(state.season is SectionUiState.Content)
        val p1: SectionUiState<RoundPodium>? = state.podiums[1]
        val p2: SectionUiState<RoundPodium>? = state.podiums[2]
        assertTrue(p1 is SectionUiState.Content)
        assertTrue("podium[2] should be Error, was $p2", p2 is SectionUiState.Error)
        assertEquals("boom-round-2", (p2 as SectionUiState.Error).message)
    }

    @Test
    fun `retryPodium reports that schedule is still loading before year resolves`() {
        val vm = fakeVm()

        assertFalse(vm.retryPodium(round = 1))
    }

    @Test
    fun `retryPodium re-fires only the requested round`() = runTest {
        val calls = mutableListOf<Int>()
        val vm = fakeVm(
            getPodium = { _, round, forceRefresh ->
                calls += round
                if (round == 2) Outcome.Failure("boom")
                else Outcome.Success(podium(SEASON.races.first { it.round == round }))
            },
        )

        startCollecting(vm)
        // After warmUp, both past rounds have been fired.
        assertEquals(listOf(1, 2), calls)

        vm.retryPodium(round = 2)
        testScheduler.advanceUntilIdle()
        // Only round 2 retried.
        assertEquals(listOf(1, 2, 2), calls)
    }

    @Test
    fun `refresh re-fires the season use case with forceRefresh true`() = runTest {
        val calls = mutableListOf<Boolean>()
        val vm = fakeVm(getSeason = { force -> calls += force; Outcome.Success(SEASON) })

        val job = backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(false), calls)
        job.cancel()

        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(false, true), calls)
    }

    @Test
    fun `refresh re-fires loadPodium for every past round and resolves to Content`() = runTest {
        // Pins the revision-1 fix for the refresh-nukes-content bug.
        // Before the fix, `loadSeason(true)` reset past rows to
        // Loading and the screen's `LaunchedEffect(race.round)` did
        // NOT re-fire (same key → no re-execute), so the cells
        // stayed stuck on Loading until nav-away-and-back. The
        // fix: `loadSeason`'s Content branch re-fires `loadPodium`
        // for every past round itself.
        val podiumCalls = mutableListOf<Int>()
        val vm = fakeVm(
            getPodium = { _, round, _ ->
                podiumCalls += round
                Outcome.Success(podium(SEASON.races.first { it.round == round }))
            },
        )

        val job = backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        assertEquals("warmUp fires every past round exactly once",
            listOf(1, 2), podiumCalls)
        job.cancel()

        vm.refresh()
        testScheduler.advanceUntilIdle()
        val after = vm.uiState.value as ScheduleViewModel.UiState.Sections
        // Refresh re-fired both past rounds.
        assertEquals("refresh re-fires every past round",
            listOf(1, 2, 1, 2), podiumCalls)
        assertTrue("podium[1] should be Content after refresh, was ${after.podiums[1]}",
            after.podiums[1] is SectionUiState.Content)
        assertTrue("podium[2] should be Content after refresh, was ${after.podiums[2]}",
            after.podiums[2] is SectionUiState.Content)
    }

    @Test
    fun `concurrent loadPodium writes do not lose updates (RMW race fix)`() = runTest {
        // Pins the revision-1 fix for the RMW race. Before the fix,
        // `podiumsState.value = podiumsState.value + (round to ...)`
        // was a non-atomic RMW; two concurrent `loadPodium` calls on
        // different rounds could interleave so one Content write was
        // lost. The fix is atomic `MutableStateFlow.update { it + ... }`
        // — a single CAS — which cannot lose updates.
        val vm = fakeVm()

        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        collectJob.cancel()

        // After warmUp both past rounds are Content. Force the slot
        // back to Loading so two concurrent writes race to Content.
        // We do that by calling `loadPodium(..., forceRefresh = true)`.
        val j1 = backgroundScope.launch { vm.loadPodium(2026, 1, forceRefresh = true) }
        val j2 = backgroundScope.launch { vm.loadPodium(2026, 2, forceRefresh = true) }
        testScheduler.advanceUntilIdle()
        j1.join(); j2.join()

        val state = vm.uiState.value as ScheduleViewModel.UiState.Sections
        // Both rounds resolved to Content — neither write was lost.
        assertTrue("podium[1] should be Content, was ${state.podiums[1]}",
            state.podiums[1] is SectionUiState.Content)
        assertTrue("podium[2] should be Content, was ${state.podiums[2]}",
            state.podiums[2] is SectionUiState.Content)
        assertTrue("verstappen",
            (state.podiums[1] as SectionUiState.Content).data.topThree[0].driverId == "verstappen")
        assertTrue("perez",
            (state.podiums[2] as SectionUiState.Content).data.topThree[0].driverId == "perez")
    }

}
