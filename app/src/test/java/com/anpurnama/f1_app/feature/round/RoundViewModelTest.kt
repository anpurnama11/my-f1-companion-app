package com.anpurnama.f1_app.feature.round

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.ResourceSnapshot
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.cache.CacheResourceKeys
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.QualifyingResult
import com.anpurnama.f1_app.f1.model.RoundQualifying
import com.anpurnama.f1_app.f1.model.RoundResult
import com.anpurnama.f1_app.f1.model.RoundResults
import com.anpurnama.f1_app.f1.model.Season
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import com.anpurnama.f1_app.test.MainCoroutineRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Rung 2: ViewModel state transitions for [RoundViewModel].
 *
 * Verifies the init-less + section-independence contract from ticket
 * 01 / 02 carried into the Round detail screen:
 *  1. First collector sees Loading then Success on both the race
 *     results and the qualifying results.
 *  2. Section independence: if the race call fails, the qualifying
 *     cell still loads (or fails independently).
 *  3. The constructor params (`year`, `round`) are honored — the
 *     use cases are called with those args.
 *  4. Pull-to-refresh re-fires both use cases with
 *     `forceRefresh = true`.
 *  5. Re-subscription within `WhileSubscribed(5_000)` reuses the
 *     cached state without re-firing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoundViewModelTest {

    @get:Rule
    val mainRule = MainCoroutineRule()

    private val RACE_RESULTS = RoundResults(
        year = 2024,
        round = 1,
        raceName = "Bahrain GP",
        date = "2024-03-02", time = "15:00:00Z",
        circuit = Circuit(
            id = "bahrain", name = "Bahrain International Circuit",
            circuitLengthRaw = "5412km", corners = 15,
            city = "Sakhir", country = "Bahrain",
        ),
        results = listOf(
            RoundResult(position = "1", points = 26, grid = "1", time = "1:31:44",
                driverId = "maxverstappen", driverName = "Max Verstappen",
                driverShortName = "VER", driverNumber = 33,
                teamId = "redbull", teamName = "Red Bull Racing"),
        ),
    )

    private val QUALY = RoundQualifying(
        year = 2024,
        round = 1,
        raceName = "Bahrain GP",
        qualyDate = "2024-03-01", qualyTime = "16:00:00Z",
        circuit = Circuit(
            id = "bahrain", name = "Bahrain International Circuit",
            circuitLengthRaw = "5412km", corners = 15,
            city = "Sakhir", country = "Bahrain",
        ),
        results = listOf(
            QualifyingResult(gridPosition = 1, q1 = "1:30.031", q2 = "1:29.374", q3 = "1:29.179",
                driverId = "maxverstappen", driverName = "Max Verstappen",
                driverShortName = "VER", driverNumber = 33,
                teamId = "redbull", teamName = "Red Bull Racing"),
        ),
    )

    private val ROUND_SEASON_2024 = Season(
        year = 2024,
        races = listOf(
            Race(
                round = 1,
                name = "Bahrain GP",
                circuit = Circuit(
                    id = "bahrain", name = "Bahrain", circuitLengthRaw = "5412km",
                    corners = 15, city = "Sakhir", country = "Bahrain",
                ),
                winnerId = "max_verstappen",
                laps = 57,
            )
        ),
        completedGp = 1,
        totalKm = 5.412,
        totalLaps = 57,
        progressPercent = 1f,
    )

    private fun fakeVm(
        year: Int = 2024,
        round: Int = 1,
        getResults: suspend (Int, Int, Boolean) -> Outcome<RoundResults> = { _, _, _ -> Outcome.Success(RACE_RESULTS) },
        getQualifying: suspend (Int, Int, Boolean) -> Outcome<RoundQualifying> = { _, _, _ -> Outcome.Success(QUALY) },
    ) = RoundViewModel(
        year = year,
        round = round,
        getRoundResults = getResults,
        getRoundQualifying = getQualifying,
    )

    private suspend fun TestScope.startCollecting(vm: RoundViewModel): Job {
        val job = backgroundScope.launch { vm.uiState.collect {} }
        // Per lode/practices.md: the first 2 emissions are
        // `initialValue` (Loading on both sections) + first post-load
        // emission. Capturing the transition with `take(2).toList()` is
        // what pins the init-less contract.
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        return job
    }

    @Test
    fun `first collector sees Loading then Success on both sections`() = runTest {
        val vm = fakeVm()

        // Loading → Content transition: take(2) captures the
        // initialValue sentinel (both sections Loading) + the first
        // post-load emission (both sections Content). This is the
        // prescribed init-less VM test (lode/practices.md).
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        val firstTwo = vm.uiState.take(2).toList()
            .map { it as RoundViewModel.UiState.Sections }
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value as RoundViewModel.UiState.Sections
        collectJob.cancel()

        assertTrue(
            "first emission should be Loading on both sections, was $firstTwo",
            firstTwo[0].results is SectionUiState.Loading &&
                firstTwo[0].qualifying is SectionUiState.Loading,
        )
        assertTrue(
            "second emission should be Content on both sections, was $firstTwo",
            firstTwo[1].results is SectionUiState.Content &&
                firstTwo[1].qualifying is SectionUiState.Content,
        )
        assertTrue(state.results is SectionUiState.Content)
        assertTrue(state.qualifying is SectionUiState.Content)
        assertEquals(2024, (state.results as SectionUiState.Content).data.year)
        assertEquals(1, (state.qualifying as SectionUiState.Content).data.round)
    }

    @Test
    fun `the round constructor params are forwarded to the use cases`() = runTest {
        val calls = mutableListOf<Pair<Int, Int>>()
        val vm = fakeVm(
            year = 2024,
            round = 7,
            getResults = { y, r, _ -> calls += y to r; Outcome.Success(RACE_RESULTS) },
            getQualifying = { y, r, _ -> calls += y to r; Outcome.Success(QUALY) },
        )

        val job = startCollecting(vm)
        job.cancel()

        // One fire per use case on first load (no refresh). Both for (2024, 7).
        assertEquals(2, calls.size)
        assertTrue(calls.all { it == 2024 to 7 })
    }

    @Test
    fun `race results failure does not blank the qualifying section`() = runTest {
        val vm = fakeVm(
            getResults = { _, _, _ -> Outcome.Failure("boom-results") },
        )

        val job = startCollecting(vm)
        val state = vm.uiState.value as RoundViewModel.UiState.Sections
        job.cancel()

        assertTrue(state.results is SectionUiState.Error)
        assertEquals("boom-results", (state.results as SectionUiState.Error).message)
        // Qualifying loaded independently — its own outcome unaffected.
        assertTrue(state.qualifying is SectionUiState.Content)
    }

    @Test
    fun `refresh re-fires both use cases with forceRefresh true`() = runTest {
        val calls = mutableListOf<Boolean>()
        val vm = fakeVm(
            getResults = { _, _, force -> calls += force; Outcome.Success(RACE_RESULTS) },
            getQualifying = { _, _, force -> calls += force; Outcome.Success(QUALY) },
        )

        val job = startCollecting(vm)
        assertEquals(listOf(false, false), calls)
        job.cancel()

        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(false, false, true, true), calls)
    }

    @Test
    fun `re-subscription within WhileSubscribed window reuses the loaded state`() = runTest {
        var callCount = 0
        val vm = fakeVm(
            getResults = { _, _, _ -> callCount++; Outcome.Success(RACE_RESULTS) },
        )

        val job1 = startCollecting(vm)
        assertEquals(1, callCount)
        job1.cancel()

        val job2 = backgroundScope.launch { vm.uiState.collect {} }
        testScheduler.advanceUntilIdle()
        assertEquals(1, callCount)
        job2.cancel()
    }

    @Test
    fun `year-specific round falls back to getSeason when current-season cache is different year`() = runTest {
        var requestedSeason: Pair<Int, Boolean>? = null
        val vm = RoundViewModel(
            year = 2024,
            round = 1,
            getRoundResults = { _, _, _ -> Outcome.Success(RACE_RESULTS) },
            getRoundQualifying = { _, _, _ -> Outcome.Success(QUALY) },
            getSeason = { year, forceRefresh ->
                requestedSeason = year to forceRefresh
                Outcome.Success(ROUND_SEASON_2024)
            },
            observeCachedSeason = MutableStateFlow(cachedSeason(ROUND_SEASON_2026)),
            refreshCachedSeason = { RefreshResult.Failure("should not refresh current cache") },
        )

        val job = startCollecting(vm)
        val state = vm.uiState.value as RoundViewModel.UiState.Sections
        job.cancel()

        assertEquals(2024 to false, requestedSeason)
        assertTrue(state.season is SectionUiState.Content)
        assertEquals(2024, (state.season as SectionUiState.Content).data.year)
    }

    @Test
    fun `matching current-season cache refreshes through cache without calling getSeason`() = runTest {
        var networkSeasonCalls = 0
        val refreshReasons = mutableListOf<RefreshReason>()
        val cachedSeason = MutableStateFlow<CachedResource<Season>?>(cachedSeason(ROUND_SEASON_2026))
        val vm = RoundViewModel(
            year = 2026,
            round = 1,
            getRoundResults = { _, _, _ -> Outcome.Success(RACE_RESULTS.copy(year = 2026)) },
            getRoundQualifying = { _, _, _ -> Outcome.Success(QUALY.copy(year = 2026)) },
            getSeason = { _, _ ->
                networkSeasonCalls++
                Outcome.Success(ROUND_SEASON_2026)
            },
            observeCachedSeason = cachedSeason,
            refreshCachedSeason = { reason ->
                refreshReasons += reason
                RefreshResult.Success
            },
        )

        val job = startCollecting(vm)
        job.cancel()

        assertEquals(0, networkSeasonCalls)
        assertEquals(listOf(RefreshReason.StaleOpen), refreshReasons)
        val state = vm.uiState.value as RoundViewModel.UiState.Sections
        assertTrue(state.season is SectionUiState.Content)
        assertEquals(2026, (state.season as SectionUiState.Content).data.year)
    }

    @Test
    fun `matching current-season cache is used even when observer emits after loadSeason starts`() = runTest {
        var networkSeasonCalls = 0
        val refreshReasons = mutableListOf<RefreshReason>()
        val vm = RoundViewModel(
            year = 2026,
            round = 1,
            getRoundResults = { _, _, _ -> Outcome.Success(RACE_RESULTS.copy(year = 2026)) },
            getRoundQualifying = { _, _, _ -> Outcome.Success(QUALY.copy(year = 2026)) },
            getSeason = { _, _ ->
                networkSeasonCalls++
                Outcome.Failure("offline")
            },
            observeCachedSeason = flow {
                kotlinx.coroutines.delay(1)
                emit(cachedSeason(ROUND_SEASON_2026))
            },
            refreshCachedSeason = { reason ->
                refreshReasons += reason
                RefreshResult.Success
            },
        )

        val job = startCollecting(vm)
        val state = vm.uiState.value as RoundViewModel.UiState.Sections
        job.cancel()

        assertEquals(0, networkSeasonCalls)
        assertEquals(listOf(RefreshReason.StaleOpen), refreshReasons)
        assertTrue(state.season is SectionUiState.Content)
        assertEquals(2026, (state.season as SectionUiState.Content).data.year)
    }

    @Test
    fun `rollover during current-season refresh falls back to route year schedule`() = runTest {
        var requestedSeason: Pair<Int, Boolean>? = null
        val cachedSeason = MutableStateFlow<CachedResource<Season>?>(cachedSeason(ROUND_SEASON_2026))
        val vm = RoundViewModel(
            year = 2026,
            round = 1,
            getRoundResults = { _, _, _ -> Outcome.Success(RACE_RESULTS.copy(year = 2026)) },
            getRoundQualifying = { _, _, _ -> Outcome.Success(QUALY.copy(year = 2026)) },
            getSeason = { year, forceRefresh ->
                requestedSeason = year to forceRefresh
                Outcome.Success(ROUND_SEASON_2026.copy(races = listOf(ROUND_SEASON_2024.races.single().copy(name = "Fallback Bahrain GP"))))
            },
            observeCachedSeason = cachedSeason,
            refreshCachedSeason = {
                cachedSeason.value = cachedSeason(ROUND_SEASON_2026.copy(year = 2027))
                RefreshResult.Success
            },
        )

        val job = startCollecting(vm)
        val state = vm.uiState.value as RoundViewModel.UiState.Sections
        job.cancel()

        assertEquals(2026 to false, requestedSeason)
        assertTrue(state.season is SectionUiState.Content)
        val season = state.season as SectionUiState.Content
        assertEquals(2026, season.data.year)
        assertEquals("Fallback Bahrain GP", season.data.races.single().name)
    }

    private val ROUND_SEASON_2026 = ROUND_SEASON_2024.copy(year = 2026)

    private fun cachedSeason(season: Season): CachedResource<Season> {
        val key = CacheResourceKeys.currentSeasonSchedule(season.year)
        return CachedResource(
            data = season,
            snapshot = ResourceSnapshot(
                key = key.value,
                season = season.year,
                payloadKind = key.payloadKind,
                payloadVersion = 1,
                payloadJson = "{}",
                fetchedAtEpochMs = 100L,
                staleAfterEpochMs = 200L,
            ),
        )
    }
}
