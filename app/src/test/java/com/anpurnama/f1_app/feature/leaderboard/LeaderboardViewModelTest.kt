package com.anpurnama.f1_app.feature.leaderboard

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.ResourceSnapshot
import com.anpurnama.f1_app.core.ui.ContentSyncStatus
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.cache.CacheResourceKeys
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.test.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test
    fun `cached driver refresh failure keeps previous standings visible`() = runTest {
        val cachedDrivers = MutableStateFlow(CachedResource(drivers, snapshot(CacheResourceKeys.driverStandings(2026))))
        val cachedConstructors = MutableStateFlow(CachedResource(teams, snapshot(CacheResourceKeys.constructorStandings(2026))))
        val vm = LeaderboardViewModel(
            getDriversStandings = { error("uncached driver use case should not run") },
            getConstructorsStandings = { error("uncached constructor use case should not run") },
            observeCachedDrivers = cachedDrivers,
            refreshCachedDrivers = { reason ->
                assertEquals(RefreshReason.StaleOpen, reason)
                RefreshResult.RetryableFailure("offline")
            },
            observeCachedConstructors = cachedConstructors,
            refreshCachedConstructors = { RefreshResult.SkippedFresh },
            nowEpochMs = { 1_000L },
        )

        val job = launch { vm.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value as LeaderboardViewModel.UiState.Sections
        val driversContent = state.drivers as SectionUiState.Content
        assertEquals("antonelli", driversContent.data.single().driverId)
        assertEquals(ContentSyncStatus.RefreshFailed("offline"), driversContent.sync)
        job.cancel()
    }

    private fun snapshot(key: com.anpurnama.f1_app.core.cache.CacheResourceKey) = ResourceSnapshot(
        key = key.value,
        season = key.season,
        payloadKind = key.payloadKind,
        payloadVersion = 1,
        payloadJson = "{}",
        fetchedAtEpochMs = 0L,
        staleAfterEpochMs = Long.MAX_VALUE,
    )
}
