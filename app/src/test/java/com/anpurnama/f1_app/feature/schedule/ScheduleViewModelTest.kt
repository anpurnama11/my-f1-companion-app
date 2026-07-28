package com.anpurnama.f1_app.feature.schedule

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshAttemptStatus
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.ResourceSnapshot
import com.anpurnama.f1_app.core.ui.ContentSyncStatus
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.RoundPodium
import com.anpurnama.f1_app.f1.cache.CacheResourceKeys
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.RoundResult
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.test.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
        totalKm = 11.687, totalLaps = 107, progressPercent = 2f / 3f,
    )

    private fun podium(race: Race): RoundPodium {
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
        val state = vm.uiState.value as ScheduleViewModel.UiState.Sections
        assertTrue(state.season is SectionUiState.Content)
        assertTrue("podium[1] should be Content after warmUp, was ${state.podiums[1]}",
            state.podiums[1] is SectionUiState.Content)
        assertTrue("podium[2] should be Content after warmUp, was ${state.podiums[2]}",
            state.podiums[2] is SectionUiState.Content)
        assertEquals(null, state.podiums[11])
        assertEquals(null, state.podiums[12])

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
        val vm = fakeVm(
            getSeason = { Outcome.Failure("boom") },
            getPodium = { _, _, _ -> podiumCalls++; Outcome.Success(podium(BAHRAIN)) }
        )

        startCollecting(vm)
        val state = vm.uiState.value as ScheduleViewModel.UiState.Sections
        assertTrue(state.season is SectionUiState.Error)
        assertEquals(0, podiumCalls)
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
        assertEquals(listOf(1, 2), calls)

        vm.retryPodium(round = 2)
        testScheduler.advanceUntilIdle()
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
    fun `cached season stays visible when refresh fails`() = runTest {
        val cachedSeason = MutableStateFlow<CachedResource<Season>?>(cachedSeason(SEASON))
        val refreshReasons = mutableListOf<RefreshReason>()
        val vm = ScheduleViewModel(
            getSeason = { Outcome.Failure("network should not be the source") },
            getRoundPodium = { _, round, _ -> Outcome.Success(podium(SEASON.races.first { it.round == round })) },
            observeCachedSeason = cachedSeason,
            refreshCachedSeason = { reason ->
                refreshReasons += reason
                cachedSeason.value = cachedSeason(SEASON, RefreshAttemptStatus.Failed("offline"))
                RefreshResult.Failure("offline")
            },
            nowEpochMs = { 150L },
        )

        startCollecting(vm)
        vm.refresh()
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value as ScheduleViewModel.UiState.Sections

        assertTrue(state.season is SectionUiState.Content)
        val season = state.season as SectionUiState.Content
        assertEquals(2026, season.data.year)
        assertEquals(ContentSyncStatus.RefreshFailed("offline"), season.sync)
        assertEquals(RefreshReason.PullToRefresh, refreshReasons.last())
    }

    @Test
    fun `refresh re-fires loadPodium for every past round and resolves to Content`() = runTest {
        // A same-key effect does not restart after refresh, so the ViewModel owns these reloads.
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
        assertEquals("refresh re-fires every past round",
            listOf(1, 2, 1, 2), podiumCalls)
        assertTrue("podium[1] should be Content after refresh, was ${after.podiums[1]}",
            after.podiums[1] is SectionUiState.Content)
        assertTrue("podium[2] should be Content after refresh, was ${after.podiums[2]}",
            after.podiums[2] is SectionUiState.Content)
    }

    @Test
    fun `concurrent loadPodium writes do not lose updates (RMW race fix)`() = runTest {
        // Concurrent StateFlow writes must preserve both completed rows.
        val vm = fakeVm()

        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        collectJob.cancel()

        val j1 = backgroundScope.launch { vm.loadPodium(2026, 1, forceRefresh = true) }
        val j2 = backgroundScope.launch { vm.loadPodium(2026, 2, forceRefresh = true) }
        testScheduler.advanceUntilIdle()
        j1.join(); j2.join()

        val state = vm.uiState.value as ScheduleViewModel.UiState.Sections
        assertTrue("podium[1] should be Content, was ${state.podiums[1]}",
            state.podiums[1] is SectionUiState.Content)
        assertTrue("podium[2] should be Content, was ${state.podiums[2]}",
            state.podiums[2] is SectionUiState.Content)
        assertTrue("verstappen",
            (state.podiums[1] as SectionUiState.Content).data.topThree[0].driverId == "verstappen")
        assertTrue("perez",
            (state.podiums[2] as SectionUiState.Content).data.topThree[0].driverId == "perez")
    }

    private fun cachedSeason(
        season: Season,
        attemptStatus: RefreshAttemptStatus? = RefreshAttemptStatus.Succeeded,
    ): CachedResource<Season> {
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
                lastAttemptEpochMs = 120L,
                lastAttemptStatus = attemptStatus,
            ),
        )
    }

}
